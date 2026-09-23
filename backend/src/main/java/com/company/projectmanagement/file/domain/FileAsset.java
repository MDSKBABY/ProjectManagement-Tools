package com.company.projectmanagement.file.domain;

import com.mybatisflex.annotation.Table;
import java.time.OffsetDateTime;

/** 统一文件元数据；原始文件名与服务端存储键严格分离。 */
@Table("file_asset")
public class FileAsset {

    private Long id;
    private String fileGroupId;
    private Integer version;
    private String originalName;
    private String storageKey;
    private String mediaType;
    private Long sizeBytes;
    private String sha256;
    private FileStatus status;
    private Long uploadedBy;
    private String uploadedByDisplayName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileGroupId() {
        return fileGroupId;
    }

    public void setFileGroupId(String fileGroupId) {
        this.fileGroupId = fileGroupId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getUploadedByDisplayName() {
        return uploadedByDisplayName;
    }

    public void setUploadedByDisplayName(String uploadedByDisplayName) {
        this.uploadedByDisplayName = uploadedByDisplayName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
