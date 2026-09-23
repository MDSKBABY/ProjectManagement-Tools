package com.company.projectmanagement.audit.web;

import com.company.projectmanagement.audit.domain.AuditLog;
import com.company.projectmanagement.audit.domain.AuditOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;

/** 审计查询返回的可读事件。 */
public record AuditLogResponse(
        Long id,
        AuditActorResponse actor,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        String ipAddress,
        String userAgent,
        String requestId,
        JsonNode details,
        OffsetDateTime createdAt) {

    public static AuditLogResponse from(AuditLog log, ObjectMapper objectMapper) {
        AuditActorResponse actor = log.getActorId() == null
                ? null
                : new AuditActorResponse(
                        log.getActorId(), log.getActorUsername(), log.getActorDisplayName());
        return new AuditLogResponse(
                log.getId(), actor, log.getAction(), log.getResourceType(), log.getResourceId(),
                log.getOutcome(), log.getIpAddress(), log.getUserAgent(), log.getRequestId(),
                readDetails(log.getDetailsJson(), objectMapper), log.getCreatedAt());
    }

    private static JsonNode readDetails(String value, ObjectMapper objectMapper) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取审计日志详情", exception);
        }
    }
}
