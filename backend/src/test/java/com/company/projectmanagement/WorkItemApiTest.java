package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** 验证统一工作项的基础增删改查、资源级权限与状态历史。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=work-item-admin",
        "app.bootstrap-admin.password=Test-only-work-item-password-123!",
        "app.bootstrap-admin.display-name=工作项管理员"
})
@AutoConfigureMockMvc
@Testcontainers
class WorkItemApiTest {

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
    void cleanWorkItemTestData() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'WORK_ITEM'");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'work-item-admin'");
    }

    @Test
    void createsListsAndReadsTaskWithDefaultsAndInitialHistory() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("work-item-admin");
        long projectId = insertProject("WI-BASE", administrator.getId());
        addMember(projectId, administrator.getId(), "OWNER", administrator.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                        .with(actor("work-item-admin", "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TASK",
                                  "title": "  准备现场部署  ",
                                  "description": "  核对服务器与安装包  ",
                                  "assigneeId": %d,
                                  "plannedStartDate": "2026-10-01",
                                  "plannedEndDate": "2026-10-03"
                                }
                                """.formatted(administrator.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(
                        "/api/v1/projects/" + projectId + "/work-items/")))
                .andExpect(jsonPath("$.type").value("TASK"))
                .andExpect(jsonPath("$.title").value("准备现场部署"))
                .andExpect(jsonPath("$.description").value("核对服务器与安装包"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.assignee.id").value(administrator.getId()))
                .andReturn();

        long workItemId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id")
                .asLong();

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-items", projectId)
                        .with(actor("work-item-admin", "work_item:read"))
                        .param("keyword", "现场")
                        .param("type", "TASK")
                        .param("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(workItemId))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-items/{id}", projectId, workItemId)
                        .with(actor("work-item-admin", "work_item:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workItemId));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM work_item_status_log
                WHERE work_item_id = ? AND from_status IS NULL AND to_status = 'TODO'
                """, Integer.class, workItemId)).isEqualTo(1);
        assertThat(successfulAuditCount("WORK_ITEM_CREATED", workItemId)).isEqualTo(1);
    }

    @Test
    void partiallyUpdatesTransitionsStatusAndSoftDeletesIdempotently() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("work-item-admin");
        long projectId = insertProject("WI-LIFECYCLE", administrator.getId());
        addMember(projectId, administrator.getId(), "OWNER", administrator.getId());
        long workItemId = createWorkItem(projectId, administrator.getId(), "MILESTONE", "上线节点");

        mockMvc.perform(patch("/api/v1/projects/{projectId}/work-items/{id}", projectId, workItemId)
                        .with(actor("work-item-admin", "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  生产上线节点  ",
                                  "priority": "HIGH",
                                  "description": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("生产上线节点"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.description").isEmpty());

        transition(projectId, workItemId, "IN_PROGRESS", "已开始准备")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.actualStartDate").isNotEmpty());
        transition(projectId, workItemId, "DONE", "验收通过")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.actualEndDate").isNotEmpty());

        transition(projectId, workItemId, "TODO", "非法跳转")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_STATUS_TRANSITION_INVALID"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-items/{id}/status-history",
                        projectId, workItemId)
                        .with(actor("work-item-admin", "work_item:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].toStatus").value("TODO"))
                .andExpect(jsonPath("$[2].toStatus").value("DONE"));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/projects/{projectId}/work-items/{id}",
                            projectId, workItemId)
                            .with(actor("work-item-admin", "work_item:delete"))
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-items/{id}", projectId, workItemId)
                        .with(actor("work-item-admin", "work_item:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_NOT_FOUND"));
        assertThat(successfulAuditCount("WORK_ITEM_DELETED", workItemId)).isEqualTo(1);
    }

    @Test
    void enforcesProjectRolesAssigneeMembershipDatesAndCsrf() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("work-item-admin");
        AppUser member = insertUser("work-item-member", "工作项成员");
        AppUser otherMember = insertUser("work-item-other", "其他项目成员");
        AppUser viewer = insertUser("work-item-viewer", "项目访客");
        AppUser outsider = insertUser("work-item-outsider", "项目外用户");
        long projectId = insertProject("WI-AUTH", administrator.getId());
        addMember(projectId, administrator.getId(), "OWNER", administrator.getId());
        addMember(projectId, member.getId(), "MEMBER", administrator.getId());
        addMember(projectId, otherMember.getId(), "MEMBER", administrator.getId());
        addMember(projectId, viewer.getId(), "VIEWER", administrator.getId());

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-items", projectId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/{projectId}/work-items", projectId)
                        .with(actor("work-item-admin", "project:read")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-items", projectId)
                        .with(actor(outsider.getUsername(), "work_item:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                        .with(actor(member.getUsername(), "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest(member.getId(), "TASK", "越权创建")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                        .with(actor("work-item-admin", "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest(outsider.getId(), "TASK", "非成员负责")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_ASSIGNEE_INVALID"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                        .with(actor("work-item-admin", "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest(viewer.getId(), "TASK", "访客不能承办")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_ASSIGNEE_INVALID"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                        .with(actor("work-item-admin", "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TASK",
                                  "title": "日期非法",
                                  "plannedStartDate": "2026-10-10",
                                  "plannedEndDate": "2026-10-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_WORK_ITEM_DATE_RANGE"));

        long assignedItemId = createWorkItem(projectId, member.getId(), "TASK", "成员任务");
        mockMvc.perform(patch("/api/v1/projects/{projectId}/work-items/{id}",
                        projectId, assignedItemId)
                        .with(actor(member.getUsername(), "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"成员已更新\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("成员已更新"));
        mockMvc.perform(post(
                        "/api/v1/projects/{projectId}/work-items/{id}/status-transitions",
                        projectId,
                        assignedItemId)
                        .with(actor(member.getUsername(), "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(patch("/api/v1/projects/{projectId}/work-items/{id}",
                        projectId, assignedItemId)
                        .with(actor(otherMember.getUsername(), "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"不应更新\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_ACCESS_DENIED"));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/work-items/{id}",
                        projectId, assignedItemId)
                        .with(actor(member.getUsername(), "work_item:delete"))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                        .with(actor("work-item-admin", "work_item:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest(member.getId(), "TASK", "缺少 CSRF")))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions transition(
            long projectId, long workItemId, String status, String comment) throws Exception {
        return mockMvc.perform(post(
                        "/api/v1/projects/{projectId}/work-items/{id}/status-transitions",
                        projectId,
                        workItemId)
                .with(actor("work-item-admin", "work_item:write"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s", "comment": "%s"}
                        """.formatted(status, comment)));
    }

    private long createWorkItem(long projectId, long assigneeId, String type, String title)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/work-items", projectId)
                        .with(actor("work-item-admin", "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest(assigneeId, type, title)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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

    private int successfulAuditCount(String action, long workItemId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND resource_type = 'WORK_ITEM'
                  AND resource_id = ? AND outcome = 'SUCCESS'
                """, Integer.class, action, Long.toString(workItemId));
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String validCreateRequest(long assigneeId, String type, String title) {
        return """
                {
                  "type": "%s",
                  "title": "%s",
                  "description": "测试工作项",
                  "priority": "NORMAL",
                  "assigneeId": %d,
                  "plannedStartDate": "2026-10-01",
                  "plannedEndDate": "2026-10-03"
                }
                """.formatted(type, title, assigneeId);
    }
}
