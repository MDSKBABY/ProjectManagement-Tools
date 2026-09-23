package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
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
import com.company.projectmanagement.identity.mapper.AppUserMapper;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 独立验证项目管理 API 的契约、项目级授权、软删除和审计闭环。
 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=project-api-admin",
        "app.bootstrap-admin.password=Test-only-project-api-password-123!",
        "app.bootstrap-admin.display-name=项目接口管理员"
})
@AutoConfigureMockMvc
@Testcontainers
class ProjectAdministrationApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserMapper appUserMapper;

    @AfterEach
    void cleanProjectTestData() {
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'project-api-admin'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'PROJECT'");
    }

    @Test
    void enforcesAuthenticationFunctionalPermissionsAndCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/v1/projects")
                        .with(actor("project-api-admin", "user:manage")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/projects")
                        .with(actor("project-api-admin", "project:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest("NO-CSRF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void createsProjectWithOwnerMembershipAndAuditRecord() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("project-api-admin");

        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .with(actor("project-api-admin", "project:create"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "  PM-001  ",
                                  "name": "  内部项目管理平台  ",
                                  "customerName": "  示例客户  ",
                                  "status": "PLANNING",
                                  "startDate": "2026-09-20",
                                  "endDate": "2026-12-31",
                                  "tags": [" 平台 ", "重点", "平台"],
                                  "description": "  建设内部项目管理能力  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/projects/")))
                .andExpect(jsonPath("$.code").value("PM-001"))
                .andExpect(jsonPath("$.name").value("内部项目管理平台"))
                .andExpect(jsonPath("$.customerName").value("示例客户"))
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.owner.id").value(administrator.getId()))
                .andExpect(jsonPath("$.owner.displayName").value("项目接口管理员"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0]").value("平台"))
                .andExpect(jsonPath("$.tags[1]").value("重点"))
                .andReturn();

        long projectId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id")
                .asLong();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM project_member
                WHERE project_id = ? AND user_id = ? AND project_role = 'OWNER'
                """, Integer.class, projectId, administrator.getId())).isEqualTo(1);
        assertThat(successfulAuditCount("PROJECT_CREATED", projectId)).isEqualTo(1);
    }

    @Test
    void listsOnlyParticipatingProjectsForOrdinaryUsersButAllProjectsForAdministrators()
            throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("project-api-admin");
        AppUser member = insertUser("project-member", "项目成员");
        AppUser outsider = insertUser("project-outsider", "项目外用户");
        long joinedProjectId = insertProject("JOINED-001", "参与的项目", administrator.getId());
        insertProject("HIDDEN-001", "不可见的项目", administrator.getId());
        addMember(joinedProjectId, member.getId(), "MEMBER", administrator.getId());

        mockMvc.perform(get("/api/v1/projects")
                        .with(actor("project-member", "project:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(joinedProjectId))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        mockMvc.perform(get("/api/v1/projects/{id}", joinedProjectId)
                        .with(actor("project-outsider", "project:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/projects")
                        .with(actor("project-api-admin", "ROLE_ADMIN", "project:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.totalItems").value(2));

        assertThat(outsider).isNotNull();
    }

    @Test
    void updatesProjectOnlyForOwnerOrProjectManagerAndRecordsAudit() throws Exception {
        AppUser owner = insertUser("project-owner", "项目负责人");
        AppUser manager = insertUser("project-manager", "项目管理员");
        AppUser outsider = insertUser("update-outsider", "无权修改用户");
        long projectId = insertProject("UPDATE-001", "待修改项目", owner.getId());
        addMember(projectId, owner.getId(), "OWNER", owner.getId());
        addMember(projectId, manager.getId(), "MANAGER", owner.getId());

        mockMvc.perform(put("/api/v1/projects/{id}", projectId)
                        .with(actor(outsider.getUsername(), "project:update"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest("越权修改", "ACTIVE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROJECT_ACCESS_DENIED"));

        mockMvc.perform(put("/api/v1/projects/{id}", projectId)
                        .with(actor(manager.getUsername(), "project:update"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest("已归档项目", "ARCHIVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("已归档项目"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.customerName").value("更新后的客户"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM project WHERE id = ?", String.class, projectId))
                .isEqualTo("ARCHIVED");
        assertThat(successfulAuditCount("PROJECT_UPDATED", projectId)).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateCodesInvalidDatesAndInvalidTags() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("project-api-admin");
        insertProject("DUPLICATE-001", "已有项目", administrator.getId());

        mockMvc.perform(post("/api/v1/projects")
                        .with(actor("project-api-admin", "project:create"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest("duplicate-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROJECT_CODE_EXISTS"));

        mockMvc.perform(post("/api/v1/projects")
                        .with(actor("project-api-admin", "project:create"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "INVALID-DATE",
                                  "name": "日期非法项目",
                                  "startDate": "2026-10-01",
                                  "endDate": "2026-09-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PROJECT_DATE_RANGE"));

        mockMvc.perform(post("/api/v1/projects")
                        .with(actor("project-api-admin", "project:create"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "INVALID-TAG",
                                  "name": "标签非法项目",
                                  "tags": ["有效标签", ""]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void softDeletesProjectIdempotentlyAndHidesItFromQueries() throws Exception {
        AppUser owner = insertUser("delete-owner", "删除负责人");
        long projectId = insertProject("DELETE-001", "待删除项目", owner.getId());
        addMember(projectId, owner.getId(), "OWNER", owner.getId());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/projects/{id}", projectId)
                            .with(actor(owner.getUsername(), "project:delete"))
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                        .with(actor(owner.getUsername(), "project:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/projects/{id}", projectId)
                        .with(actor(owner.getUsername(), "project:update"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest("不应被更新", "ACTIVE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM project WHERE id = ?",
                Boolean.class,
                projectId)).isTrue();
        assertThat(successfulAuditCount("PROJECT_DELETED", projectId)).isEqualTo(1);
    }

    private AppUser insertUser(String username, String displayName) {
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, display_name)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                username,
                "{bcrypt}$2a$12$test-hash-not-used-by-mock-authentication",
                displayName);
        AppUser userRecord = new AppUser();
        userRecord.setId(userId);
        userRecord.setUsername(username);
        userRecord.setDisplayName(displayName);
        return userRecord;
    }

    private long insertProject(String code, String name, long ownerId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO project (code, name, status, owner_id, created_by)
                VALUES (?, ?, 'PLANNING', ?, ?)
                RETURNING id
                """, Long.class, code, name, ownerId, ownerId);
    }

    private void addMember(long projectId, long userId, String role, long createdBy) {
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, project_role, created_by)
                VALUES (?, ?, ?, ?)
                """, projectId, userId, role, createdBy);
    }

    private int successfulAuditCount(String action, long projectId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ?
                  AND resource_type = 'PROJECT'
                  AND resource_id = ?
                  AND outcome = 'SUCCESS'
                """, Integer.class, action, Long.toString(projectId));
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String validCreateRequest(String code) {
        return """
                {
                  "code": "%s",
                  "name": "测试项目",
                  "customerName": "测试客户",
                  "status": "PLANNING",
                  "startDate": "2026-09-20",
                  "endDate": "2026-12-31",
                  "tags": ["测试"],
                  "description": "测试项目说明"
                }
                """.formatted(code);
    }

    private static String validUpdateRequest(String name, String status) {
        return """
                {
                  "name": "%s",
                  "customerName": "更新后的客户",
                  "status": "%s",
                  "startDate": "2026-09-20",
                  "endDate": "2027-01-31",
                  "tags": ["更新", "归档"],
                  "description": "更新后的说明"
                }
                """.formatted(name, status);
    }
}
