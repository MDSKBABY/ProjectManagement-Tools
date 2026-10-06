package com.company.projectmanagement.knowledge.domain;

/** 技术知识从草稿提交审核，通过后锁定；驳回后可修订并重新提交。 */
public enum KnowledgeArticleStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
