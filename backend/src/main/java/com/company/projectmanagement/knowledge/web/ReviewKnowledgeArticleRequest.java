package com.company.projectmanagement.knowledge.web;

import com.company.projectmanagement.knowledge.domain.KnowledgeArticleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 审核只能选择通过或驳回；驳回原因由业务层强制校验。 */
public record ReviewKnowledgeArticleRequest(
        @NotNull KnowledgeArticleStatus status,
        @Size(max = 2000) String comment) {
}
