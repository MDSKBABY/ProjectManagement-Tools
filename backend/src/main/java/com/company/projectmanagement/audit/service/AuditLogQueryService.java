package com.company.projectmanagement.audit.service;

import com.company.projectmanagement.audit.domain.AuditOutcome;
import com.company.projectmanagement.audit.mapper.AuditLogMapper;
import com.company.projectmanagement.audit.web.AuditLogResponse;
import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 组合审计查询条件，并保证时间范围有效。 */
@Service
public class AuditLogQueryService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditLogQueryService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    public PageResponse<AuditLogResponse> list(
            int page,
            int pageSize,
            Long actorId,
            String action,
            String resourceType,
            AuditOutcome outcome,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_AUDIT_TIME_RANGE",
                    "开始时间不能晚于结束时间");
        }
        String normalizedAction = normalizeCode(action);
        String normalizedResourceType = normalizeCode(resourceType);
        long totalItems = auditLogMapper.count(
                actorId, normalizedAction, normalizedResourceType, outcome, createdFrom, createdTo);
        List<AuditLogResponse> data = auditLogMapper.selectPage(
                        actorId,
                        normalizedAction,
                        normalizedResourceType,
                        outcome,
                        createdFrom,
                        createdTo,
                        (page - 1) * pageSize,
                        pageSize)
                .stream()
                .map(log -> AuditLogResponse.from(log, objectMapper))
                .toList();
        return PageResponse.of(data, page, pageSize, totalItems);
    }

    private static String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
