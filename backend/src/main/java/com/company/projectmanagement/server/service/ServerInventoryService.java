package com.company.projectmanagement.server.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.server.domain.ServerCredential;
import com.company.projectmanagement.server.domain.ServerRecord;
import com.company.projectmanagement.server.domain.ServerStatus;
import com.company.projectmanagement.server.mapper.ServerRecordMapper;
import com.company.projectmanagement.server.security.ServerCredentialCipher;
import com.company.projectmanagement.server.web.SaveServerCredentialRequest;
import com.company.projectmanagement.server.web.SaveServerRequest;
import com.company.projectmanagement.server.web.ServerCredentialResponse;
import com.company.projectmanagement.server.web.ServerResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理项目服务器档案，并把凭据解密限制在项目负责人和系统管理员范围内。
 */
@Service
public class ServerInventoryService {

    private final ServerRecordMapper serverRecordMapper;
    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final ServerCredentialCipher credentialCipher;

    public ServerInventoryService(
            ServerRecordMapper serverRecordMapper,
            ProjectMapper projectMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper,
            ServerCredentialCipher credentialCipher) {
        this.serverRecordMapper = serverRecordMapper;
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.credentialCipher = credentialCipher;
    }

    public PageResponse<ServerResponse> list(
            Long projectId,
            int page,
            int pageSize,
            String keyword,
            DeploymentEnvironment environment,
            ServerStatus status,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        String normalizedKeyword = normalizeNullable(keyword);
        long total = serverRecordMapper.count(projectId, normalizedKeyword, environment, status);
        List<ServerResponse> data = serverRecordMapper.selectPage(
                        projectId,
                        normalizedKeyword,
                        environment,
                        status,
                        (page - 1) * pageSize,
                        pageSize)
                .stream()
                .map(ServerResponse::from)
                .toList();
        return PageResponse.of(data, page, pageSize, total);
    }

    public ServerResponse detail(Long projectId, Long serverId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        return ServerResponse.from(requireServer(projectId, serverId));
    }

    @Transactional
    public ServerResponse create(
            Long projectId, SaveServerRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        ServerRecord server = map(request, new ServerRecord());
        server.setProjectId(projectId);
        server.setCreatedBy(actor.user().getId());
        server.setUpdatedBy(actor.user().getId());
        serverRecordMapper.insert(server);
        serverRecordMapper.recordAudit(
                actor.user().getId(), "SERVER_CREATED", "SERVER",
                server.getId().toString(), projectId);
        return ServerResponse.from(serverRecordMapper.selectById(projectId, server.getId()));
    }

    @Transactional
    public ServerResponse update(
            Long projectId, Long serverId, SaveServerRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        requireServer(projectId, serverId);
        ServerRecord server = map(request, new ServerRecord());
        server.setId(serverId);
        server.setProjectId(projectId);
        server.setUpdatedBy(actor.user().getId());
        serverRecordMapper.update(server);
        serverRecordMapper.recordAudit(
                actor.user().getId(), "SERVER_UPDATED", "SERVER",
                serverId.toString(), projectId);
        return ServerResponse.from(serverRecordMapper.selectById(projectId, serverId));
    }

    @Transactional
    public void delete(Long projectId, Long serverId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        requireServer(projectId, serverId);
        // 服务器采用软删除，但凭据密文不承担历史展示职责，应立即最小化清理。
        serverRecordMapper.deleteCredential(serverId);
        serverRecordMapper.softDelete(projectId, serverId, actor.user().getId());
        serverRecordMapper.recordAudit(
                actor.user().getId(), "SERVER_DELETED", "SERVER",
                serverId.toString(), projectId);
    }

    /** 保存凭据前再次检查资源级负责人身份；审计详情不包含任何凭据字段。 */
    @Transactional
    public void saveCredential(
            Long projectId,
            Long serverId,
            SaveServerCredentialRequest request,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireCredentialAccess(projectId, actor);
        requireServer(projectId, serverId);
        ServerCredentialCipher.EncryptedCredential encrypted = credentialCipher.encrypt(
                projectId, serverId, request.username().trim(), request.password());
        ServerCredential credential = new ServerCredential();
        credential.setServerId(serverId);
        credential.setCiphertext(encrypted.ciphertext());
        credential.setNonce(encrypted.nonce());
        credential.setKeyVersion(encrypted.keyVersion());
        credential.setUpdatedBy(actor.user().getId());
        serverRecordMapper.upsertCredential(credential);
        serverRecordMapper.recordAudit(
                actor.user().getId(), "SERVER_CREDENTIAL_CHANGED", "SERVER_CREDENTIAL",
                serverId.toString(), projectId);
    }

    /** 显式查看才会解密，并在解密成功后立即记录审计。 */
    @Transactional
    public ServerCredentialResponse viewCredential(
            Long projectId, Long serverId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireCredentialAccess(projectId, actor);
        requireServer(projectId, serverId);
        ServerCredential credential = serverRecordMapper.selectCredential(serverId);
        if (credential == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SERVER_CREDENTIAL_NOT_CONFIGURED",
                    "该服务器尚未配置凭据");
        }
        ServerCredentialCipher.PlainCredential plaintext = credentialCipher.decrypt(
                projectId,
                serverId,
                credential.getCiphertext(),
                credential.getNonce(),
                credential.getKeyVersion());
        serverRecordMapper.recordAudit(
                actor.user().getId(), "SERVER_CREDENTIAL_VIEWED", "SERVER_CREDENTIAL",
                serverId.toString(), projectId);
        return new ServerCredentialResponse(plaintext.username(), plaintext.password());
    }

    private Project requireVisibleProject(Long projectId, Actor actor) {
        Project project = projectMapper.selectVisibleById(
                projectId, actor.user().getId(), actor.administrator());
        if (project == null) {
            throw projectNotFound();
        }
        return project;
    }

    private Project requireWritableProject(Long projectId, Actor actor) {
        Project project = projectMapper.selectByIdIncludingDeleted(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw projectNotFound();
        }
        if (!actor.administrator()
                && !projectMapper.canWriteFiles(projectId, actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "无权维护该项目的服务器档案");
        }
        return project;
    }

    private void requireCredentialAccess(Long projectId, Actor actor) {
        Project project = projectMapper.selectByIdIncludingDeleted(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw projectNotFound();
        }
        if (!actor.administrator() && !project.getOwnerId().equals(actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "SERVER_CREDENTIAL_ACCESS_DENIED",
                    "只有项目负责人或系统管理员可以维护和查看服务器凭据");
        }
    }

    private ServerRecord requireServer(Long projectId, Long serverId) {
        ServerRecord server = serverRecordMapper.selectById(projectId, serverId);
        if (server == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVER_NOT_FOUND", "服务器档案不存在");
        }
        return server;
    }

    private Actor requireActor(String username) {
        AppUser user = appUserMapper.selectActiveByUsername(username);
        if (user == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        }
        boolean administrator = identityAccessMapper.selectRoleCodesByUserId(user.getId())
                .contains("ADMIN");
        return new Actor(user, administrator);
    }

    private static ServerRecord map(SaveServerRequest request, ServerRecord server) {
        server.setName(request.name().trim());
        server.setHost(request.host().trim());
        server.setPort(request.port());
        server.setEnvironment(request.environment());
        server.setStatus(request.status());
        server.setOperatingSystem(normalizeNullable(request.operatingSystem()));
        server.setArchitecture(normalizeNullable(request.architecture()));
        server.setPurpose(normalizeNullable(request.purpose()));
        server.setDescription(normalizeNullable(request.description()));
        return server;
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
    }

    private record Actor(AppUser user, boolean administrator) { }
}
