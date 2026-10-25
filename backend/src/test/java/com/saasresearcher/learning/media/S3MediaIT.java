package com.saasresearcher.learning.media;

import static com.saasresearcher.learning.support.CourseFixtures.PDF;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static com.saasresearcher.learning.support.CourseFixtures.uploadPdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.UploadSource;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.EnrollmentFixtures;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.GeneratedInputStream;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.JsonNode;

/** S3 adapter contract against a pinned S3-compatible server (SeaweedFS) that enforces signed requests. */
class S3MediaIT extends PostgresIntegrationTest {

    private static final String ACCESS = "learning-access";
    private static final String SECRET = "learning-secret-key";
    private static final String BUCKET = "learning-media";
    private static final String CONFIG = """
            {"identities":[{"name":"learning","credentials":[{"accessKey":"%s","secretKey":"%s"}],"actions":["Admin","Read","Write","List"]}]}
            """.formatted(ACCESS, SECRET);

    @SuppressWarnings("resource")
    private static final GenericContainer<?> S3 = new GenericContainer<>("chrislusf/seaweedfs:3.97")
            .withCopyToContainer(Transferable.of(CONFIG), "/etc/s3.json")
            .withCommand("server", "-s3", "-s3.port=8333", "-s3.config=/etc/s3.json", "-dir=/data", "-ip.bind=0.0.0.0")
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCode(403).withStartupTimeout(java.time.Duration.ofMinutes(3)));

    private static S3Client admin() {
        return S3Client.builder().region(Region.US_EAST_1).endpointOverride(endpoint()).forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS, SECRET))).build();
    }

    private static URI endpoint() {
        return URI.create("http://" + S3.getHost() + ":" + S3.getMappedPort(8333));
    }

    @DynamicPropertySource
    static void s3(DynamicPropertyRegistry registry) {
        S3.start();
        try (S3Client client = admin()) {
            client.createBucket(request -> request.bucket(BUCKET));
        }
        registry.add("learning.storage.mode", () -> "s3");
        registry.add("learning.storage.s3.endpoint", () -> endpoint().toString());
        registry.add("learning.storage.s3.bucket", () -> BUCKET);
        registry.add("learning.storage.s3.access-key", () -> ACCESS);
        registry.add("learning.storage.s3.secret-key", () -> SECRET);
        registry.add("learning.storage.s3.path-style", () -> "true");
        registry.add("learning.storage.s3.part-size", () -> "5MB");
    }

    @Autowired
    private AssetStore store;

    private final HttpClient http = HttpClient.newHttpClient();
    private TestUser owner;
    private TestUser teacher;

    @BeforeEach
    void users() {
        owner = setupOwner();
        teacher = approvedTeacher("s3.teacher", owner);
    }

    @Test
    void uploadsAreStoredPrivatelyAndDeliveredByShortLivedSignedUrls() throws Exception {
        String id = uploadPdf(teacher.session(), "notes.pdf");
        String key = jdbc.sql("select storage_key from media_assets where id = :id").param("id", UUID.fromString(id)).query(String.class).single();
        assertThat(jdbc.sql("select sha256 from media_assets where id = :id").param("id", UUID.fromString(id)).query(String.class).single()).hasSize(64);

        ApiResponse redirect = teacher.session().get("/media/" + id + "/content");
        assertThat(redirect.status()).isEqualTo(302);
        assertThat(redirect.header("Cache-Control")).contains("no-store");
        URI signed = URI.create(redirect.header("Location"));
        assertThat(signed.getQuery()).contains("X-Amz-Expires=60", "X-Amz-Signature=");

        HttpResponse<byte[]> full = http.send(HttpRequest.newBuilder(signed).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(full.statusCode()).isEqualTo(200);
        assertThat(full.body()).isEqualTo(PDF);
        HttpResponse<byte[]> partial = http.send(HttpRequest.newBuilder(signed).header("Range", "bytes=0-3").build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(partial.statusCode()).isEqualTo(206);
        assertThat(new String(partial.body())).isEqualTo("%PDF");

        HttpResponse<Void> anonymous = http.send(HttpRequest.newBuilder(endpoint().resolve("/" + BUCKET + "/" + key)).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(anonymous.statusCode()).as("no public ACL").isEqualTo(403);
        HttpResponse<Void> tampered = http.send(HttpRequest.newBuilder(URI.create(signed.toString().replace("X-Amz-Expires=60", "X-Amz-Expires=600"))).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(tampered.statusCode()).as("expiry is covered by the signature").isEqualTo(403);
    }

    @Test
    void failedStreamsAbortTheMultipartUpload() {
        InputStream failing = new GeneratedInputStream(new byte[0], 12L * 1024 * 1024, (byte) 1) {
            private long served;

            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (served > 7L * 1024 * 1024) {
                    throw new IllegalStateException(new IOException("client disconnected"));
                }
                int read = super.read(buffer, offset, length);
                served += read;
                return read;
            }
        };
        assertThatThrownBy(() -> store.put(new UploadSource("assets/broken", failing))).isInstanceOf(IOException.class);
        try (S3Client client = admin()) {
            assertThat(client.listMultipartUploads(request -> request.bucket(BUCKET)).uploads()).isEmpty();
            assertThat(client.listObjectsV2(request -> request.bucket(BUCKET).prefix("assets/broken")).contents()).isEmpty();
        }
    }

    @Test
    void largeObjectsStreamInPartsWithChecksumsAndMissingObjectsReportNotFound() throws IOException {
        var stored = store.put(new UploadSource("assets/large", new GeneratedInputStream(new byte[] {1, 2, 3}, 11L * 1024 * 1024, (byte) 7)));
        assertThat(stored.sizeBytes()).isEqualTo(11L * 1024 * 1024);
        assertThat(store.stat("assets/large").sizeBytes()).isEqualTo(11L * 1024 * 1024);
        assertThat(stored.sha256()).hasSize(64);
        assertThatThrownBy(() -> store.stat("assets/missing")).isInstanceOf(NoSuchFileException.class);
        store.delete("assets/large");
        assertThatThrownBy(() -> store.stat("assets/large")).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void withdrawnLearnersGetNoNewUrls() {
        String pdf = uploadPdf(teacher.session(), "worksheet.pdf");
        Map<String, Object> withFile = lesson("Worksheet", "", true);
        withFile.put("resourceAssetIds", List.of(pdf));
        JsonNode course = publishedCourse(teacher.session(), "S3 course", List.of(withFile));
        TestUser student = registerStudent("s3.student");
        UUID enrollmentId = EnrollmentFixtures.enroll(teacher.session(), student.session(), UUID.fromString(course.path("courseId").asString()));
        assertThat(student.session().get("/media/" + pdf + "/content").status()).isEqualTo(302);

        student.session().post("/me/enrollments/" + enrollmentId + "/withdraw", null).expect(204);
        assertThat(student.session().get("/media/" + pdf + "/content").status()).isEqualTo(404);
    }

    @Test
    void readinessIncludesStorage() {
        JsonNode readiness = managementClient().get("/actuator/health/readiness").expect(200).json();
        assertThat(readiness.path("status").asString()).isEqualTo("UP");
    }
}
