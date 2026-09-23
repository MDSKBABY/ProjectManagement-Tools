package com.company.projectmanagement.file.domain;

/** 已接收分片的持久化摘要，用于幂等重传和完成前校验。 */
public class FileUploadChunk {

    private Long fileAssetId;
    private Integer chunkIndex;
    private Integer sizeBytes;
    private String sha256;

    public Long getFileAssetId() {
        return fileAssetId;
    }

    public void setFileAssetId(Long fileAssetId) {
        this.fileAssetId = fileAssetId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Integer sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }
}
