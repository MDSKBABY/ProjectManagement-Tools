package com.company.projectmanagement.project.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.project.domain.ProjectStatus;
import com.company.projectmanagement.project.service.ProjectService;
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

/** 项目基础 API；功能权限由安全配置处理，项目级权限由业务层二次检查。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    PageResponse<ProjectResponse> list(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProjectStatus status,
            Authentication authentication) {
        return projectService.list(page, pageSize, keyword, status, authentication.getName());
    }

    @GetMapping("/{id}")
    ProjectResponse get(@PathVariable Long id, Authentication authentication) {
        return projectService.get(id, authentication.getName());
    }

    @PostMapping
    ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request,
            Authentication authentication) {
        ProjectResponse created = projectService.create(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    ProjectResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request,
            Authentication authentication) {
        return projectService.update(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        projectService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
