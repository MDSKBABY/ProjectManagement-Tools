package com.company.projectmanagement.knowledge.web;

import com.company.projectmanagement.knowledge.domain.KnowledgeArticle;
import com.company.projectmanagement.knowledge.domain.KnowledgeArticleStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** 列表响应保留检索和审核所需摘要，避免重复返回长正文。 */
public record KnowledgeArticleSummaryResponse(
        Long id,
        String title,
        String scenario,
        List<String> tags,
        KnowledgeArticleStatus status,
        Long createdBy,
        String createdByDisplayName,
        int attachmentCount,
        OffsetDateTime updatedAt) {

    public static KnowledgeArticleSummaryResponse from(
            KnowledgeArticle article, List<String> tags) {
        return new KnowledgeArticleSummaryResponse(
                article.getId(), article.getTitle(), article.getScenario(), tags,
                article.getStatus(), article.getCreatedBy(), article.getCreatedByDisplayName(),
                article.getAttachmentCount() == null ? 0 : article.getAttachmentCount(),
                article.getUpdatedAt());
    }
}
