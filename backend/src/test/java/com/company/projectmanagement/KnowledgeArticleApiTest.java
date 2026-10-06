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

/** 验证技术知识库的项目边界、审核流程、附件约束和检索。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=knowledge-admin",
        "app.bootstrap-admin.password=Test-only-knowledge-password-123!",
        "app.bootstrap-admin.display-name=知识库管理员"
})
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class KnowledgeArticleApiTest {

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
        jdbcTemplate.update("DELETE FROM file_link WHERE business_type = 'KNOWLEDGE_ARTICLE'");
        jdbcTemplate.update("DELETE FROM knowledge_article");
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'knowledge-admin'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'KNOWLEDGE_ARTICLE'");
    }

    @Test
    void requiresFunctionalPermissionAndCsrf() throws Exception {
        AppUser owner = insertUser("knowledge-permission-owner", "知识权限负责人");
        long projectId = insertProjectWithMember("KNOWLEDGE-PERMISSION", owner, "OWNER");

        mockMvc.perform(get("/api/v1/projects/{projectId}/knowledge-articles", projectId)
                        .with(actor(owner.getUsername(), "project:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/knowledge-articles", projectId)
                        .with(actor(owner.getUsername(), "knowledge_article:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleRequest("部署端口冲突", new long[0])))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void createsSearchesAndUpdatesDraftWithAvailableAttachments() throws Exception {
        AppUser member = insertUser("knowledge-author", "知识作者");
        long projectId = insertProjectWithMember("KNOWLEDGE-CREATE", member, "MEMBER");
        long fileId = insertFile(projectId, member.getId(), "port-check.log", "AVAILABLE");

        JsonNode created = create(projectId, member,
                articleRequest("部署端口冲突", new long[] {fileId}));
        long articleId = created.get("id").asLong();
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("attachments").get(0).get("id").asLong()).isEqualTo(fileId);

        mockMvc.perform(get("/api/v1/projects/{projectId}/knowledge-articles", projectId)
                        .param("keyword", "端口")
                        .param("status", "DRAFT")
                        .param("tag", "Linux")
                        .with(actor(member.getUsername(), "knowledge_article:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("部署端口冲突"))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        mockMvc.perform(put("/api/v1/projects/{projectId}/knowledge-articles/{articleId}",
                                projectId, articleId)
                        .with(actor(member.getUsername(), "knowledge_article:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleRequest("部署端口被占用", new long[] {fileId})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("部署端口被占用"));

        assertThat(auditCount("KNOWLEDGE_ARTICLE_CREATED", articleId)).isEqualTo(1);
        assertThat(auditCount("KNOWLEDGE_ARTICLE_UPDATED", articleId)).isEqualTo(1);
    }

    @Test
    void enforcesSubmissionAttachmentsAndManagerReviewWorkflow() throws Exception {
        AppUser owner = insertUser("knowledge-review-owner", "审核负责人");
        AppUser member = insertUser("knowledge-review-member", "知识作者");
        long projectId = insertProjectWithMember("KNOWLEDGE-REVIEW", owner, "OWNER");
        addProjectMember(projectId, member, "MEMBER", owner.getId());

        JsonNode withoutAttachment = create(projectId, member,
                articleRequest("无附件草稿", new long[0]));
        mockMvc.perform(post("/api/v1/projects/{projectId}/knowledge-articles/{articleId}/submit",
                                projectId, withoutAttachment.get("id").asLong())
                        .with(actor(member.getUsername(), "knowledge_article:write"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KNOWLEDGE_ATTACHMENT_REQUIRED"));

        long fileId = insertFile(projectId, member.getId(), "solution.md", "AVAILABLE");
        JsonNode article = create(projectId, member,
                articleRequest("可审核知识", new long[] {fileId}));
        long articleId = article.get("id").asLong();

        mockMvc.perform(post("/api/v1/projects/{projectId}/knowledge-articles/{articleId}/submit",
                                projectId, articleId)
                        .with(actor(member.getUsername(), "knowledge_article:write"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/knowledge-articles/{articleId}/review",
                                projectId, articleId)
                        .with(actor(member.getUsername(), "knowledge_article:review"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"comment\":\"可以复用\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KNOWLEDGE_REVIEW_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/knowledge-articles/{articleId}/review",
                                projectId, articleId)
                        .with(actor(owner.getUsername(), "knowledge_article:review"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"comment\":\"可以复用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewComment").value("可以复用"))
                .andExpect(jsonPath("$.reviewedByDisplayName").value("审核负责人"));

        mockMvc.perform(put("/api/v1/projects/{projectId}/knowledge-articles/{articleId}",
                                projectId, articleId)
                        .with(actor(member.getUsername(), "knowledge_article:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleRequest("不能覆盖已通过知识", new long[] {fileId})))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KNOWLEDGE_ARTICLE_LOCKED"));
    }

    @Test
    void rejectsCrossProjectFilesAndHidesArticlesFromOutsiders() throws Exception {
        AppUser owner = insertUser("knowledge-visible-owner", "可见项目负责人");
        AppUser outsider = insertUser("knowledge-outsider", "项目外人员");
        long projectId = insertProjectWithMember("KNOWLEDGE-VISIBLE", owner, "OWNER");
        long otherProjectId = insertProjectWithMember("KNOWLEDGE-OTHER", outsider, "OWNER");
        long foreignFileId = insertFile(otherProjectId, outsider.getId(), "foreign.txt", "AVAILABLE");

        mockMvc.perform(post("/api/v1/projects/{projectId}/knowledge-articles", projectId)
                        .with(actor(owner.getUsername(), "knowledge_article:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleRequest("跨项目附件", new long[] {foreignFileId})))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));

        JsonNode article = create(projectId, owner, articleRequest("项目内知识", new long[0]));
        mockMvc.perform(get("/api/v1/projects/{projectId}/knowledge-articles/{articleId}",
                                projectId, article.get("id").asLong())
                        .with(actor(outsider.getUsername(), "knowledge_article:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/knowledge-articles/{articleId}",
                                projectId, article.get("id").asLong())
                        .with(actor(owner.getUsername(), "knowledge_article:write"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private JsonNode create(long projectId, AppUser actor, String request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/knowledge-articles", projectId)
                        .with(actor(actor.getUsername(), "knowledge_article:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
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

    private long insertProjectWithMember(String code, AppUser member, String role) {
        long projectId = jdbcTemplate.queryForObject("""
                INSERT INTO project (code, name, status, owner_id, created_by)
                VALUES (?, ?, 'PLANNING', ?, ?) RETURNING id
                """, Long.class, code, code, member.getId(), member.getId());
        addProjectMember(projectId, member, role, member.getId());
        return projectId;
    }

    private void addProjectMember(long projectId, AppUser member, String role, long createdBy) {
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, project_role, created_by)
                VALUES (?, ?, ?, ?)
                """, projectId, member.getId(), role, createdBy);
    }

    private long insertFile(long projectId, long userId, String name, String status) {
        Long fileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_asset (
                    file_group_id, version, original_name, storage_key, media_type,
                    size_bytes, sha256, status, uploaded_by
                ) VALUES (gen_random_uuid(), 1, ?, ?, 'text/plain', 12,
                          repeat('a', 64), ?, ?) RETURNING id
                """, Long.class, name, "projects/" + projectId + "/" + name, status, userId);
        jdbcTemplate.update("""
                INSERT INTO file_link (file_asset_id, project_id, business_type, business_id, created_by)
                VALUES (?, ?, 'PROJECT_DOCUMENT', ?, ?)
                """, fileId, projectId, projectId, userId);
        return fileId;
    }

    private int auditCount(String action, long articleId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND resource_type = 'KNOWLEDGE_ARTICLE'
                  AND resource_id = ? AND outcome = 'SUCCESS'
                """, Integer.class, action, Long.toString(articleId));
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String articleRequest(String title, long[] fileIds) {
        String attachments = Arrays.stream(fileIds)
                .mapToObj(Long::toString)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return """
                {
                  "title":"%s",
                  "scenario":"Linux 单机部署",
                  "symptom":"服务启动时提示 Address already in use",
                  "cause":"目标端口已被其他进程占用",
                  "solution":"确认占用进程后调整端口或停止冲突服务",
                  "applicableConditions":"Spring Boot 服务",
                  "tags":["Linux","部署"],
                  "attachmentIds":[%s]
                }
                """.formatted(title, attachments);
    }
}
