package com.company.projectmanagement.solution.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.solution.domain.DeploymentSolutionStatus;
import com.company.projectmanagement.solution.service.DeploymentSolutionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
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

@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/deployment-solutions")
public class DeploymentSolutionController {
    private final DeploymentSolutionService service;

    public DeploymentSolutionController(DeploymentSolutionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<DeploymentSolutionSummaryResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DeploymentSolutionStatus status,
            @RequestParam(required = false) Long fingerprintId,
            Principal principal) {
        return service.list(projectId, page, pageSize, keyword, status, fingerprintId, principal.getName());
    }

    @GetMapping("/{solutionId}")
    public DeploymentSolutionResponse detail(
            @PathVariable Long projectId, @PathVariable Long solutionId, Principal principal) {
        return service.detail(projectId, solutionId, principal.getName());
    }

    @PostMapping
    public ResponseEntity<DeploymentSolutionResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveDeploymentSolutionRequest request,
            Principal principal) {
        DeploymentSolutionResponse response = service.create(projectId, request, principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/projects/%d/deployment-solutions/%d"
                        .formatted(projectId, response.id())))
                .body(response);
    }

    @PutMapping("/{solutionId}")
    public DeploymentSolutionResponse update(
            @PathVariable Long projectId,
            @PathVariable Long solutionId,
            @Valid @RequestBody SaveDeploymentSolutionRequest request,
            Principal principal) {
        return service.update(projectId, solutionId, request, principal.getName());
    }

    @DeleteMapping("/{solutionId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long projectId, @PathVariable Long solutionId, Principal principal) {
        service.delete(projectId, solutionId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
