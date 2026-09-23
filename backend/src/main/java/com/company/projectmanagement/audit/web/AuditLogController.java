package com.company.projectmanagement.audit.web;

import com.company.projectmanagement.audit.domain.AuditOutcome;
import com.company.projectmanagement.audit.service.AuditLogQueryService;
import com.company.projectmanagement.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员审计查询入口；仅暴露只读能力。 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    PageResponse<AuditLogResponse> list(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime createdFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime createdTo) {
        return auditLogQueryService.list(
                page, pageSize, actorId, action, resourceType, outcome, createdFrom, createdTo);
    }
}
