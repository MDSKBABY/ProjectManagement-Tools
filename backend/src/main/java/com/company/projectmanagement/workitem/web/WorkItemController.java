package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.workitem.domain.WorkItemPriority;
import com.company.projectmanagement.workitem.domain.WorkItemStatus;
import com.company.projectmanagement.workitem.domain.WorkItemType;
import com.company.projectmanagement.workitem.service.WorkItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目内统一工作项 API；功能权限与项目资源权限分层校验。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/work-items")
public class WorkItemController {

    private final WorkItemService workItemService;

    public WorkItemController(WorkItemService workItemService) {
        this.workItemService = workItemService;
    }

    @GetMapping
    PageResponse<WorkItemResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) WorkItemType type,
            @RequestParam(required = false) WorkItemStatus status,
            @RequestParam(required = false) WorkItemPriority priority,
            @RequestParam(required = false) @Positive Long assigneeId,
            @RequestParam(required = false) LocalDate plannedFrom,
            @RequestParam(required = false) LocalDate plannedTo,
            Authentication authentication) {
        return workItemService.list(
                projectId, page, pageSize, keyword, type, status, priority,
                assigneeId, plannedFrom, plannedTo, authentication.getName());
    }

    @GetMapping("/{id}")
    WorkItemResponse get(
            @PathVariable Long projectId,
            @PathVariable Long id,
            Authentication authentication) {
        return workItemService.get(projectId, id, authentication.getName());
    }

    @PostMapping
    ResponseEntity<WorkItemResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateWorkItemRequest request,
            Authentication authentication) {
        WorkItemResponse created = workItemService.create(
                projectId, request, authentication.getName());
        return ResponseEntity.created(URI.create(
                        "/api/v1/projects/" + projectId + "/work-items/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    WorkItemResponse update(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateWorkItemRequest request,
            Authentication authentication) {
        return workItemService.update(projectId, id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long id,
            Authentication authentication) {
        workItemService.delete(projectId, id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/status-transitions")
    WorkItemResponse transition(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @Valid @RequestBody TransitionWorkItemStatusRequest request,
            Authentication authentication) {
        return workItemService.transition(projectId, id, request, authentication.getName());
    }

    @GetMapping("/{id}/status-history")
    List<WorkItemStatusLogResponse> statusHistory(
            @PathVariable Long projectId,
            @PathVariable Long id,
            Authentication authentication) {
        return workItemService.statusHistory(projectId, id, authentication.getName());
    }
}
