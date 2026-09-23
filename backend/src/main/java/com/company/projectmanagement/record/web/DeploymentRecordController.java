package com.company.projectmanagement.record.web;

import com.company.projectmanagement.record.service.DeploymentRecordService;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.record.domain.DeploymentResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/deployment-records")
public class DeploymentRecordController {
    private final DeploymentRecordService service;

    public DeploymentRecordController(DeploymentRecordService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<DeploymentRecordSummaryResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) DeploymentResult result,
            @RequestParam(required = false) Long serverId,
            @RequestParam(required = false) Boolean baseline,
            @RequestParam(required = false) OffsetDateTime executedFrom,
            @RequestParam(required = false) OffsetDateTime executedTo,
            Principal principal) {
        return service.list(projectId, page, pageSize, result, serverId, baseline,
                executedFrom, executedTo, principal.getName());
    }

    @GetMapping("/similar")
    public List<SimilarDeploymentResponse> similar(
            @PathVariable Long projectId,
            @RequestParam Long fingerprintId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit,
            Principal principal) {
        return service.similar(projectId, fingerprintId, limit, principal.getName());
    }

    @GetMapping("/{recordId}")
    public DeploymentRecordResponse detail(
            @PathVariable Long projectId, @PathVariable Long recordId, Principal principal) {
        return service.detail(projectId, recordId, principal.getName());
    }

    @PostMapping
    public ResponseEntity<DeploymentRecordResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateDeploymentRecordRequest request,
            Principal principal) {
        DeploymentRecordResponse response = service.create(projectId, request, principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/projects/%d/deployment-records/%d"
                        .formatted(projectId, response.id())))
                .body(response);
    }

    @PutMapping("/{recordId}/baseline")
    public DeploymentRecordResponse updateBaseline(
            @PathVariable Long projectId,
            @PathVariable Long recordId,
            @Valid @RequestBody UpdateDeploymentBaselineRequest request,
            Principal principal) {
        return service.updateBaseline(projectId, recordId, request.baseline(), principal.getName());
    }
}
