package com.company.projectmanagement.file.web;

import com.company.projectmanagement.file.domain.FileAsset;
import com.company.projectmanagement.file.domain.FileStatus;
import java.time.OffsetDateTime;

public record FileAssetResponse(
        Long id,
        String fileGroupId,
        int version,
        String originalName,
        String mediaType,
        long sizeBytes,
        String sha256,
        FileStatus status,
        UploadedByResponse uploadedBy,
        OffsetDateTime createdAt,
        Integer chunkSizeBytes,
        Integer totalChunks) {

    public static FileAssetResponse from(FileAsset asset) {
        return new FileAssetResponse(
                asset.getId(),
                asset.getFileGroupId(),
                asset.getVersion(),
                asset.getOriginalName(),
                asset.getMediaType(),
                asset.getSizeBytes(),
                asset.getSha256(),
                asset.getStatus(),
                new UploadedByResponse(asset.getUploadedBy(), asset.getUploadedByDisplayName()),
                asset.getCreatedAt(),
                null,
                null);
    }

    public static FileAssetResponse forUpload(
            FileAsset asset, int chunkSizeBytes, int totalChunks) {
        FileAssetResponse response = from(asset);
        return new FileAssetResponse(
                response.id(),
                response.fileGroupId(),
                response.version(),
                response.originalName(),
                response.mediaType(),
                response.sizeBytes(),
                response.sha256(),
                response.status(),
                response.uploadedBy(),
                response.createdAt(),
                chunkSizeBytes,
                totalChunks);
    }

    public record UploadedByResponse(Long id, String displayName) {
    }
}
