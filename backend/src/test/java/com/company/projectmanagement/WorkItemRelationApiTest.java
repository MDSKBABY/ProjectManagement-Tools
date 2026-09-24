package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** 验证工作项父子、前置和阻塞关系的契约、权限与循环保护。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=relation-admin",
        "app.bootstrap-admin.password=Test-only-relation-password-123!",
        "app.bootstrap-admin.display-name=关系管理员"
})
@AutoConfigureMockMvc
@Testcontainers
class WorkItemRelationApiTest {

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
    void cleanRelationTestData() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'WORK_ITEM_RELATION'");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'relation-admin'");
    }

    @Test
    void createsFiltersAndSoftDeletesParentChildRelationIdempotently() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("relation-admin");
        long projectId = insertProject("REL-CRUD", administrator.getId());
        addMember(projectId, administrator.getId(), "OWNER", administrator.getId());
        long parentId = insertWorkItem(projectId, administrator.getId(), "父任务");
        long childId = insertWorkItem(projectId, administrator.getId(), "子任务");

        MvcResult created = createRelation(projectId, parentId, childId, "PARENT_CHILD")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(
                        "/api/v1/projects/" + projectId + "/work-item-relations/")))
                .andExpect(jsonPath("$.type").value("PARENT_CHILD"))
                .andExpect(jsonPath("$.source.id").value(parentId))
                .andExpect(jsonPath("$.source.title").value("父任务"))
                .andExpect(jsonPath("$.target.id").value(childId))
                .andReturn();
        long relationId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id")
                .asLong();

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-item-relations", projectId)
                        .with(actor("relation-admin", "work_item:read"))
                        .param("workItemId", Long.toString(childId))
                        .param("type", "PARENT_CHILD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(relationId))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete(
                            "/api/v1/projects/{projectId}/work-item-relations/{relationId}",
                            projectId,
                            relationId)
                            .with(actor("relation-admin", "work_item:write"))
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-item-relations", projectId)
                        .with(actor("relation-admin", "work_item:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        assertThat(successfulAuditCount("WORK_ITEM_RELATION_CREATED", relationId)).isEqualTo(1);
        assertThat(successfulAuditCount("WORK_ITEM_RELATION_DELETED", relationId)).isEqualTo(1);
    }

    @Test
    void rejectsSelfCrossProjectAndDuplicateDependencyRelations() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("relation-admin");
        long projectId = insertProject("REL-VALID", administrator.getId());
        long otherProjectId = insertProject("REL-OTHER", administrator.getId());
        addMember(projectId, administrator.getId(), "OWNER", administrator.getId());
        addMember(otherProjectId, administrator.getId(), "OWNER", administrator.getId());
        long firstId = insertWorkItem(projectId, administrator.getId(), "工作项 A");
        long secondId = insertWorkItem(projectId, administrator.getId(), "工作项 B");
        long foreignId = insertWorkItem(otherProjectId, administrator.getId(), "其他项目工作项");

        createRelation(projectId, firstId, firstId, "PARENT_CHILD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_RELATION_SELF_NOT_ALLOWED"));
        createRelation(projectId, firstId, foreignId, "PRECEDES")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_NOT_FOUND"));

        createRelation(projectId, firstId, secondId, "PRECEDES")
                .andExpect(status().isCreated());
        createRelation(projectId, firstId, secondId, "BLOCKS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_RELATION_EXISTS"));
    }

    @Test
    void rejectsHierarchyAndCombinedDependencyCycles() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("relation-admin");
        long projectId = insertProject("REL-CYCLE", administrator.getId());
        addMember(projectId, administrator.getId(), "OWNER", administrator.getId());
        long firstId = insertWorkItem(projectId, administrator.getId(), "工作项 A");
        long secondId = insertWorkItem(projectId, administrator.getId(), "工作项 B");
        long thirdId = insertWorkItem(projectId, administrator.getId(), "工作项 C");

        createRelation(projectId, firstId, secondId, "PARENT_CHILD")
                .andExpect(status().isCreated());
        createRelation(projectId, secondId, thirdId, "PARENT_CHILD")
                .andExpect(status().isCreated());
        createRelation(projectId, thirdId, firstId, "PARENT_CHILD")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_RELATION_CYCLE"));

        createRelation(projectId, firstId, secondId, "PRECEDES")
                .andExpect(status().isCreated());
        createRelation(projectId, secondId, thirdId, "BLOCKS")
                .andExpect(status().isCreated());
        createRelation(projectId, thirdId, firstId, "PRECEDES")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_RELATION_CYCLE"));
    }

    @Test
    void enforcesFunctionalProjectRoleAndCsrfBoundaries() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("relation-admin");
        AppUser member = insertUser("relation-member", "项目成员");
        AppUser outsider = insertUser("relation-outsider", "项目外用户");
        long projectId = insertProject("REL-AUTH", administrator.getId());
        addMember(projectId, administrator.getId(), "OWNER", administrator.getId());
        addMember(projectId, member.getId(), "MEMBER", administrator.getId());
        long firstId = insertWorkItem(projectId, administrator.getId(), "工作项 A");
        long secondId = insertWorkItem(projectId, administrator.getId(), "工作项 B");

        mockMvc.perform(get("/api/v1/projects/{projectId}/work-item-relations", projectId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/{projectId}/work-item-relations", projectId)
                        .with(actor("relation-admin", "project:read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/{projectId}/work-item-relations", projectId)
                        .with(actor(outsider.getUsername(), "work_item:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/work-item-relations", projectId)
                        .with(actor(member.getUsername(), "work_item:read")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/{projectId}/work-item-relations", projectId)
                        .with(actor(member.getUsername(), "work_item:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relationRequest(firstId, secondId, "BLOCKS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/work-item-relations", projectId)
                        .with(actor("relation-admin", "work_item:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relationRequest(firstId, secondId, "BLOCKS")))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions createRelation(
            long projectId, long sourceId, long targetId, String type) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/{projectId}/work-item-relations", projectId)
                .with(actor("relation-admin", "work_item:write"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(relationRequest(sourceId, targetId, type)));
    }

    private String relationRequest(long sourceId, long targetId, String type) {
        return """
                {
                  "sourceWorkItemId": %d,
                  "targetWorkItemId": %d,
                  "type": "%s"
                }
                """.formatted(sourceId, targetId, type);
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

    private int successfulAuditCount(String action, long relationId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND resource_type = 'WORK_ITEM_RELATION'
                  AND resource_id = ? AND outcome = 'SUCCESS'
                """, Integer.class, action, Long.toString(relationId));
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
