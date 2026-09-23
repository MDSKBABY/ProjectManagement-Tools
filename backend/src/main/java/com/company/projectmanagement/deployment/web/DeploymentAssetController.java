package com.company.projectmanagement.deployment.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.deployment.domain.DeploymentAssetType;
import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.deployment.domain.RiskLevel;
import com.company.projectmanagement.deployment.service.DeploymentAssetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目部署资产查询、创建和不可变版本历史接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/deployment-assets")
public class DeploymentAssetController {

    private final DeploymentAssetService deploymentAssetService;

    public DeploymentAssetController(DeploymentAssetService deploymentAssetService) {
        this.deploymentAssetService = deploymentAssetService;
    }

    @GetMapping
    public PageResponse<DeploymentAssetResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DeploymentAssetType assetType,
            @RequestParam(required = false) String operatingSystem,
            @RequestParam(required = false) String architecture,
            @RequestParam(required = false) DeploymentEnvironment environment,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) String tag,
            Principal principal) {
        return deploymentAssetService.list(
                projectId,
                page,
                pageSize,
                keyword,
                assetType,
                operatingSystem,
                architecture,
                environment,
                riskLevel,
                tag,
                principal.getName());
    }

    @GetMapping("/{assetId}")
    public DeploymentAssetResponse detail(
            @PathVariable Long projectId,
            @PathVariable Long assetId,
            Principal principal) {
        return deploymentAssetService.detail(projectId, assetId, principal.getName());
    }

    @PostMapping
    public ResponseEntity<DeploymentAssetResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateDeploymentAssetRequest request,
            Principal principal) {
        DeploymentAssetResponse response = deploymentAssetService.create(
                projectId, request, principal.getName());
        return ResponseEntity.created(URI.create(
                        "/api/v1/projects/%d/deployment-assets/%d"
                                .formatted(projectId, response.id())))
                .body(response);
    }

    @GetMapping("/groups/{assetGroupId}/versions")
    public List<DeploymentAssetResponse> versions(
            @PathVariable Long projectId,
            @PathVariable String assetGroupId,
            Principal principal) {
        return deploymentAssetService.versions(projectId, assetGroupId, principal.getName());
    }
}
