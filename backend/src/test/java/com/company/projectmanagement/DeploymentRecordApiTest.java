package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证部署记录保存执行时快照，而不是只依赖后续可变的业务表。 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DeploymentRecordApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void cleanTestData() {
        if (tableExists("deployment_record")) jdbcTemplate.update("DELETE FROM deployment_record");
        jdbcTemplate.update("DELETE FROM deployment_solution_step");
        jdbcTemplate.update("DELETE FROM deployment_solution");
        jdbcTemplate.update("DELETE FROM environment_fingerprint");
        jdbcTemplate.update("DELETE FROM deployment_asset");
        jdbcTemplate.update("DELETE FROM file_link");
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM server_record");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username LIKE 'record-%'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'DEPLOYMENT_RECORD'");
    }

    @Test
    void createsADeploymentRecordWithExecutionSnapshots() throws Exception {
        long userId = insertUser("record-owner", "部署负责人");
        long projectId = insertProject(userId);
        long serverId = insertServer(projectId, userId);
        long fingerprintId = insertFingerprint(projectId, userId);
        long assetId = insertAsset(projectId, userId);
        long solutionId = insertSolution(projectId, fingerprintId, assetId, userId);

        JsonNode response = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/projects/{projectId}/deployment-records", projectId)
                        .with(user("record-owner")
                                .authorities(new SimpleGrantedAuthority("deployment_record:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serverId": %d,
                                  "solutionId": %d,
                                  "executedAt": "2026-09-22T10:00:00+08:00",
                                  "result": "SUCCESS",
                                  "notes": "发布后健康检查正常"
                                }
                                """.formatted(serverId, solutionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serverSnapshot.name").value("生产应用服务器"))
                .andExpect(jsonPath("$.environmentSnapshot.operatingSystem").value("Linux"))
                .andExpect(jsonPath("$.solutionSnapshot.steps[0].assetVersionLabel").value("9.0.0"))
                .andReturn().getResponse().getContentAsString());

        long recordId = response.get("id").asLong();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT solution_snapshot -> 'steps' -> 0 ->> 'assetVersionLabel'
                FROM deployment_record WHERE id = ?
                """, String.class, recordId)).isEqualTo("9.0.0");
    }

    @Test
    void keepsSnapshotsImmutableAndListsWithFilters() throws Exception {
        long userId = insertUser("record-history", "历史负责人");
        long projectId = insertProject(userId);
        long serverId = insertServer(projectId, userId);
        long fingerprintId = insertFingerprint(projectId, userId);
        long assetId = insertAsset(projectId, userId);
        long solutionId = insertSolution(projectId, fingerprintId, assetId, userId);
        long recordId = createRecord(projectId, serverId, solutionId, "SUCCESS", null);

        jdbcTemplate.update("UPDATE server_record SET name = '已修改服务器' WHERE id = ?", serverId);
        jdbcTemplate.update("UPDATE environment_fingerprint SET operating_system = 'Windows' WHERE id = ?", fingerprintId);
        jdbcTemplate.update("UPDATE deployment_solution SET name = '已修改方案' WHERE id = ?", solutionId);

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-records/{recordId}",
                                projectId, recordId)
                        .with(recordActor("record-history", "deployment_record:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverName").value("生产应用服务器"))
                .andExpect(jsonPath("$.serverSnapshot.name").value("生产应用服务器"))
                .andExpect(jsonPath("$.environmentSnapshot.operatingSystem").value("Linux"))
                .andExpect(jsonPath("$.solutionSnapshot.name").value("生产标准方案"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-records", projectId)
                        .param("result", "SUCCESS")
                        .param("serverId", Long.toString(serverId))
                        .with(recordActor("record-history", "deployment_record:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(recordId))
                .andExpect(jsonPath("$.data[0].serverName").value("生产应用服务器"));
    }

    @Test
    void requiresFailureNotesAndOnlyAllowsSuccessfulBaselines() throws Exception {
        long userId = insertUser("record-baseline", "基线负责人");
        long projectId = insertProject(userId);
        long serverId = insertServer(projectId, userId);
        long fingerprintId = insertFingerprint(projectId, userId);
        long solutionId = insertSolution(projectId, fingerprintId,
                insertAsset(projectId, userId), userId);

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-records", projectId)
                        .with(recordActor("record-baseline", "deployment_record:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(recordRequest(serverId, solutionId, "FAILED", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_RECORD_EXCEPTION_REQUIRED"));

        long failedId = createRecord(projectId, serverId, solutionId, "FAILED", "启动超时");
        mockMvc.perform(put("/api/v1/projects/{projectId}/deployment-records/{recordId}/baseline",
                                projectId, failedId)
                        .with(recordActor("record-baseline", "deployment_record:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseline\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_BASELINE_REQUIRES_SUCCESS"));

        long successId = createRecord(projectId, serverId, solutionId, "SUCCESS", null);
        mockMvc.perform(put("/api/v1/projects/{projectId}/deployment-records/{recordId}/baseline",
                                projectId, successId)
                        .with(recordActor("record-baseline", "deployment_record:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseline\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseline").value(true));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = 'DEPLOYMENT_BASELINE_ENABLED' AND resource_id = ?
                """, Integer.class, Long.toString(successId))).isEqualTo(1);
    }

    @Test
    void ranksSameEnvironmentBaselineBeforeDifferentEnvironment() throws Exception {
        long userId = insertUser("record-similar", "检索负责人");
        long projectId = insertProject(userId);
        long serverId = insertServer(projectId, userId);
        long targetFingerprintId = insertFingerprint(projectId, userId);
        long assetId = insertAsset(projectId, userId);
        long matchingSolutionId = insertSolution(projectId, targetFingerprintId, assetId, userId);
        long matchingRecordId = createRecord(projectId, serverId, matchingSolutionId, "SUCCESS", null);
        enableBaseline(projectId, matchingRecordId, "record-similar");

        long differentFingerprintId = jdbcTemplate.queryForObject("""
                INSERT INTO environment_fingerprint (
                    project_id, name, environment, operating_system, architecture,
                    runtime_name, runtime_version, database_name, database_version,
                    middlewares, tags, created_by, updated_by
                ) VALUES (?, '测试 Windows', 'STAGING', 'Windows', 'arm64',
                          '.NET', '8', 'SQL Server', '2022', '["IIS"]', '["边缘"]', ?, ?)
                RETURNING id
                """, Long.class, projectId, userId, userId);
        long differentSolutionId = insertSolution(
                projectId, differentFingerprintId, assetId, userId);
        long differentRecordId = createRecord(
                projectId, serverId, differentSolutionId, "SUCCESS", null);
        enableBaseline(projectId, differentRecordId, "record-similar");

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-records/similar", projectId)
                        .param("fingerprintId", Long.toString(targetFingerprintId))
                        .with(recordActor("record-similar", "deployment_record:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].record.id").value(matchingRecordId))
                .andExpect(jsonPath("$[0].score").value(100))
                .andExpect(jsonPath("$[1].record.id").value(differentRecordId))
                .andExpect(jsonPath("$[1].differentFields.length()").isNotEmpty());
    }

    @Test
    void enforcesFunctionalPermissionProjectMembershipAndCsrf() throws Exception {
        long ownerId = insertUser("record-access-owner", "权限负责人");
        insertUser("record-access-outsider", "项目外用户");
        long projectId = insertProject(ownerId);
        long serverId = insertServer(projectId, ownerId);
        long fingerprintId = insertFingerprint(projectId, ownerId);
        long solutionId = insertSolution(projectId, fingerprintId,
                insertAsset(projectId, ownerId), ownerId);

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-records", projectId)
                        .with(recordActor("record-access-owner", "project:read"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(recordRequest(serverId, solutionId, "SUCCESS", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-records", projectId)
                        .with(recordActor("record-access-owner", "deployment_record:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordRequest(serverId, solutionId, "SUCCESS", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-records", projectId)
                        .with(recordActor("record-access-outsider", "deployment_record:read")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-records", projectId)
                        .with(recordActor("record-access-outsider", "deployment_record:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(recordRequest(serverId, solutionId, "SUCCESS", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROJECT_ACCESS_DENIED"));
    }

    private long insertUser(String username, String displayName) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, display_name)
                VALUES (?, '{noop}unused', ?) RETURNING id
                """, Long.class, username, displayName);
    }

    private long insertProject(long userId) {
        long projectId = jdbcTemplate.queryForObject("""
                INSERT INTO project (code, name, status, owner_id, created_by)
                VALUES ('RECORD-API', '部署记录测试项目', 'ACTIVE', ?, ?) RETURNING id
                """, Long.class, userId, userId);
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, project_role, created_by)
                VALUES (?, ?, 'OWNER', ?)
                """, projectId, userId, userId);
        return projectId;
    }

    private long insertServer(long projectId, long userId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO server_record (
                    project_id, name, host, port, environment, status,
                    operating_system, architecture, created_by, updated_by
                ) VALUES (?, '生产应用服务器', '10.0.0.9', 22, 'PRODUCTION', 'ACTIVE',
                          'Linux', 'amd64', ?, ?) RETURNING id
                """, Long.class, projectId, userId, userId);
    }

    private long insertFingerprint(long projectId, long userId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO environment_fingerprint (
                    project_id, name, environment, operating_system, architecture,
                    runtime_name, runtime_version, database_name, database_version,
                    middlewares, tags, created_by, updated_by
                ) VALUES (?, '生产 Java 基线', 'PRODUCTION', 'Linux', 'amd64',
                          'Java', '21', 'PostgreSQL', '17', '["Nginx"]', '["核心"]', ?, ?)
                RETURNING id
                """, Long.class, projectId, userId, userId);
    }

    private long insertAsset(long projectId, long userId) {
        long fileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_asset (
                    file_group_id, version, original_name, storage_key, size_bytes,
                    status, uploaded_by
                ) VALUES (gen_random_uuid(), 1, 'app.tar.gz', ?, 128, 'AVAILABLE', ?)
                RETURNING id
                """, Long.class, "record-api/" + projectId + "/app.tar.gz", userId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO deployment_asset (
                    asset_group_id, version, project_id, name, asset_type, version_label,
                    file_asset_id, environment, risk_level, tags, created_by
                ) VALUES (gen_random_uuid(), 1, ?, '生产安装包', 'INSTALLATION_PACKAGE',
                          '9.0.0', ?, 'PRODUCTION', 'MEDIUM', '[]', ?) RETURNING id
                """, Long.class, projectId, fileId, userId);
    }

    private long insertSolution(
            long projectId, long fingerprintId, long assetId, long userId) {
        long solutionId = jdbcTemplate.queryForObject("""
                INSERT INTO deployment_solution (
                    project_id, environment_fingerprint_id, name, scenario,
                    architecture_description, prerequisites, rollback_steps, risk_notes,
                    status, created_by, updated_by
                ) VALUES (?, ?, '生产标准方案', '生产发布', '单节点应用', '先备份',
                          '恢复上一版', '短时重启', 'ACTIVE', ?, ?) RETURNING id
                """, Long.class, projectId, fingerprintId, userId, userId);
        jdbcTemplate.update("""
                INSERT INTO deployment_solution_step (
                    solution_id, step_order, title, instructions, deployment_asset_id
                ) VALUES (?, 1, '发布应用', '替换并重启', ?)
                """, solutionId, assetId);
        return solutionId;
    }

    private long createRecord(
            long projectId, long serverId, long solutionId, String result,
            String exceptionNotes) throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post(
                                "/api/v1/projects/{projectId}/deployment-records", projectId)
                        .with(recordActor(currentUsername(), "deployment_record:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(recordRequest(serverId, solutionId, result, exceptionNotes)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private String currentUsername() {
        return jdbcTemplate.queryForObject("""
                SELECT username FROM app_user WHERE username LIKE 'record-%'
                ORDER BY id DESC LIMIT 1
                """, String.class);
    }

    private void enableBaseline(long projectId, long recordId, String username) throws Exception {
        mockMvc.perform(put("/api/v1/projects/{projectId}/deployment-records/{recordId}/baseline",
                                projectId, recordId)
                        .with(recordActor(username, "deployment_record:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseline\":true}"))
                .andExpect(status().isOk());
    }

    private static String recordRequest(
            long serverId, long solutionId, String result, String exceptionNotes) {
        String failureField = exceptionNotes == null ? ""
                : ",\"exceptionNotes\":\"" + exceptionNotes + "\"";
        return """
                {"serverId":%d,"solutionId":%d,
                 "executedAt":"2026-09-22T10:00:00+08:00","result":"%s"%s}
                """.formatted(serverId, solutionId, result, failureField);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor
            recordActor(String username, String permission) {
        return user(username).authorities(new SimpleGrantedAuthority(permission));
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.' || ?) IS NOT NULL", Boolean.class, tableName));
    }
}
