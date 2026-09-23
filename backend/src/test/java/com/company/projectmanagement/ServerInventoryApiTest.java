package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.projectmanagement.identity.domain.AppUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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

/** 验证服务器档案、凭据密文、资源级授权和敏感操作审计。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=server-api-admin",
        "app.bootstrap-admin.password=Test-only-server-api-password-123!",
        "app.bootstrap-admin.display-name=服务器测试管理员",
        "app.server-credential.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.server-credential.key-version=test-v1"
})
@AutoConfigureMockMvc
@Testcontainers
class ServerInventoryApiTest {

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
        if (tableExists("server_record")) {
            jdbcTemplate.update("DELETE FROM server_record");
        }
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'server-api-admin'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type IN ('SERVER', 'SERVER_CREDENTIAL')");
    }

    @Test
    void createsListsUpdatesAndSoftDeletesServerRecords() throws Exception {
        AppUser owner = insertUser("server-owner", "服务器负责人");
        long projectId = insertProjectWithMember("SERVER-CRUD", owner, "OWNER");

        JsonNode created = createServer(projectId, owner, "生产应用节点", "app-01.internal", 22);
        long serverId = created.get("id").asLong();
        assertThat(created.get("credentialConfigured").asBoolean()).isFalse();

        mockMvc.perform(get("/api/v1/projects/{projectId}/servers", projectId)
                        .param("environment", "PRODUCTION")
                        .param("keyword", "app-01")
                        .with(actor(owner.getUsername(), "server:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].host").value("app-01.internal"))
                .andExpect(jsonPath("$.data[0].credentialConfigured").value(false))
                .andExpect(jsonPath("$.data[0].credential").doesNotExist());

        mockMvc.perform(put("/api/v1/projects/{projectId}/servers/{serverId}", projectId, serverId)
                        .with(actor(owner.getUsername(), "server:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serverRequest("生产应用节点 A", "app-01.internal", 2222)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("生产应用节点 A"))
                .andExpect(jsonPath("$.port").value(2222));

        saveCredential(projectId, serverId, owner, "deploy", "delete-with-server-password");

        mockMvc.perform(delete("/api/v1/projects/{projectId}/servers/{serverId}", projectId, serverId)
                        .with(actor(owner.getUsername(), "server:write"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM server_credential WHERE server_id = ?",
                Integer.class,
                serverId)).isZero();

        mockMvc.perform(get("/api/v1/projects/{projectId}/servers/{serverId}", projectId, serverId)
                        .with(actor(owner.getUsername(), "server:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SERVER_NOT_FOUND"));

        assertThat(auditCount("SERVER_CREATED", serverId)).isEqualTo(1);
        assertThat(auditCount("SERVER_UPDATED", serverId)).isEqualTo(1);
        assertThat(auditCount("SERVER_DELETED", serverId)).isEqualTo(1);
    }

    @Test
    void storesCredentialAsAuthenticatedCiphertextAndNeverLeaksItInServerResponses() throws Exception {
        AppUser owner = insertUser("credential-owner", "凭据负责人");
        long projectId = insertProjectWithMember("SERVER-CREDENTIAL", owner, "OWNER");
        long serverId = createServer(projectId, owner, "数据库节点", "db-01.internal", 5432)
                .get("id").asLong();

        mockMvc.perform(put("/api/v1/projects/{projectId}/servers/{serverId}/credential", projectId, serverId)
                        .with(actor(owner.getUsername(), "server_credential:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"deploy_user","password":"test-only-secret-value"}
                                """))
                .andExpect(status().isNoContent());

        byte[] ciphertext = jdbcTemplate.queryForObject(
                "SELECT ciphertext FROM server_credential WHERE server_id = ?",
                byte[].class,
                serverId);
        byte[] nonce = jdbcTemplate.queryForObject(
                "SELECT nonce FROM server_credential WHERE server_id = ?",
                byte[].class,
                serverId);
        assertThat(new String(ciphertext, StandardCharsets.UTF_8))
                .doesNotContain("deploy_user")
                .doesNotContain("test-only-secret-value");
        assertThat(nonce).hasSize(12);

        mockMvc.perform(get("/api/v1/projects/{projectId}/servers/{serverId}", projectId, serverId)
                        .with(actor(owner.getUsername(), "server:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.ciphertext").doesNotExist())
                .andExpect(jsonPath("$.nonce").doesNotExist());

        mockMvc.perform(get("/api/v1/projects/{projectId}/servers/{serverId}/credential", projectId, serverId)
                        .with(actor(owner.getUsername(), "server_credential:read")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.username").value("deploy_user"))
                .andExpect(jsonPath("$.password").value("test-only-secret-value"))
                .andExpect(jsonPath("$.ciphertext").doesNotExist());

        assertThat(auditCount("SERVER_CREDENTIAL_CHANGED", serverId)).isEqualTo(1);
        assertThat(auditCount("SERVER_CREDENTIAL_VIEWED", serverId)).isEqualTo(1);
    }

    @Test
    void onlyProjectOwnerOrAdministratorCanChangeAndViewCredentials() throws Exception {
        AppUser owner = insertUser("credential-auth-owner", "凭据授权负责人");
        AppUser member = insertUser("credential-member", "普通项目成员");
        long projectId = insertProjectWithMember("SERVER-AUTH", owner, "OWNER");
        addProjectMember(projectId, member, "MEMBER", owner.getId());
        long serverId = createServer(projectId, owner, "授权测试节点", "auth-01.internal", 22)
                .get("id").asLong();
        saveCredential(projectId, serverId, owner, "root", "owner-only-password");

        mockMvc.perform(get("/api/v1/projects/{projectId}/servers/{serverId}/credential", projectId, serverId)
                        .with(actor(member.getUsername(), "server_credential:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SERVER_CREDENTIAL_ACCESS_DENIED"));

        mockMvc.perform(put("/api/v1/projects/{projectId}/servers/{serverId}/credential", projectId, serverId)
                        .with(actor(member.getUsername(), "server_credential:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"password\":\"must-not-save\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SERVER_CREDENTIAL_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/servers/{serverId}/credential", projectId, serverId)
                        .with(actor("server-api-admin", "server_credential:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("root"));

        assertThat(auditCount("SERVER_CREDENTIAL_VIEWED", serverId)).isEqualTo(1);
    }

    @Test
    void hidesProjectServersFromOutsidersAndRequiresFunctionalPermissionsAndCsrf() throws Exception {
        AppUser owner = insertUser("server-visible-owner", "服务器可见负责人");
        AppUser outsider = insertUser("server-outsider", "服务器外部用户");
        long projectId = insertProjectWithMember("SERVER-VISIBLE", owner, "OWNER");
        long serverId = createServer(projectId, owner, "隐藏节点", "hidden.internal", 22)
                .get("id").asLong();

        mockMvc.perform(get("/api/v1/projects/{projectId}/servers", projectId)
                        .with(actor(owner.getUsername(), "project:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/servers", projectId)
                        .with(actor(owner.getUsername(), "server:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serverRequest("缺少 CSRF", "csrf.internal", 22)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects/{projectId}/servers/{serverId}", projectId, serverId)
                        .with(actor(outsider.getUsername(), "server:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void validatesHostPortAndCredentialInput() throws Exception {
        AppUser owner = insertUser("server-validation-owner", "服务器校验负责人");
        long projectId = insertProjectWithMember("SERVER-VALIDATION", owner, "OWNER");

        mockMvc.perform(post("/api/v1/projects/{projectId}/servers", projectId)
                        .with(actor(owner.getUsername(), "server:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serverRequest("非法节点", "bad host\nvalue", 70000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        long serverId = createServer(projectId, owner, "校验节点", "valid.internal", 22)
                .get("id").asLong();
        mockMvc.perform(put("/api/v1/projects/{projectId}/servers/{serverId}/credential", projectId, serverId)
                        .with(actor(owner.getUsername(), "server_credential:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private JsonNode createServer(
            long projectId, AppUser actor, String name, String host, int port) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/servers", projectId)
                        .with(actor(actor.getUsername(), "server:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serverRequest(name, host, port)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void saveCredential(
            long projectId, long serverId, AppUser actor, String username, String password)
            throws Exception {
        mockMvc.perform(put("/api/v1/projects/{projectId}/servers/{serverId}/credential", projectId, serverId)
                        .with(actor(actor.getUsername(), "server_credential:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CredentialInput(username, password))))
                .andExpect(status().isNoContent());
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

    private long insertProjectWithMember(String code, AppUser user, String role) {
        long projectId = jdbcTemplate.queryForObject("""
                INSERT INTO project (code, name, status, owner_id, created_by)
                VALUES (?, ?, 'PLANNING', ?, ?) RETURNING id
                """, Long.class, code, code, user.getId(), user.getId());
        addProjectMember(projectId, user, role, user.getId());
        return projectId;
    }

    private void addProjectMember(long projectId, AppUser user, String role, long createdBy) {
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, project_role, created_by)
                VALUES (?, ?, ?, ?)
                """, projectId, user.getId(), role, createdBy);
    }

    private int auditCount(String action, long serverId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND resource_id = ? AND outcome = 'SUCCESS'
                """, Integer.class, action, Long.toString(serverId));
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

    private static String serverRequest(String name, String host, int port) {
        return """
                {
                  "name":"%s",
                  "host":"%s",
                  "port":%d,
                  "environment":"PRODUCTION",
                  "status":"ACTIVE",
                  "operatingSystem":"Linux",
                  "architecture":"amd64",
                  "purpose":"应用服务",
                  "description":"第一期服务器档案"
                }
                """.formatted(name, host, port);
    }

    private record CredentialInput(String username, String password) { }
}
