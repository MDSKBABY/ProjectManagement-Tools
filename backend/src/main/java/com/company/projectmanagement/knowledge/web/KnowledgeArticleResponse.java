package com.company.projectmanagement.knowledge.web;

import com.company.projectmanagement.knowledge.domain.KnowledgeArticle;
import com.company.projectmanagement.knowledge.domain.KnowledgeArticleStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** 技术知识详情响应，包含审核展示信息和当前附件。 */
public record KnowledgeArticleResponse(
        Long id,
        Long projectId,
        String title,
        String scenario,
        String symptom,
        String cause,
        String solution,
        String applicableConditions,
        List<String> tags,
        KnowledgeArticleStatus status,
        String reviewComment,
        Long reviewedBy,
        String reviewedByDisplayName,
        OffsetDateTime reviewedAt,
        Long createdBy,
        String createdByDisplayName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<KnowledgeAttachmentResponse> attachments) {

    public static KnowledgeArticleResponse from(
            KnowledgeArticle article,
            List<String> tags,
            List<KnowledgeAttachmentResponse> attachments) {
        return new KnowledgeArticleResponse(
                article.getId(), article.getProjectId(), article.getTitle(), article.getScenario(),
                article.getSymptom(), article.getCause(), article.getSolution(),
                article.getApplicableConditions(), tags, article.getStatus(),
                article.getReviewComment(), article.getReviewedBy(),
                article.getReviewedByDisplayName(), article.getReviewedAt(),
                article.getCreatedBy(), article.getCreatedByDisplayName(),
                article.getCreatedAt(), article.getUpdatedAt(), attachments);
    }
}
