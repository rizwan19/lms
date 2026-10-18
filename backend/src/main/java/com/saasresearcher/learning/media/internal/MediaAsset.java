package com.saasresearcher.learning.media.internal;

import com.saasresearcher.learning.media.api.AssetMetadata;
import com.saasresearcher.learning.media.api.AssetPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
class MediaAsset {

    enum Status { UPLOADING, VALIDATING, AVAILABLE, REJECTED }

    @Id
    private UUID id;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaAsset() {
    }

    MediaAsset(AssetPurpose purpose, String filename, String contentType, UUID uploadedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.storageKey = "assets/" + id;
        this.purpose = purpose;
        this.filename = filename;
        this.contentType = contentType;
        this.uploadedBy = uploadedBy;
        this.status = Status.UPLOADING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void validating(Instant now) {
        this.status = Status.VALIDATING;
        this.updatedAt = now;
    }

    void available(String contentType, String filename, long sizeBytes, String sha256, Integer durationSeconds, Instant now) {
        this.contentType = contentType;
        this.filename = filename;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.durationSeconds = durationSeconds;
        this.status = Status.AVAILABLE;
        this.updatedAt = now;
    }

    void reject(String reason, Instant now) {
        this.status = Status.REJECTED;
        this.rejectionReason = reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.updatedAt = now;
    }

    boolean isAvailable() {
        return status == Status.AVAILABLE;
    }

    AssetMetadata toMetadata() {
        return new AssetMetadata(id, purpose, filename, contentType, sizeBytes == null ? 0 : sizeBytes, durationSeconds);
    }

    UUID id() {
        return id;
    }

    String storageKey() {
        return storageKey;
    }

    AssetPurpose purpose() {
        return purpose;
    }

    Status status() {
        return status;
    }

    String rejectionReason() {
        return rejectionReason;
    }

    UUID uploadedBy() {
        return uploadedBy;
    }
}
