package com.saasresearcher.learning.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.service.MediaMaintenance;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.GeneratedInputStream;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LocalMediaIT extends PostgresIntegrationTest {

    private static final long MIB = 1024 * 1024;
    private static final byte[] PDF = "%PDF-1.4\n%âãÏÓ\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
    private static final Path STAGING = Path.of("build/test-media/staging");

    @Autowired
    private AssetStore store;

    @Autowired
    private MediaMaintenance maintenance;

    private ApiClient admin;
    private TestUser teacher;

    @BeforeEach
    void users() {
        admin = setupAdministrator();
        teacher = createUser(admin, "media.teacher", "media@example.test", "TEACHER");
    }

    @Test
    void uploadedDocumentIsServedPrivatelyWithByteRanges() {
        ApiResponse uploaded = upload(teacher.session(), "DOCUMENT", "notes.pdf", PDF, "application/pdf").expect(201);
        String id = uploaded.json().path("id").asString();
        assertThat(uploaded.json().path("sizeBytes").asLong()).isEqualTo(PDF.length);

        ApiResponse full = teacher.session().get("/media/" + id + "/content");
        assertThat(full.status()).isEqualTo(200);
        assertThat(full.bytes()).isEqualTo(PDF);
        assertThat(full.header("Cache-Control")).isEqualTo("private, no-store");
        assertThat(full.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(full.header("Accept-Ranges")).isEqualTo("bytes");

        ApiResponse partial = teacher.session().get("/media/" + id + "/content", "Range", "bytes=0-7");
        assertThat(partial.status()).isEqualTo(206);
        assertThat(partial.text()).isEqualTo("%PDF-1.4");
        assertThat(partial.header("Content-Range")).isEqualTo("bytes 0-7/" + PDF.length);

        ApiResponse suffix = teacher.session().get("/media/" + id + "/content", "Range", "bytes=-6");
        assertThat(suffix.text()).isEqualTo("%%EOF\n");

        ApiResponse unsatisfiable = teacher.session().get("/media/" + id + "/content", "Range", "bytes=100000-");
        assertThat(unsatisfiable.status()).isEqualTo(416);
        assertThat(unsatisfiable.header("Content-Range")).isEqualTo("bytes */" + PDF.length);

        ApiResponse download = teacher.session().get("/media/" + id + "/content?download=true");
        assertThat(download.header("Content-Disposition")).startsWith("attachment");
    }

    @Test
    void h264AacVideoIsAcceptedAndOtherCodecsRejected() throws IOException {
        ApiResponse video = upload(teacher.session(), "VIDEO", "lesson.mp4", fixture("h264-aac.mp4"), "video/mp4").expect(201);
        assertThat(video.json().path("durationSeconds").asInt()).isEqualTo(2);
        assertThat(video.json().path("contentType").asString()).isEqualTo("video/mp4");

        ApiResponse mpeg4 = upload(teacher.session(), "VIDEO", "old.mp4", fixture("mpeg4.mp4"), "video/mp4");
        assertThat(mpeg4.status()).isEqualTo(422);
        assertThat(mpeg4.code()).isEqualTo("UPLOAD_REJECTED");
    }

    @Test
    void spoofedContentIsRejectedAndNotStored() throws IOException {
        ApiResponse spoofed = upload(teacher.session(), "DOCUMENT", "fake.pdf", png(10, 10), "application/pdf");
        assertThat(spoofed.status()).isEqualTo(422);
        assertThat(jdbc.sql("select status from media_assets").query(String.class).single()).isEqualTo("REJECTED");
        String key = jdbc.sql("select storage_key from media_assets").query(String.class).single();
        assertThatThrownBy(() -> store.stat(key)).isInstanceOf(IOException.class);

        ApiResponse binaryText = upload(teacher.session(), "DOCUMENT", "data.txt", new byte[] {'a', 0, 'b'}, "text/plain");
        assertThat(binaryText.status()).isEqualTo(422);
        assertThat(upload(teacher.session(), "DOCUMENT", "বাংলা.txt", "বাংলা নোট".getBytes(StandardCharsets.UTF_8), "text/plain").status())
                .isEqualTo(201);
    }

    @Test
    void unsupportedTypesAndRolesAreRefused() throws IOException {
        assertThat(upload(teacher.session(), "DOCUMENT", "page.html", "<script>".getBytes(StandardCharsets.UTF_8), "text/html").status()).isEqualTo(415);
        assertThat(upload(teacher.session(), "BRANDING_IMAGE", "logo.svg", "<svg/>".getBytes(StandardCharsets.UTF_8), "image/svg+xml").status()).isEqualTo(403);
        assertThat(upload(admin, "BRANDING_IMAGE", "logo.svg", "<svg/>".getBytes(StandardCharsets.UTF_8), "image/svg+xml").status()).isEqualTo(415);
        assertThat(upload(admin, "DOCUMENT", "slides.pdf.docx", PDF, "application/pdf").status()).isEqualTo(415);
        TestUser student = createUser(admin, "media.student", null, "STUDENT");
        assertThat(upload(student.session(), "DOCUMENT", "notes.pdf", PDF, "application/pdf").status()).isEqualTo(403);
    }

    @Test
    void brandingImagesAreReencodedAsPngAndUsableAsLogo() throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
        ApiResponse uploaded = upload(admin, "BRANDING_IMAGE", "logo.jpg", jpeg.toByteArray(), "image/jpeg").expect(201);
        String id = uploaded.json().path("id").asString();
        assertThat(uploaded.json().path("contentType").asString()).isEqualTo("image/png");
        assertThat(uploaded.json().path("filename").asString()).isEqualTo("logo.png");
        assertThat(Arrays.copyOf(admin.get("/media/" + id + "/content").bytes(), 4)).isEqualTo(new byte[] {(byte) 0x89, 'P', 'N', 'G'});

        TestUser student = createUser(admin, "logo.viewer", null, "STUDENT");
        assertThat(student.session().get("/media/" + id + "/content").status()).isEqualTo(404);
        long revision = admin.get("/institution").json().path("revision").asLong();
        admin.put("/institution", Map.of("name", "Test School", "timezone", "Asia/Dhaka", "logoAssetId", id, "certificateVerification", false,
                "verificationShowsStudent", false, "expectedRevision", revision)).expect(200);
        assertThat(student.session().get("/media/" + id + "/content").status()).isEqualTo(200);
    }

    @Test
    void privateContentCannotBeFetchedByGuessingOrTraversal() throws IOException {
        ApiResponse uploaded = upload(teacher.session(), "DOCUMENT", "../../etc/passwd.pdf", PDF, "application/pdf").expect(201);
        String id = uploaded.json().path("id").asString();
        assertThat(uploaded.json().path("filename").asString()).isEqualTo("passwd.pdf");
        String key = jdbc.sql("select storage_key from media_assets where id = :id").param("id", UUID.fromString(id)).query(String.class).single();
        assertThat(key).isEqualTo("assets/" + id);
        assertThat(Files.exists(Path.of("build/test-media/assets").resolve(key))).isTrue();

        TestUser otherTeacher = createUser(admin, "other.teacher", "other@example.test", "TEACHER");
        TestUser student = createUser(admin, "curious.student", null, "STUDENT");
        assertThat(otherTeacher.session().get("/media/" + id + "/content").status()).isEqualTo(404);
        assertThat(student.session().get("/media/" + id + "/content").status()).isEqualTo(404);
        assertThat(student.session().get("/media/" + UUID.randomUUID() + "/content").status()).isEqualTo(404);
        assertThat(client().get("/media/" + id + "/content").status()).isEqualTo(401);
        assertThat(student.session().get("/media/" + key + "/content").status()).isIn(400, 404);
        assertThat(admin.get("/media/" + id + "/content").status()).isEqualTo(200);
        assertThatThrownBy(() -> store.stat("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.stat("assets/../../secret")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sizeLimitsAreEnforcedWhileStreaming() throws IOException {
        assertThat(uploadStream(teacher.session(), "DOCUMENT", "exact.pdf", PDF, 25 * MIB, "application/pdf").status()).isEqualTo(201);

        ApiResponse documentOver = uploadStream(teacher.session(), "DOCUMENT", "over.pdf", PDF, 25 * MIB + 1, "application/pdf");
        assertThat(documentOver.status()).isEqualTo(413);
        assertThat(documentOver.code()).isEqualTo("FILE_TOO_LARGE");

        byte[] image = png(8, 8);
        assertThat(uploadStream(admin, "BRANDING_IMAGE", "exact.png", image, 5 * MIB, "image/png").status()).isEqualTo(201);
        assertThat(uploadStream(admin, "BRANDING_IMAGE", "over.png", image, 5 * MIB + 1, "image/png").status()).isEqualTo(413);

        byte[] video = fixture("h264-aac.mp4");
        byte[] videoWithFreeBoxHeader = withFreeBox(video, 500 * MIB);
        assertThat(uploadStream(teacher.session(), "VIDEO", "exact.mp4", videoWithFreeBoxHeader, 500 * MIB, "video/mp4").status()).isEqualTo(201);
        assertThat(uploadStream(teacher.session(), "VIDEO", "over.mp4", video, 500 * MIB + 1, "video/mp4").status()).isEqualTo(413);

        assertThat(jdbc.sql("select count(*) from media_assets where status = 'REJECTED'").query(Long.class).single()).isEqualTo(3);
        try (var staged = Files.list(STAGING)) {
            assertThat(staged).isEmpty();
        }
    }

    @Test
    void abandonedUploadsExpireAndOrphansAreRemovedAfterGracePeriod() throws IOException {
        UUID teacherId = teacher.id();
        UUID abandoned = UUID.randomUUID();
        jdbc.sql("""
                insert into media_assets (id, storage_key, filename, content_type, purpose, status, uploaded_by, created_at, updated_at)
                values (:id, :key, 'big.mp4', 'video/mp4', 'VIDEO', 'UPLOADING', :teacher, :at, :at)
                """)
                .param("id", abandoned).param("key", "assets/" + abandoned).param("teacher", teacherId)
                .param("at", Timestamp.from(clock.instant()))
                .update();
        Files.write(STAGING.resolve(abandoned.toString()), new byte[] {1, 2, 3});
        String orphan = upload(teacher.session(), "DOCUMENT", "orphan.pdf", PDF, "application/pdf").expect(201).json().path("id").asString();

        clock.advance(Duration.ofHours(25));
        maintenance.sweep();
        assertThat(jdbc.sql("select status from media_assets where id = :id").param("id", abandoned).query(String.class).single()).isEqualTo("REJECTED");
        assertThat(Files.exists(STAGING.resolve(abandoned.toString()))).isFalse();
        assertThat(teacher.session().get("/media/" + orphan + "/content").status()).isEqualTo(200);

        clock.advance(Duration.ofDays(7));
        maintenance.sweep();
        assertThat(teacher.session().get("/media/" + orphan + "/content").status()).isEqualTo(404);
        assertThat(jdbc.sql("select count(*) from media_assets where id = :id").param("id", UUID.fromString(orphan)).query(Long.class).single()).isZero();
    }

    /** Large boundary uploads must not accumulate on disk between runs. */
    @AfterEach
    void deleteStoredFiles() throws IOException {
        for (String key : jdbc.sql("select storage_key from media_assets").query(String.class).list()) {
            store.delete(key);
        }
    }

    private ApiResponse upload(ApiClient session, String purpose, String filename, byte[] body, String contentType) {
        return session.postBytes(uploadPath(purpose, filename), body, contentType);
    }

    private ApiResponse uploadStream(ApiClient session, String purpose, String filename, byte[] prefix, long size, String contentType) {
        return session.postStream(uploadPath(purpose, filename), () -> new GeneratedInputStream(prefix, size, (byte) 0), contentType);
    }

    private static String uploadPath(String purpose, String filename) {
        return "/media?purpose=" + purpose + "&filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8);
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = LocalMediaIT.class.getResourceAsStream("/media/" + name)) {
            return in.readAllBytes();
        }
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    /** Appends a top-level MP4 'free' box header so zero padding up to {@code totalSize} stays a valid file. */
    private static byte[] withFreeBox(byte[] video, long totalSize) {
        long boxSize = totalSize - video.length;
        return ByteBuffer.allocate(video.length + 8).put(video).putInt((int) boxSize).put("free".getBytes(StandardCharsets.US_ASCII)).array();
    }
}
