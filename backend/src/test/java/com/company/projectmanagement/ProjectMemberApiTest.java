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
import com.company.projectmanagement.identity.mapper.AppUserMapper;
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

/** 验证项目成员接口的权限边界、OWNER 保护、幂等移除和审计。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=member-api-admin",
        "app.bootstrap-admin.password=Test-only-member-api-password-123!",
        "app.bootstrap-admin.display-name=成员接口管理员"
})
@AutoConfigureMockMvc
@Testcontainers
class ProjectMemberApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserMapper appUserMapper;

    @AfterEach
    void cleanTestData() {
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'member-api-admin'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'PROJECT'");
    }

    @Test
    void requiresManageMembersPermissionAndCsrfForWrites() throws Exception {
        AppUser owner = insertUser("permission-owner", "权限负责人", "ACTIVE");
        AppUser target = insertUser("permission-target", "待加入成员", "ACTIVE");
        long projectId = insertProjectWithOwner("MEMBER-PERMISSION", owner);

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .with(actor(owner.getUsername(), "project:update"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addRequest(target.getId(), "MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .with(actor(owner.getUsername(), "project:manage_members"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addRequest(target.getId(), "MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void listsMembersOnlyForProjectParticipantsOrAdministrators() throws Exception {
        AppUser owner = insertUser("list-owner", "列表负责人", "ACTIVE");
        AppUser member = insertUser("list-member", "项目成员", "ACTIVE");
        AppUser outsider = insertUser("list-outsider", "外部用户", "ACTIVE");
        long projectId = insertProjectWithOwner("MEMBER-LIST", owner);
        addMember(projectId, member.getId(), "VIEWER", owner.getId());

        mockMvc.perform(get("/api/v1/projects/{projectId}/members", projectId)
                        .param("keyword", "项目")
                        .with(actor(member.getUsername(), "project:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(member.getId()))
                .andExpect(jsonPath("$.data[0].username").value("list-member"))
                .andExpect(jsonPath("$.data[0].role").value("VIEWER"))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        mockMvc.perform(get("/api/v1/projects/{projectId}/members", projectId)
                        .with(actor(outsider.getUsername(), "project:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void addsOnlyActiveNonOwnerMembersAndRecordsAudit() throws Exception {
        AppUser owner = insertUser("add-owner", "添加负责人", "ACTIVE");
        AppUser manager = insertUser("add-manager", "成员管理员", "ACTIVE");
        AppUser activeTarget = insertUser("active-target", "活动成员", "ACTIVE");
        AppUser disabledTarget = insertUser("disabled-target", "停用成员", "DISABLED");
        long projectId = insertProjectWithOwner("MEMBER-ADD", owner);
        addMember(projectId, manager.getId(), "MANAGER", owner.getId());

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .with(actor(manager.getUsername(), "project:manage_members"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addRequest(activeTarget.getId(), "MEMBER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(activeTarget.getId()))
                .andExpect(jsonPath("$.displayName").value("活动成员"))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .with(actor(manager.getUsername(), "project:manage_members"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addRequest(activeTarget.getId(), "MEMBER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROJECT_MEMBER_EXISTS"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .with(actor(manager.getUsername(), "project:manage_members"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addRequest(disabledTarget.getId(), "VIEWER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROJECT_MEMBER_USER_UNAVAILABLE"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .with(actor(manager.getUsername(), "project:manage_members"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addRequest(disabledTarget.getId(), "OWNER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROJECT_OWNER_ROLE_RESERVED"));

        assertThat(auditCount("PROJECT_MEMBER_ADDED", projectId)).isEqualTo(1);
    }

    @Test
    void searchesOnlyActiveNonMembersAsCandidatesForProjectManagers() throws Exception {
        AppUser owner = insertUser("candidate-owner", "候选负责人", "ACTIVE");
        AppUser manager = insertUser("candidate-manager", "候选管理员", "ACTIVE");
        AppUser candidate = insertUser("candidate-user", "候选成员", "ACTIVE");
        insertUser("candidate-disabled", "停用候选", "DISABLED");
        long projectId = insertProjectWithOwner("MEMBER-CANDIDATE", owner);
        addMember(projectId, manager.getId(), "MANAGER", owner.getId());

        mockMvc.perform(get("/api/v1/projects/{projectId}/members/candidates", projectId)
                        .param("keyword", "候选")
                        .with(actor(manager.getUsername(), "project:manage_members")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(candidate.getId()))
                .andExpect(jsonPath("$.data[0].username").value("candidate-user"))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        mockMvc.perform(get("/api/v1/projects/{projectId}/members/candidates", projectId)
                        .with(actor(owner.getUsername(), "project:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void changesOrdinaryMemberRoleButProtectsOwner() throws Exception {
        AppUser owner = insertUser("role-owner", "角色负责人", "ACTIVE");
        AppUser member = insertUser("role-member", "待调整成员", "ACTIVE");
        long projectId = insertProjectWithOwner("MEMBER-ROLE", owner);
        addMember(projectId, member.getId(), "MEMBER", owner.getId());

        mockMvc.perform(put("/api/v1/projects/{projectId}/members/{userId}", projectId, member.getId())
                        .with(actor(owner.getUsername(), "project:manage_members"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));

        mockMvc.perform(put("/api/v1/projects/{projectId}/members/{userId}", projectId, owner.getId())
                        .with(actor(owner.getUsername(), "project:manage_members"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROJECT_OWNER_PROTECTED"));

        assertThat(auditCount("PROJECT_MEMBER_ROLE_CHANGED", projectId)).isEqualTo(1);
    }

    @Test
    void removesOrdinaryMemberIdempotentlyButNeverRemovesOwner() throws Exception {
        AppUser owner = insertUser("remove-owner", "移除负责人", "ACTIVE");
        AppUser member = insertUser("remove-member", "待移除成员", "ACTIVE");
        long projectId = insertProjectWithOwner("MEMBER-REMOVE", owner);
        addMember(projectId, member.getId(), "MEMBER", owner.getId());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/projects/{projectId}/members/{userId}", projectId, member.getId())
                            .with(actor(owner.getUsername(), "project:manage_members"))
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(delete("/api/v1/projects/{projectId}/members/{userId}", projectId, owner.getId())
                        .with(actor("member-api-admin", "ROLE_ADMIN", "project:manage_members"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROJECT_OWNER_PROTECTED"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM project_member WHERE project_id = ? AND user_id = ?
                """, Integer.class, projectId, member.getId())).isZero();
        assertThat(auditCount("PROJECT_MEMBER_REMOVED", projectId)).isEqualTo(1);
    }

    private AppUser insertUser(String username, String displayName, String status) {
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, display_name, status)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                username,
                "{bcrypt}$2a$12$test-hash-not-used-by-mock-authentication",
                displayName,
                status);
        AppUser userRecord = new AppUser();
        userRecord.setId(userId);
        userRecord.setUsername(username);
        userRecord.setDisplayName(displayName);
        return userRecord;
    }

    private long insertProjectWithOwner(String code, AppUser owner) {
        long projectId = jdbcTemplate.queryForObject("""
                INSERT INTO project (code, name, status, owner_id, created_by)
                VALUES (?, ?, 'PLANNING', ?, ?)
                RETURNING id
                """, Long.class, code, code, owner.getId(), owner.getId());
        addMember(projectId, owner.getId(), "OWNER", owner.getId());
        return projectId;
    }

    private void addMember(long projectId, long userId, String role, long createdBy) {
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, project_role, created_by)
                VALUES (?, ?, ?, ?)
                """, projectId, userId, role, createdBy);
    }

    private int auditCount(String action, long projectId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND resource_type = 'PROJECT'
                  AND resource_id = ? AND outcome = 'SUCCESS'
                """, Integer.class, action, Long.toString(projectId));
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String addRequest(long userId, String role) {
        return "{\"userId\":%d,\"role\":\"%s\"}".formatted(userId, role);
    }
}
