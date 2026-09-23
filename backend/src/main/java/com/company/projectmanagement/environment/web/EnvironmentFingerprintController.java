package com.company.projectmanagement.environment.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.environment.service.EnvironmentFingerprintService;
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
@RequestMapping("/api/v1/projects/{projectId}/environment-fingerprints")
public class EnvironmentFingerprintController {
    private final EnvironmentFingerprintService service;

    public EnvironmentFingerprintController(EnvironmentFingerprintService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<EnvironmentFingerprintResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DeploymentEnvironment environment,
            @RequestParam(required = false) String operatingSystem,
            @RequestParam(required = false) String architecture,
            @RequestParam(required = false) String databaseName,
            @RequestParam(required = false) String middleware,
            @RequestParam(required = false) String tag,
            Principal principal) {
        return service.list(projectId, page, pageSize, keyword, environment, operatingSystem,
                architecture, databaseName, middleware, tag, principal.getName());
    }

    @GetMapping("/{fingerprintId}")
    public EnvironmentFingerprintResponse detail(
            @PathVariable Long projectId, @PathVariable Long fingerprintId, Principal principal) {
        return service.detail(projectId, fingerprintId, principal.getName());
    }

    @PostMapping
    public ResponseEntity<EnvironmentFingerprintResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveEnvironmentFingerprintRequest request,
            Principal principal) {
        EnvironmentFingerprintResponse response = service.create(projectId, request, principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/projects/%d/environment-fingerprints/%d"
                        .formatted(projectId, response.id())))
                .body(response);
    }

    @PutMapping("/{fingerprintId}")
    public EnvironmentFingerprintResponse update(
            @PathVariable Long projectId,
            @PathVariable Long fingerprintId,
            @Valid @RequestBody SaveEnvironmentFingerprintRequest request,
            Principal principal) {
        return service.update(projectId, fingerprintId, request, principal.getName());
    }

    @DeleteMapping("/{fingerprintId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long projectId, @PathVariable Long fingerprintId, Principal principal) {
        service.delete(projectId, fingerprintId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
