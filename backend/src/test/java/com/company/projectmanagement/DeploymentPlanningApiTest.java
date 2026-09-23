package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** 验证环境指纹、部署方案与精确资产版本编排。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=planning-api-admin",
        "app.bootstrap-admin.password=Test-only-planning-api-password-123!",
        "app.bootstrap-admin.display-name=部署规划测试管理员"
})
@AutoConfigureMockMvc
@Testcontainers
class DeploymentPlanningApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void cleanTestData() {
        if (tableExists("deployment_solution_step")) jdbcTemplate.update("DELETE FROM deployment_solution_step");
        if (tableExists("deployment_solution")) jdbcTemplate.update("DELETE FROM deployment_solution");
        if (tableExists("environment_fingerprint")) jdbcTemplate.update("DELETE FROM environment_fingerprint");
        jdbcTemplate.update("DELETE FROM deployment_asset");
        jdbcTemplate.update("DELETE FROM file_link");
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'planning-api-admin'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type IN ('ENVIRONMENT_FINGERPRINT', 'DEPLOYMENT_SOLUTION')");
    }

    @Test
    void createsFiltersUpdatesAndSoftDeletesEnvironmentFingerprint() throws Exception {
        AppUser owner = insertUser("fingerprint-owner", "环境负责人");
        long projectId = insertProjectWithOwner("FINGERPRINT-CRUD", owner);

        JsonNode created = createFingerprint(projectId, owner, fingerprintRequest("生产区 Java 集群", "PostgreSQL"));
        long fingerprintId = created.get("id").asLong();
        assertThat(created.get("middlewares").get(0).asText()).isEqualTo("Nginx");

        mockMvc.perform(get("/api/v1/projects/{projectId}/environment-fingerprints", projectId)
                        .param("environment", "PRODUCTION")
                        .param("operatingSystem", "linux")
                        .param("architecture", "amd64")
                        .param("databaseName", "postgresql")
                        .param("middleware", "nginx")
                        .param("tag", "核心")
                        .with(actor(owner.getUsername(), "environment_fingerprint:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        mockMvc.perform(put("/api/v1/projects/{projectId}/environment-fingerprints/{fingerprintId}", projectId, fingerprintId)
                        .with(actor(owner.getUsername(), "environment_fingerprint:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fingerprintRequest("生产区 Java 集群 v2", "PostgreSQL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("生产区 Java 集群 v2"));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/environment-fingerprints/{fingerprintId}", projectId, fingerprintId)
                        .with(actor(owner.getUsername(), "environment_fingerprint:write"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/{projectId}/environment-fingerprints/{fingerprintId}", projectId, fingerprintId)
                        .with(actor(owner.getUsername(), "environment_fingerprint:read")))
                .andExpect(status().isNotFound());

        assertThat(auditCount("ENVIRONMENT_FINGERPRINT_CREATED", fingerprintId)).isEqualTo(1);
        assertThat(auditCount("ENVIRONMENT_FINGERPRINT_UPDATED", fingerprintId)).isEqualTo(1);
        assertThat(auditCount("ENVIRONMENT_FINGERPRINT_DELETED", fingerprintId)).isEqualTo(1);
    }

    @Test
    void createsUpdatesAndDeletesSolutionWithOrderedExactAssetVersions() throws Exception {
        AppUser owner = insertUser("solution-owner", "方案负责人");
        long projectId = insertProjectWithOwner("SOLUTION-CRUD", owner);
        long fingerprintId = createFingerprint(projectId, owner,
                fingerprintRequest("生产指纹", "PostgreSQL")).get("id").asLong();
        long assetV1 = insertAsset(projectId, owner.getId(), "部署脚本", "1.0.0");
        long assetV2 = insertAsset(projectId, owner.getId(), "应用安装包", "2.0.0");

        JsonNode created = createSolution(projectId, owner,
                solutionRequest("生产双机方案", fingerprintId, "DRAFT", assetV1, assetV2, true));
        long solutionId = created.get("id").asLong();
        assertThat(created.get("steps").get(0).get("stepOrder").asInt()).isEqualTo(1);
        assertThat(created.get("steps").get(0).get("assetVersionLabel").asText()).isEqualTo("1.0.0");
        assertThat(created.get("steps").get(1).get("stepOrder").asInt()).isEqualTo(2);

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-solutions", projectId)
                        .param("status", "DRAFT")
                        .param("fingerprintId", Long.toString(fingerprintId))
                        .with(actor(owner.getUsername(), "deployment_solution:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].stepCount").value(2));

        mockMvc.perform(put("/api/v1/projects/{projectId}/deployment-solutions/{solutionId}", projectId, solutionId)
                        .with(actor(owner.getUsername(), "deployment_solution:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solutionRequest("生产双机方案 v2", fingerprintId,
                                "ACTIVE", assetV2, assetV2, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].assetId").value(assetV2));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/environment-fingerprints/{fingerprintId}", projectId, fingerprintId)
                        .with(actor(owner.getUsername(), "environment_fingerprint:write"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ENVIRONMENT_FINGERPRINT_IN_USE"));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/deployment-solutions/{solutionId}", projectId, solutionId)
                        .with(actor(owner.getUsername(), "deployment_solution:write"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-solutions/{solutionId}", projectId, solutionId)
                        .with(actor(owner.getUsername(), "deployment_solution:read")))
                .andExpect(status().isNotFound());

        assertThat(auditCount("DEPLOYMENT_SOLUTION_CREATED", solutionId)).isEqualTo(1);
        assertThat(auditCount("DEPLOYMENT_SOLUTION_UPDATED", solutionId)).isEqualTo(1);
        assertThat(auditCount("DEPLOYMENT_SOLUTION_DELETED", solutionId)).isEqualTo(1);
    }

    @Test
    void rejectsCrossProjectFingerprintAndAssetReferences() throws Exception {
        AppUser owner = insertUser("solution-boundary-owner", "边界负责人");
        long projectId = insertProjectWithOwner("SOLUTION-BOUNDARY", owner);
        long otherProjectId = insertProjectWithOwner("SOLUTION-OTHER", owner);
        long localFingerprintId = createFingerprint(projectId, owner,
                fingerprintRequest("本地指纹", "PostgreSQL")).get("id").asLong();
        long foreignFingerprintId = createFingerprint(otherProjectId, owner,
                fingerprintRequest("外部指纹", "MySQL")).get("id").asLong();
        long localAssetId = insertAsset(projectId, owner.getId(), "本地资产", "1.0.0");
        long foreignAssetId = insertAsset(otherProjectId, owner.getId(), "外部资产", "1.0.0");

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-solutions", projectId)
                        .with(actor(owner.getUsername(), "deployment_solution:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(solutionRequest("跨项目指纹", foreignFingerprintId,
                                "DRAFT", localAssetId, localAssetId, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ENVIRONMENT_FINGERPRINT_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-solutions", projectId)
                        .with(actor(owner.getUsername(), "deployment_solution:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(solutionRequest("跨项目资产", localFingerprintId,
                                "DRAFT", foreignAssetId, foreignAssetId, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_ASSET_NOT_FOUND"));
    }

    @Test
    void enforcesPermissionsProjectMembershipCsrfAndSolutionValidation() throws Exception {
        AppUser owner = insertUser("planning-access-owner", "权限负责人");
        AppUser outsider = insertUser("planning-outsider", "项目外用户");
        long projectId = insertProjectWithOwner("PLANNING-ACCESS", owner);
        long fingerprintId = createFingerprint(projectId, owner,
                fingerprintRequest("权限指纹", "PostgreSQL")).get("id").asLong();
        long assetId = insertAsset(projectId, owner.getId(), "权限资产", "1.0.0");

        mockMvc.perform(get("/api/v1/projects/{projectId}/environment-fingerprints", projectId)
                        .with(actor(owner.getUsername(), "project:read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/{projectId}/environment-fingerprints", projectId)
                        .with(actor(outsider.getUsername(), "environment_fingerprint:read")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-solutions", projectId)
                        .with(actor(owner.getUsername(), "deployment_solution:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solutionRequest("缺少 CSRF", fingerprintId,
                                "DRAFT", assetId, assetId, false)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-solutions", projectId)
                        .with(actor(owner.getUsername(), "deployment_solution:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(solutionRequest("重复顺序", fingerprintId,
                                "DRAFT", assetId, assetId, true).replace("\"stepOrder\":2", "\"stepOrder\":1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_SOLUTION_STEP_ORDER_DUPLICATE"));
    }

    private JsonNode createFingerprint(long projectId, AppUser actor, String request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/environment-fingerprints", projectId)
                        .with(actor(actor.getUsername(), "environment_fingerprint:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createSolution(long projectId, AppUser actor, String request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-solutions", projectId)
                        .with(actor(actor.getUsername(), "deployment_solution:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andReturn();
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

    private long insertAsset(long projectId, long userId, String name, String versionLabel) {
        Long fileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_asset (
                    file_group_id, version, original_name, storage_key, media_type,
                    size_bytes, sha256, status, uploaded_by
                ) VALUES (gen_random_uuid(), 1, ?, ?, 'application/octet-stream', 10,
                          repeat('a', 64), 'AVAILABLE', ?) RETURNING id
                """, Long.class, name + ".zip", "planning/" + projectId + "/" + name, userId);
        jdbcTemplate.update("""
                INSERT INTO file_link (file_asset_id, project_id, business_type, business_id, created_by)
                VALUES (?, ?, 'PROJECT_DOCUMENT', ?, ?)
                """, fileId, projectId, projectId, userId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO deployment_asset (
                    asset_group_id, version, project_id, name, asset_type, version_label,
                    file_asset_id, environment, risk_level, tags, created_by
                ) VALUES (gen_random_uuid(), 1, ?, ?, 'INSTALLATION_PACKAGE', ?, ?,
                          'PRODUCTION', 'MEDIUM', '[]'::jsonb, ?) RETURNING id
                """, Long.class, projectId, name, versionLabel, fileId, userId);
    }

    private int auditCount(String action, long resourceId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND resource_id = ? AND outcome = 'SUCCESS'
                """, Integer.class, action, Long.toString(resourceId));
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.' || ?) IS NOT NULL", Boolean.class, tableName));
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String fingerprintRequest(String name, String databaseName) {
        return """
                {
                  "name":"%s",
                  "environment":"PRODUCTION",
                  "operatingSystem":"Linux",
                  "osVersion":"Rocky Linux 9.4",
                  "kernelVersion":"5.14",
                  "architecture":"amd64",
                  "runtimeName":"Java",
                  "runtimeVersion":"21",
                  "databaseName":"%s",
                  "databaseVersion":"17",
                  "middlewares":["Nginx","Valkey"],
                  "networkZone":"production-dmz",
                  "tags":["核心","Java"],
                  "notes":"主生产集群"
                }
                """.formatted(name, databaseName);
    }

    private static String solutionRequest(
            String name, long fingerprintId, String status, long firstAssetId,
            long secondAssetId, boolean includeSecondStep) {
        String secondStep = includeSecondStep ? """
                ,{
                  "stepOrder":2,
                  "title":"启动应用",
                  "instructions":"执行应用启动并检查健康端点",
                  "assetId":%d,
                  "parametersTemplate":"--profile=production"
                }
                """.formatted(secondAssetId) : "";
        return """
                {
                  "name":"%s",
                  "scenario":"生产环境标准化部署",
                  "fingerprintId":%d,
                  "architectureDescription":"Nginx 双节点与应用服务",
                  "prerequisites":"备份当前版本并确认维护窗口",
                  "rollbackSteps":"恢复上一版本并重启服务",
                  "riskNotes":"启动失败可能造成短时中断",
                  "status":"%s",
                  "steps":[{
                    "stepOrder":1,
                    "title":"发布安装包",
                    "instructions":"校验文件后替换当前版本",
                    "assetId":%d,
                    "parametersTemplate":"--verify-sha256"
                  }%s]
                }
                """.formatted(name, fingerprintId, status, firstAssetId, secondStep);
    }
}
