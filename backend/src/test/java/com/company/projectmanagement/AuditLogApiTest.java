package com.company.projectmanagement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证审计日志只读查询、组合筛选和管理员权限边界。 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuditLogApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long actorId;

    @BeforeEach
    void setUp() {
        actorId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, display_name)
                VALUES ('audit-reader', '{noop}unused', '审计管理员') RETURNING id
                """, Long.class);
        insertAudit("PROJECT_CREATED", "PROJECT", "101", "SUCCESS",
                "2026-09-20T08:00:00+08:00", "{\"projectId\":101}");
        insertAudit("DEPLOYMENT_RECORD_CREATED", "DEPLOYMENT_RECORD", "202", "SUCCESS",
                "2026-09-21T09:30:00+08:00", "{\"projectId\":101}");
        insertAudit("DEPLOYMENT_FAILED", "DEPLOYMENT_RECORD", "203", "FAILURE",
                "2026-09-22T10:00:00+08:00", "{\"reason\":\"timeout\"}");
    }

    @AfterEach
    void cleanTestData() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE actor_user_id = ?", actorId);
        jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", actorId);
    }

    @Test
    void listsNewestAuditEventsWithFiltersAndStructuredDetails() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("actorId", Long.toString(actorId))
                        .param("resourceType", "DEPLOYMENT_RECORD")
                        .param("outcome", "SUCCESS")
                        .param("createdFrom", "2026-09-21T00:00:00+08:00")
                        .param("createdTo", "2026-09-21T23:59:59+08:00")
                        .with(user("audit-reader")
                                .authorities(new SimpleGrantedAuthority("audit:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].action").value("DEPLOYMENT_RECORD_CREATED"))
                .andExpect(jsonPath("$.data[0].actor.username").value("audit-reader"))
                .andExpect(jsonPath("$.data[0].details.projectId").value(101))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("actorId", Long.toString(actorId))
                        .with(user("audit-reader")
                                .authorities(new SimpleGrantedAuthority("audit:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].resourceId").value("203"))
                .andExpect(jsonPath("$.data[2].resourceId").value("101"));
    }

    @Test
    void rejectsMissingPermissionAndInvalidTimeRange() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .with(user("audit-reader")
                                .authorities(new SimpleGrantedAuthority("project:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("createdFrom", "2026-09-23T00:00:00+08:00")
                        .param("createdTo", "2026-09-20T00:00:00+08:00")
                        .with(user("audit-reader")
                                .authorities(new SimpleGrantedAuthority("audit:read"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_AUDIT_TIME_RANGE"));
    }

    private void insertAudit(
            String action, String resourceType, String resourceId, String outcome,
            String createdAt, String details) {
        jdbcTemplate.update("""
                INSERT INTO audit_log (
                    actor_user_id, action, resource_type, resource_id, outcome,
                    ip_address, user_agent, request_id, details, created_at
                ) VALUES (?, ?, ?, ?, ?, '127.0.0.1', 'Audit API Test', ?, CAST(? AS jsonb), ?)
                """, actorId, action, resourceType, resourceId, outcome,
                "request-" + resourceId, details, OffsetDateTime.parse(createdAt));
    }
}
