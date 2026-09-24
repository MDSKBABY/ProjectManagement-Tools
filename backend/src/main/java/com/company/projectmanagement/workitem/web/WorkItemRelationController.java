package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.workitem.domain.WorkItemRelationType;
import com.company.projectmanagement.workitem.service.WorkItemRelationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
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

/** 项目内工作项关系 API；关系方向由 source 指向 target。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/work-item-relations")
public class WorkItemRelationController {

    private final WorkItemRelationService relationService;

    public WorkItemRelationController(WorkItemRelationService relationService) {
        this.relationService = relationService;
    }

    @GetMapping
    PageResponse<WorkItemRelationResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Positive Long workItemId,
            @RequestParam(required = false) WorkItemRelationType type,
            Authentication authentication) {
        return relationService.list(
                projectId, page, pageSize, workItemId, type, authentication.getName());
    }

    @PostMapping
    ResponseEntity<WorkItemRelationResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateWorkItemRelationRequest request,
            Authentication authentication) {
        WorkItemRelationResponse created = relationService.create(
                projectId, request, authentication.getName());
        return ResponseEntity.created(URI.create(
                        "/api/v1/projects/" + projectId
                                + "/work-item-relations/" + created.id()))
                .body(created);
    }

    @DeleteMapping("/{relationId}")
    ResponseEntity<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long relationId,
            Authentication authentication) {
        relationService.delete(projectId, relationId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
