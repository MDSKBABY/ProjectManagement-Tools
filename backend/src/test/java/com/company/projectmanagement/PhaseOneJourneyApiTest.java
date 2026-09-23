package com.company.projectmanagement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 从空库验证第一期主链路的跨模块接口契约。 */
@SpringBootTest(properties = {
        "app.file-storage.root=/tmp/project-management-phase-one-journey",
        "app.file-storage.chunk-size-bytes=5242880"
})
@AutoConfigureMockMvc
@Testcontainers
class PhaseOneJourneyApiTest {

    private static final Path STORAGE_ROOT = Path.of("/tmp/project-management-phase-one-journey");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void cleanJourney() throws IOException {
        jdbcTemplate.update("DELETE FROM deployment_record");
        jdbcTemplate.update("DELETE FROM deployment_solution_step");
        jdbcTemplate.update("DELETE FROM deployment_solution");
        jdbcTemplate.update("DELETE FROM environment_fingerprint");
        jdbcTemplate.update("DELETE FROM server_credential");
        jdbcTemplate.update("DELETE FROM server_record");
        jdbcTemplate.update("DELETE FROM deployment_asset");
        jdbcTemplate.update("DELETE FROM file_upload_chunk");
        jdbcTemplate.update("DELETE FROM file_link");
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM app_user WHERE username LIKE 'journey-%'");
        if (Files.exists(STORAGE_ROOT)) {
            try (var paths = Files.walk(STORAGE_ROOT)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
        }
    }

    @Test
    void completesProjectToSimilarDeploymentJourney() throws Exception {
        long ownerId = insertUser("journey-owner", "一期验收负责人");
        long memberId = insertUser("journey-member", "一期验收成员");

        long projectId = responseId(mockMvc.perform(post("/api/v1/projects")
                        .with(actor("journey-owner", "project:create"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PHASE-ONE-JOURNEY","name":"第一期整体验收",
                                 "status":"ACTIVE","tags":["验收"],
                                 "description":"跨模块主链路"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner.id").value(ownerId))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .with(actor("journey-owner", "project:manage_members"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d,\"role\":\"MEMBER\"}".formatted(memberId)))
                .andExpect(status().isCreated());

        byte[] fileContent = "phase-one-package".getBytes(StandardCharsets.UTF_8);
        JsonNode file = response(mockMvc.perform(post(
                                "/api/v1/projects/{projectId}/files/metadata", projectId)
                        .with(actor("journey-owner", "file:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalName":"application.jar","mediaType":"application/java-archive",
                                 "sizeBytes":%d,"sha256":"%s"}
                                """.formatted(fileContent.length, sha256(fileContent))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long fileId = file.get("id").asLong();
        mockMvc.perform(put("/api/v1/projects/{projectId}/files/{fileId}/chunks/0", projectId, fileId)
                        .with(actor("journey-owner", "file:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(fileContent))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/projects/{projectId}/files/{fileId}/complete", projectId, fileId)
                        .with(actor("journey-owner", "file:write"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        long assetId = responseId(mockMvc.perform(post(
                                "/api/v1/projects/{projectId}/deployment-assets", projectId)
                        .with(actor("journey-owner", "deployment_asset:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"核心服务安装包","assetType":"INSTALLATION_PACKAGE",
                                 "versionLabel":"1.0.0","fileAssetId":%d,"operatingSystem":"Linux",
                                 "architecture":"amd64","environment":"PRODUCTION","riskLevel":"HIGH",
                                 "tags":["核心","验收"],"description":"第一期安装包"}
                                """.formatted(fileId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        long serverId = responseId(mockMvc.perform(post("/api/v1/projects/{projectId}/servers", projectId)
                        .with(actor("journey-owner", "server:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"生产应用节点","host":"app-01.internal","port":22,
                                 "environment":"PRODUCTION","status":"ACTIVE","operatingSystem":"Linux",
                                 "architecture":"amd64","purpose":"应用服务"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        long fingerprintId = responseId(mockMvc.perform(post(
                                "/api/v1/projects/{projectId}/environment-fingerprints", projectId)
                        .with(actor("journey-owner", "environment_fingerprint:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"生产 Java 基线","environment":"PRODUCTION",
                                 "operatingSystem":"Linux","osVersion":"Rocky Linux 9.4",
                                 "architecture":"amd64","runtimeName":"Java","runtimeVersion":"21",
                                 "databaseName":"PostgreSQL","databaseVersion":"17",
                                 "middlewares":["Nginx"],"networkZone":"production-dmz","tags":["核心"]}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        long solutionId = responseId(mockMvc.perform(post(
                                "/api/v1/projects/{projectId}/deployment-solutions", projectId)
                        .with(actor("journey-owner", "deployment_solution:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"生产标准方案","scenario":"生产发布","fingerprintId":%d,
                                 "architectureDescription":"Nginx 与应用服务",
                                 "prerequisites":"备份当前版本","rollbackSteps":"恢复上一版本",
                                 "riskNotes":"可能短时重启","status":"ACTIVE",
                                 "steps":[{"stepOrder":1,"title":"发布安装包",
                                 "instructions":"校验后替换并重启","assetId":%d}]}
                                """.formatted(fingerprintId, assetId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        long recordId = responseId(mockMvc.perform(post(
                                "/api/v1/projects/{projectId}/deployment-records", projectId)
                        .with(actor("journey-owner", "deployment_record:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serverId":%d,"solutionId":%d,
                                 "executedAt":"2026-09-23T18:00:00+08:00","result":"SUCCESS",
                                 "notes":"健康检查通过"}
                                """.formatted(serverId, solutionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.solutionSnapshot.steps[0].assetVersionLabel").value("1.0.0"))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(put(
                                "/api/v1/projects/{projectId}/deployment-records/{recordId}/baseline",
                                projectId, recordId)
                        .with(actor("journey-owner", "deployment_record:write"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseline\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseline").value(true));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-records/similar", projectId)
                        .param("fingerprintId", Long.toString(fingerprintId))
                        .with(actor("journey-owner", "deployment_record:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].record.id").value(recordId))
                .andExpect(jsonPath("$[0].score").value(100));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("actorId", Long.toString(ownerId))
                        .with(actor("journey-owner", "audit:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(10));
    }

    private long insertUser(String username, String displayName) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, display_name)
                VALUES (?, '{noop}unused', ?) RETURNING id
                """, Long.class, username, displayName);
    }

    private long responseId(String body) throws Exception {
        return response(body).get("id").asLong();
    }

    private JsonNode response(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private static UserRequestPostProcessor actor(String username, String authority) {
        return user(username).authorities(new SimpleGrantedAuthority(authority));
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
