package com.company.projectmanagement.audit.web;

/** 审计事件中的操作人摘要。 */
public record AuditActorResponse(Long id, String username, String displayName) {
}
