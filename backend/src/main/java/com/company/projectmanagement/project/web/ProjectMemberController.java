package com.company.projectmanagement.project.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.project.service.ProjectMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目成员 API；功能权限和项目资源权限分别在安全配置及业务层校验。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    public ProjectMemberController(ProjectMemberService projectMemberService) {
        this.projectMemberService = projectMemberService;
    }

    @GetMapping
    PageResponse<ProjectMemberResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        return projectMemberService.list(
                projectId, page, pageSize, keyword, authentication.getName());
    }

    @GetMapping("/candidates")
    PageResponse<ProjectMemberCandidateResponse> listCandidates(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        return projectMemberService.listCandidates(
                projectId, page, pageSize, keyword, authentication.getName());
    }

    @PostMapping
    ResponseEntity<ProjectMemberResponse> add(
            @PathVariable Long projectId,
            @Valid @RequestBody AddProjectMemberRequest request,
            Authentication authentication) {
        ProjectMemberResponse created = projectMemberService.add(
                projectId, request, authentication.getName());
        return ResponseEntity.created(URI.create(
                        "/api/v1/projects/" + projectId + "/members/" + created.userId()))
                .body(created);
    }

    @PutMapping("/{userId}")
    ProjectMemberResponse updateRole(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateProjectMemberRoleRequest request,
            Authentication authentication) {
        return projectMemberService.updateRole(
                projectId, userId, request, authentication.getName());
    }

    @DeleteMapping("/{userId}")
    ResponseEntity<Void> remove(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            Authentication authentication) {
        projectMemberService.remove(projectId, userId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
