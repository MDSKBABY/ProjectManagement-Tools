package com.company.projectmanagement.knowledge.web;

import com.company.projectmanagement.file.domain.FileAsset;

/** 技术知识附件只暴露下载展示所需的非敏感元数据。 */
public record KnowledgeAttachmentResponse(
        Long id,
        String originalName,
        String mediaType,
        long sizeBytes) {

    public static KnowledgeAttachmentResponse from(FileAsset file) {
        return new KnowledgeAttachmentResponse(
                file.getId(), file.getOriginalName(), file.getMediaType(), file.getSizeBytes());
    }
}
