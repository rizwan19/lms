package com.saasresearcher.learning.media.infra;

import com.saasresearcher.learning.media.api.AssetStat;
import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.AssetStream;
import com.saasresearcher.learning.media.api.ByteRange;
import com.saasresearcher.learning.media.api.StoredAsset;
import com.saasresearcher.learning.media.api.UploadSource;
import com.saasresearcher.learning.media.config.StorageProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Private S3 storage. Uploads stream through bounded multipart parts (memory never scales with file size) and are
 * aborted on any failure; objects get no ACL beyond the bucket's private default; reads are delivered through
 * 60-second presigned URLs issued only after authorization.
 */
@Component
@ConditionalOnProperty(name = "learning.storage.mode", havingValue = "s3")
public class S3AssetStore implements AssetStore {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties.S3 config;

    S3AssetStore(StorageProperties properties) {
        this.config = properties.s3();
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKey(), config.secretKey()));
        S3Configuration s3Configuration = S3Configuration.builder().pathStyleAccessEnabled(config.pathStyle()).build();
        // Checksum trailers only when required: many S3-compatible servers mishandle the newer aws-chunked default.
        var client = S3Client.builder().region(Region.of(config.region())).credentialsProvider(credentials).serviceConfiguration(s3Configuration)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        var signer = S3Presigner.builder().region(Region.of(config.region())).credentialsProvider(credentials).serviceConfiguration(s3Configuration);
        if (config.endpoint() != null) {
            client.endpointOverride(config.endpoint());
            signer.endpointOverride(config.endpoint());
        }
        this.s3 = client.build();
        this.presigner = signer.build();
    }

    @PreDestroy
    void close() {
        s3.close();
        presigner.close();
    }

    @Override
    public StoredAsset put(UploadSource source) throws IOException {
        String uploadId = s3.createMultipartUpload(request -> request.bucket(config.bucket()).key(source.key())).uploadId();
        try (InputStream in = source.content()) {
            MessageDigest digest = sha256();
            List<CompletedPart> parts = new ArrayList<>();
            byte[] buffer = new byte[(int) config.partSize().toBytes()];
            long size = 0;
            int read;
            do {
                read = in.readNBytes(buffer, 0, buffer.length);
                if (read > 0 || parts.isEmpty()) {
                    digest.update(buffer, 0, read);
                    int partNumber = parts.size() + 1;
                    String etag = s3.uploadPart(request -> request.bucket(config.bucket()).key(source.key()).uploadId(uploadId).partNumber(partNumber),
                            RequestBody.fromBytes(Arrays.copyOf(buffer, read))).eTag();
                    parts.add(CompletedPart.builder().partNumber(partNumber).eTag(etag).build());
                    size += read;
                }
            } while (read == buffer.length);
            s3.completeMultipartUpload(request -> request.bucket(config.bucket()).key(source.key()).uploadId(uploadId)
                    .multipartUpload(upload -> upload.parts(parts)));
            return new StoredAsset(source.key(), size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | RuntimeException e) {
            s3.abortMultipartUpload(request -> request.bucket(config.bucket()).key(source.key()).uploadId(uploadId));
            throw e instanceof IOException io ? io : new IOException("S3 upload failed", e);
        }
    }

    @Override
    public AssetStat stat(String key) throws IOException {
        try {
            return new AssetStat(key, s3.headObject(request -> request.bucket(config.bucket()).key(key)).contentLength());
        } catch (NoSuchKeyException e) {
            throw new NoSuchFileException(key);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new NoSuchFileException(key);
            }
            throw new IOException("S3 stat failed", e);
        }
    }

    @Override
    public AssetStream openRange(String key, ByteRange range) throws IOException {
        long size = stat(key).sizeBytes();
        try {
            InputStream content = s3.getObject(request -> request.bucket(config.bucket()).key(key).range("bytes=" + range.start() + "-" + range.end()));
            return new AssetStream(content, range, size);
        } catch (SdkException e) {
            throw new IOException("S3 read failed", e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            s3.deleteObject(request -> request.bucket(config.bucket()).key(key));
        } catch (SdkException e) {
            throw new IOException("S3 delete failed", e);
        }
    }

    @Override
    public void checkAvailable() throws IOException {
        try {
            s3.headBucket(request -> request.bucket(config.bucket()));
        } catch (SdkException e) {
            throw new IOException("S3 bucket is not reachable", e);
        }
    }

    @Override
    public Optional<URI> presignedRead(String key, String contentType, String contentDisposition) {
        URI url = URI.create(presigner.presignGetObject(request -> request.signatureDuration(config.presignTtl())
                .getObjectRequest(get -> get.bucket(config.bucket()).key(key).responseContentType(contentType)
                        .responseContentDisposition(contentDisposition).responseCacheControl("private, no-store")))
                .url().toString());
        return Optional.of(url);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
