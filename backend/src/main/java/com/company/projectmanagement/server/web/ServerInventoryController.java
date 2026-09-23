package com.company.projectmanagement.server.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.server.domain.ServerStatus;
import com.company.projectmanagement.server.service.ServerInventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.CacheControl;
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

/** 服务器档案 CRUD 与独立敏感凭据接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/servers")
public class ServerInventoryController {

    private final ServerInventoryService serverInventoryService;

    public ServerInventoryController(ServerInventoryService serverInventoryService) {
        this.serverInventoryService = serverInventoryService;
    }

    @GetMapping
    public PageResponse<ServerResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DeploymentEnvironment environment,
            @RequestParam(required = false) ServerStatus status,
            Principal principal) {
        return serverInventoryService.list(
                projectId, page, pageSize, keyword, environment, status, principal.getName());
    }

    @GetMapping("/{serverId}")
    public ServerResponse detail(
            @PathVariable Long projectId,
            @PathVariable Long serverId,
            Principal principal) {
        return serverInventoryService.detail(projectId, serverId, principal.getName());
    }

    @PostMapping
    public ResponseEntity<ServerResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveServerRequest request,
            Principal principal) {
        ServerResponse response = serverInventoryService.create(
                projectId, request, principal.getName());
        return ResponseEntity.created(URI.create(
                        "/api/v1/projects/%d/servers/%d".formatted(projectId, response.id())))
                .body(response);
    }

    @PutMapping("/{serverId}")
    public ServerResponse update(
            @PathVariable Long projectId,
            @PathVariable Long serverId,
            @Valid @RequestBody SaveServerRequest request,
            Principal principal) {
        return serverInventoryService.update(projectId, serverId, request, principal.getName());
    }

    @DeleteMapping("/{serverId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long serverId,
            Principal principal) {
        serverInventoryService.delete(projectId, serverId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{serverId}/credential")
    public ResponseEntity<Void> saveCredential(
            @PathVariable Long projectId,
            @PathVariable Long serverId,
            @Valid @RequestBody SaveServerCredentialRequest request,
            Principal principal) {
        serverInventoryService.saveCredential(projectId, serverId, request, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{serverId}/credential")
    public ResponseEntity<ServerCredentialResponse> viewCredential(
            @PathVariable Long projectId,
            @PathVariable Long serverId,
            Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(serverInventoryService.viewCredential(
                        projectId, serverId, principal.getName()));
    }
}
