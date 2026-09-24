package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.workitem.domain.WorkItemReminderStatus;
import com.company.projectmanagement.workitem.service.WorkItemReminderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目内个人提醒 API；业务层会进一步按当前登录用户隔离数据。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/work-item-reminders")
public class WorkItemReminderController {

    private final WorkItemReminderService reminderService;

    public WorkItemReminderController(WorkItemReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    PageResponse<WorkItemReminderResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Positive Long workItemId,
            @RequestParam(required = false) WorkItemReminderStatus status,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            Authentication authentication) {
        return reminderService.list(
                projectId,
                page,
                pageSize,
                workItemId,
                status,
                from,
                to,
                authentication.getName());
    }

    @PostMapping
    ResponseEntity<WorkItemReminderResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateWorkItemReminderRequest request,
            Authentication authentication) {
        WorkItemReminderResponse created = reminderService.create(
                projectId, request, authentication.getName());
        return ResponseEntity.created(URI.create(
                        "/api/v1/projects/" + projectId + "/work-item-reminders/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/dismiss")
    WorkItemReminderResponse dismiss(
            @PathVariable Long projectId,
            @PathVariable Long id,
            Authentication authentication) {
        return reminderService.dismiss(projectId, id, authentication.getName());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long id,
            Authentication authentication) {
        reminderService.delete(projectId, id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
