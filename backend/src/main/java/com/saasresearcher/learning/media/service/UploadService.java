package com.saasresearcher.learning.media.service;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.media.api.AssetPurpose;
import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.StoredAsset;
import com.saasresearcher.learning.media.api.UploadSource;
import com.saasresearcher.learning.media.config.MediaProperties;
import com.saasresearcher.learning.media.entity.MediaAsset;
import com.saasresearcher.learning.media.repository.MediaAssetRepository;
import com.saasresearcher.learning.media.service.UploadValidator.Validated;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Upload lifecycle UPLOADING → VALIDATING → AVAILABLE or REJECTED. The body is streamed to a staging
 * file with a running size limit, validated there, then written to the asset store.
 */
@Service
public class UploadService {

    private final MediaAssetRepository assets;
    private final AssetStore store;
    private final UploadValidator validator;
    private final MediaProperties properties;
    private final Clock clock;

    UploadService(MediaAssetRepository assets, AssetStore store, UploadValidator validator, MediaProperties properties, Clock clock)
            throws IOException {
        this.assets = assets;
        this.store = store;
        this.validator = validator;
        this.properties = properties;
        this.clock = clock;
        Files.createDirectories(properties.stagingDir());
    }

    public MediaAsset upload(AssetPurpose purpose, String rawFilename, String contentType, long contentLength, InputStream body, Actor actor) {
        requireUploader(purpose, actor);
        String filename = SafeFilenames.clean(rawFilename);
        FileType type = FileType.declared(purpose, contentType, filename)
                .orElseThrow(() -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                        "This file type is not accepted for " + purpose.name().toLowerCase().replace('_', ' ')));
        long maxBytes = maxBytes(purpose);
        if (contentLength > maxBytes) {
            RejectedUpload tooLarge = RejectedUpload.tooLarge(maxBytes);
            throw new ApiException(tooLarge.status, tooLarge.code, tooLarge.getMessage());
        }

        MediaAsset asset = assets.save(new MediaAsset(purpose, filename, type.mimeType, actor.userId(), clock.instant()));
        Path staged = staging(asset);
        Path validatedFile = null;
        try {
            copyWithLimit(body, staged, maxBytes);
            asset.validating(clock.instant());
            asset = assets.save(asset);
            Validated validated = validator.validate(type, staged);
            validatedFile = validated.file();
            StoredAsset stored;
            try (InputStream in = Files.newInputStream(validatedFile)) {
                stored = store.put(new UploadSource(asset.getStorageKey(), in));
            }
            String storedName = validated.contentType().equals(type.mimeType) ? filename : SafeFilenames.withExtension(filename, ".png");
            asset.available(validated.contentType(), storedName, stored.sizeBytes(), stored.sha256(), validated.durationSeconds(), clock.instant());
            return assets.save(asset);
        } catch (RejectedUpload rejected) {
            throw reject(asset, rejected);
        } catch (IOException interrupted) {
            throw reject(asset, new RejectedUpload(HttpStatus.BAD_REQUEST, RejectedUpload.UPLOAD_INTERRUPTED, "The upload was interrupted"));
        } finally {
            UploadValidator.deleteQuietly(staged, validatedFile);
        }
    }

    Path staging(MediaAsset asset) {
        return properties.stagingDir().resolve(asset.getId().toString());
    }

    private ApiException reject(MediaAsset asset, RejectedUpload rejected) {
        asset.reject(rejected.getMessage(), clock.instant());
        assets.save(asset);
        return new ApiException(rejected.status, rejected.code, rejected.getMessage());
    }

    private static void copyWithLimit(InputStream body, Path target, long maxBytes) throws IOException, RejectedUpload {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            int read;
            while ((read = body.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw RejectedUpload.tooLarge(maxBytes);
                }
                out.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw RejectedUpload.invalid("The file is empty");
        }
    }

    private long maxBytes(AssetPurpose purpose) {
        return switch (purpose) {
            case VIDEO -> properties.maxVideoSize().toBytes();
            case DOCUMENT -> properties.maxDocumentSize().toBytes();
            case BRANDING_IMAGE -> properties.maxImageSize().toBytes();
        };
    }

    /** Verified teachers upload course files for their drafts; workspace branding needs an approved teacher. */
    private static void requireUploader(AssetPurpose purpose, Actor actor) {
        if (purpose == AssetPurpose.BRANDING_IMAGE) {
            actor.requireApprovedTeacher();
        } else {
            actor.requireVerifiedTeacher();
        }
    }
}
