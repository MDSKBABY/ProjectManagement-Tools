package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.projectmanagement.identity.domain.AppUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证部署资产版本、文件关联、筛选规则和项目级权限。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=deployment-asset-admin",
        "app.bootstrap-admin.password=Test-only-deployment-asset-password-123!",
        "app.bootstrap-admin.display-name=部署资产管理员"
})
@AutoConfigureMockMvc
@Testcontainers
class DeploymentAssetApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanTestData() {
        jdbcTemplate.update("DELETE FROM deployment_asset");
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'deployment-asset-admin'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'DEPLOYMENT_ASSET'");
    }

    @Test
    void requiresFunctionalPermissionAndCsrf() throws Exception {
        AppUser owner = insertUser("asset-permission-owner", "资产权限负责人");
        long projectId = insertProjectWithOwner("ASSET-PERMISSION", owner);
        long fileId = insertFile(projectId, owner.getId(), "package.zip", "AVAILABLE");

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-assets", projectId)
                        .with(actor(owner.getUsername(), "project:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-assets", projectId)
                        .with(actor(owner.getUsername(), "deployment_asset:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(packageRequest(fileId, null, "1.0.0")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void createsScriptWithRequiredInstructionsFileLinkAndAudit() throws Exception {
        AppUser owner = insertUser("asset-script-owner", "脚本资产负责人");
        long projectId = insertProjectWithOwner("ASSET-SCRIPT", owner);
        long fileId = insertFile(projectId, owner.getId(), "deploy.sh", "AVAILABLE");

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-assets", projectId)
                        .with(actor(owner.getUsername(), "deployment_asset:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scriptRequest(fileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetType").value("SCRIPT"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.versionLabel").value("1.0.0"))
                .andExpect(jsonPath("$.file.id").value(fileId))
                .andExpect(jsonPath("$.tags.length()").value(2));

        long assetId = jdbcTemplate.queryForObject("SELECT id FROM deployment_asset", Long.class);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM file_link
                WHERE file_asset_id = ? AND project_id = ?
                  AND business_type = 'DEPLOYMENT_ASSET' AND business_id = ?
                """, Integer.class, fileId, projectId, assetId)).isEqualTo(1);
        assertThat(auditCount("DEPLOYMENT_ASSET_CREATED", assetId)).isEqualTo(1);
    }

    @Test
    void rejectsIncompleteScriptsUnavailableFilesAndCrossProjectFiles() throws Exception {
        AppUser owner = insertUser("asset-validation-owner", "资产校验负责人");
        long projectId = insertProjectWithOwner("ASSET-VALIDATION", owner);
        long otherProjectId = insertProjectWithOwner("ASSET-OTHER", owner);
        long availableFileId = insertFile(projectId, owner.getId(), "deploy.sh", "AVAILABLE");
        long reservedFileId = insertFile(projectId, owner.getId(), "pending.zip", "RESERVED");
        long foreignFileId = insertFile(otherProjectId, owner.getId(), "foreign.zip", "AVAILABLE");

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-assets", projectId)
                        .with(actor(owner.getUsername(), "deployment_asset:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scriptRequest(availableFileId).replace(
                                "\"rollbackInstructions\":\"恢复上一个版本\"",
                                "\"rollbackInstructions\":\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SCRIPT_INSTRUCTIONS_REQUIRED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-assets", projectId)
                        .with(actor(owner.getUsername(), "deployment_asset:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(packageRequest(reservedFileId, null, "1.0.0")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_AVAILABLE"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-assets", projectId)
                        .with(actor(owner.getUsername(), "deployment_asset:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(packageRequest(foreignFileId, null, "1.0.0")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void createsImmutableVersionsAndFiltersAssetList() throws Exception {
        AppUser owner = insertUser("asset-version-owner", "资产版本负责人");
        long projectId = insertProjectWithOwner("ASSET-VERSION", owner);
        long firstFileId = insertFile(projectId, owner.getId(), "package-v1.zip", "AVAILABLE");
        JsonNode first = create(projectId, owner, packageRequest(firstFileId, null, "1.0.0"));
        long secondFileId = insertFile(projectId, owner.getId(), "package-v2.zip", "AVAILABLE");
        JsonNode second = create(projectId, owner, packageRequest(
                secondFileId, first.get("assetGroupId").asText(), "1.1.0"));

        assertThat(second.get("version").asInt()).isEqualTo(2);
        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-assets", projectId)
                        .param("operatingSystem", "linux")
                        .param("architecture", "amd64")
                        .param("environment", "PRODUCTION")
                        .param("riskLevel", "HIGH")
                        .param("tag", "核心")
                        .with(actor(owner.getUsername(), "deployment_asset:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.totalItems").value(2));

        mockMvc.perform(get(
                                "/api/v1/projects/{projectId}/deployment-assets/groups/{assetGroupId}/versions",
                                projectId,
                                first.get("assetGroupId").asText())
                        .with(actor(owner.getUsername(), "deployment_asset:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].version").value(1));
    }

    @Test
    void hidesAssetsFromProjectOutsiders() throws Exception {
        AppUser owner = insertUser("asset-visible-owner", "资产可见负责人");
        AppUser outsider = insertUser("asset-outsider", "资产外部用户");
        long projectId = insertProjectWithOwner("ASSET-VISIBLE", owner);
        long fileId = insertFile(projectId, owner.getId(), "manual.pdf", "AVAILABLE");
        JsonNode asset = create(projectId, owner, packageRequest(fileId, null, "1.0.0"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-assets/{assetId}",
                                projectId, asset.get("id").asLong())
                        .with(actor(outsider.getUsername(), "deployment_asset:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    private JsonNode create(long projectId, AppUser actor, String request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-assets", projectId)
                        .with(actor(actor.getUsername(), "deployment_asset:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private AppUser insertUser(String username, String displayName) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, display_name)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, username,
                "{bcrypt}$2a$12$test-hash-not-used-by-mock-authentication", displayName);
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        return user;
    }

    private long insertProjectWithOwner(String code, AppUser owner) {
        long projectId = jdbcTemplate.queryForObject("""
                INSERT INTO project (code, name, status, owner_id, created_by)
                VALUES (?, ?, 'PLANNING', ?, ?) RETURNING id
                """, Long.class, code, code, owner.getId(), owner.getId());
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, project_role, created_by)
                VALUES (?, ?, 'OWNER', ?)
                """, projectId, owner.getId(), owner.getId());
        return projectId;
    }

    private long insertFile(long projectId, long userId, String name, String status) {
        Long fileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_asset (
                    file_group_id, version, original_name, storage_key, media_type,
                    size_bytes, sha256, status, uploaded_by
                ) VALUES (gen_random_uuid(), 1, ?, ?, 'application/octet-stream', 12,
                          repeat('a', 64), ?, ?) RETURNING id
                """, Long.class, name, "projects/" + projectId + "/" + name, status, userId);
        jdbcTemplate.update("""
                INSERT INTO file_link (file_asset_id, project_id, business_type, business_id, created_by)
                VALUES (?, ?, 'PROJECT_DOCUMENT', ?, ?)
                """, fileId, projectId, projectId, userId);
        return fileId;
    }

    private int auditCount(String action, long assetId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND resource_type = 'DEPLOYMENT_ASSET'
                  AND resource_id = ? AND outcome = 'SUCCESS'
                """, Integer.class, action, Long.toString(assetId));
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String packageRequest(long fileId, String groupId, String versionLabel) {
        String group = groupId == null ? "" : "\"assetGroupId\":\"" + groupId + "\",";
        return """
                {
                  %s
                  "name":"核心服务安装包",
                  "assetType":"INSTALLATION_PACKAGE",
                  "versionLabel":"%s",
                  "fileAssetId":%d,
                  "operatingSystem":"Linux",
                  "architecture":"amd64",
                  "environment":"PRODUCTION",
                  "riskLevel":"HIGH",
                  "tags":["核心","Java"],
                  "description":"生产环境安装包"
                }
                """.formatted(group, versionLabel, fileId);
    }

    private static String scriptRequest(long fileId) {
        return """
                {
                  "name":"自动部署脚本",
                  "assetType":"SCRIPT",
                  "versionLabel":"1.0.0",
                  "fileAssetId":%d,
                  "operatingSystem":"Linux",
                  "architecture":"amd64",
                  "environment":"PRODUCTION",
                  "riskLevel":"HIGH",
                  "tags":["部署","核心"],
                  "prerequisites":"安装 Java 21",
                  "executionInstructions":"使用受限账号执行 ./deploy.sh",
                  "rollbackInstructions":"恢复上一个版本"
                }
                """.formatted(fileId);
    }
}
