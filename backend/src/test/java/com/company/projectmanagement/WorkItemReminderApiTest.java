package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
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

/** 验证个人工作项提醒的隔离、生命周期、授权与输入边界。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=reminder-admin",
        "app.bootstrap-admin.password=Test-only-reminder-password-123!",
        "app.bootstrap-admin.display-name=提醒管理员"
})
@AutoConfigureMockMvc
@Testcontainers
class WorkItemReminderApiTest {

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
    void cleanReminderTestData() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'WORK_ITEM_REMINDER'");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'reminder-admin'");
    }

    @Test
    void createsListsDismissesAndSoftDeletesOwnReminderIdempotently() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("reminder-admin");
        AppUser colleague = insertUser("reminder-colleague", "同事");
        long projectId = insertProject("REM-CRUD", administrator.getId());
        addMember(projectId, administrator.getId(), "OWNER", administrator.getId());
        addMember(projectId, colleague.getId(), "MEMBER", administrator.getId());
        long workItemId = insertWorkItem(projectId, administrator.getId(), "准备上线");
        insertReminder(projectId, workItemId, colleague.getId(), "同事的提醒");

        MvcResult created = mockMvc.perform(post(
                        "/api/v1/projects/{projectId}/work-item-reminders", projectId)
                        .with(actor("reminder-admin", "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderRequest(workItemId, OffsetDateTime.now().plusDays(1), "检查发布清单")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workItem.id").value(workItemId))
                .andExpect(jsonPath("$.message").value("检查发布清单"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long reminderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id")
                .asLong();

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-item-reminders", projectId)
                        .with(actor("reminder-admin", "work_item:read"))
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(reminderId));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(
                            "/api/v1/projects/{projectId}/work-item-reminders/{reminderId}/dismiss",
                            projectId,
                            reminderId)
                            .with(actor("reminder-admin", "work_item:write"))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISMISSED"));
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete(
                            "/api/v1/projects/{projectId}/work-item-reminders/{reminderId}",
                            projectId,
                            reminderId)
                            .with(actor("reminder-admin", "work_item:write"))
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }
        assertThat(auditCount("WORK_ITEM_REMINDER_CREATED", reminderId)).isEqualTo(1);
        assertThat(auditCount("WORK_ITEM_REMINDER_DISMISSED", reminderId)).isEqualTo(1);
        assertThat(auditCount("WORK_ITEM_REMINDER_DELETED", reminderId)).isEqualTo(1);
    }

    @Test
    void rejectsCrossProjectWorkItemAndPastReminderTime() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("reminder-admin");
        long projectId = insertProject("REM-VALID", administrator.getId());
        long otherProjectId = insertProject("REM-OTHER", administrator.getId());
        addMember(projectId, administrator.getId(), "OWNER", administrator.getId());
        addMember(otherProjectId, administrator.getId(), "OWNER", administrator.getId());
        long workItemId = insertWorkItem(projectId, administrator.getId(), "当前项目任务");
        long foreignWorkItemId = insertWorkItem(otherProjectId, administrator.getId(), "其他项目任务");

        createReminder(projectId, foreignWorkItemId, OffsetDateTime.now().plusDays(1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_NOT_FOUND"));
        createReminder(projectId, workItemId, OffsetDateTime.now().minusMinutes(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void enforcesAuthenticationFunctionalPermissionProjectVisibilityAndCsrf() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("reminder-admin");
        AppUser outsider = insertUser("reminder-outsider", "项目外用户");
        long projectId = insertProject("REM-AUTH", administrator.getId());
        addMember(projectId, administrator.getId(), "OWNER", administrator.getId());
        long workItemId = insertWorkItem(projectId, administrator.getId(), "权限任务");

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-item-reminders", projectId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/{projectId}/work-item-reminders", projectId)
                        .with(actor("reminder-admin", "project:read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/{projectId}/work-item-reminders", projectId)
                        .with(actor(outsider.getUsername(), "work_item:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/work-item-reminders", projectId)
                        .with(actor("reminder-admin", "work_item:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderRequest(
                                workItemId, OffsetDateTime.now().plusDays(1), "缺少 CSRF")))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions createReminder(
            long projectId, long workItemId, OffsetDateTime remindAt) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/{projectId}/work-item-reminders", projectId)
                .with(actor("reminder-admin", "work_item:write"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reminderRequest(workItemId, remindAt, "测试提醒")));
    }

    private String reminderRequest(long workItemId, OffsetDateTime remindAt, String message)
            throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "workItemId", workItemId,
                "remindAt", remindAt,
                "message", message));
    }

    private AppUser insertUser(String username, String displayName) {
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, display_name)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, username,
                "{bcrypt}$2a$12$test-hash-not-used-by-mock-authentication", displayName);
        AppUser user = new AppUser();
        user.setId(userId);
        user.setUsername(username);
        user.setDisplayName(displayName);
        return user;
    }

    private long insertProject(String code, long ownerId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO project (code, name, status, owner_id, created_by)
                VALUES (?, ?, 'ACTIVE', ?, ?)
                RETURNING id
                """, Long.class, code, code, ownerId, ownerId);
    }

    private void addMember(long projectId, long userId, String role, long createdBy) {
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, project_role, created_by)
                VALUES (?, ?, ?, ?)
                """, projectId, userId, role, createdBy);
    }

    private long insertWorkItem(long projectId, long createdBy, String title) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO work_item (
                    project_id, type, title, status, priority, created_by
                ) VALUES (?, 'TASK', ?, 'TODO', 'NORMAL', ?)
                RETURNING id
                """, Long.class, projectId, title, createdBy);
    }

    private void insertReminder(long projectId, long workItemId, long createdBy, String message) {
        jdbcTemplate.update("""
                INSERT INTO work_item_reminder (
                    project_id, work_item_id, remind_at, message, status, created_by
                ) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day', ?, 'PENDING', ?)
                """, projectId, workItemId, message, createdBy);
    }

    private int auditCount(String action, long reminderId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND resource_type = 'WORK_ITEM_REMINDER'
                  AND resource_id = ? AND outcome = 'SUCCESS'
                """, Integer.class, action, Long.toString(reminderId));
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
