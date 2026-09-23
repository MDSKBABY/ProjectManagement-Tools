package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证项目文件元数据、资源级授权和安全路径边界。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=file-api-admin",
        "app.bootstrap-admin.password=Test-only-file-api-password-123!",
        "app.bootstrap-admin.display-name=文件接口管理员",
        "app.file-storage.root=${java.io.tmpdir}/project-management-file-api-test"
})
@AutoConfigureMockMvc
@Testcontainers
class ProjectFileMetadataApiTest {

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
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'file-api-admin'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'FILE_ASSET'");
    }

    @Test
    void requiresFilePermissionAndCsrfForMetadataCreation() throws Exception {
        AppUser owner = insertUser("file-permission-owner", "文件权限负责人");
        long projectId = insertProjectWithOwner("FILE-PERMISSION", owner);

        mockMvc.perform(get("/api/v1/projects/{projectId}/files", projectId)
                        .with(actor(owner.getUsername(), "project:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/files/metadata", projectId)
                        .with(actor(owner.getUsername(), "file:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMetadata("交付说明.pdf", 1024)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void reservesMetadataWithServerGeneratedSafeStorageKeyAndAudit() throws Exception {
        AppUser owner = insertUser("file-create-owner", "文件创建负责人");
        long projectId = insertProjectWithOwner("FILE-CREATE", owner);

        mockMvc.perform(post("/api/v1/projects/{projectId}/files/metadata", projectId)
                        .with(actor(owner.getUsername(), "file:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMetadata("交付说明.pdf", 1024)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalName").value("交付说明.pdf"))
                .andExpect(jsonPath("$.mediaType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(1024))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.uploadedBy.displayName").value("文件创建负责人"));

        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM file_asset", String.class);
        assertThat(storageKey)
                .startsWith("projects/" + projectId + "/")
                .doesNotContain("..")
                .doesNotContain("交付说明.pdf");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM file_link WHERE project_id = ? AND business_type = 'PROJECT_DOCUMENT'",
                Integer.class,
                projectId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = 'FILE_METADATA_RESERVED'
                  AND resource_type = 'FILE_ASSET' AND outcome = 'SUCCESS'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void listsOnlyFilesFromVisibleProjectsWithPaginationAndKeyword() throws Exception {
        AppUser owner = insertUser("file-list-owner", "文件列表负责人");
        AppUser member = insertUser("file-list-member", "文件列表成员");
        AppUser outsider = insertUser("file-list-outsider", "文件外部用户");
        long projectId = insertProjectWithOwner("FILE-LIST", owner);
        addMember(projectId, member.getId(), "VIEWER", owner.getId());
        insertReservedFile(projectId, owner.getId(), "部署手册.pdf", "project-manual");
        insertReservedFile(projectId, owner.getId(), "验收报告.docx", "acceptance-report");

        mockMvc.perform(get("/api/v1/projects/{projectId}/files", projectId)
                        .param("keyword", "手册")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .with(actor(member.getUsername(), "file:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].originalName").value("部署手册.pdf"))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        mockMvc.perform(get("/api/v1/projects/{projectId}/files", projectId)
                        .with(actor(outsider.getUsername(), "file:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void rejectsUnsafeNamesAndInvalidMetadata() throws Exception {
        AppUser owner = insertUser("file-validation-owner", "文件校验负责人");
        long projectId = insertProjectWithOwner("FILE-VALIDATION", owner);

        mockMvc.perform(post("/api/v1/projects/{projectId}/files/metadata", projectId)
                        .with(actor(owner.getUsername(), "file:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMetadata("../secret.txt", 12)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/files/metadata", projectId)
                        .with(actor(owner.getUsername(), "file:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMetadata("huge.bin", 2_147_483_649L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/files/metadata", projectId)
                        .with(actor(owner.getUsername(), "file:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalName":"checksum.bin",
                                  "mediaType":"application/octet-stream",
                                  "sizeBytes":16,
                                  "sha256":"not-a-sha256"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
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
        AppUser user = new AppUser();
        user.setId(userId);
        user.setUsername(username);
        user.setDisplayName(displayName);
        return user;
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

    private void insertReservedFile(long projectId, long userId, String name, String key) {
        Long fileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_asset (
                    file_group_id, version, original_name, storage_key, media_type,
                    size_bytes, status, uploaded_by
                ) VALUES (gen_random_uuid(), 1, ?, ?, 'application/octet-stream', 12, 'RESERVED', ?)
                RETURNING id
                """, Long.class, name, "projects/" + projectId + "/" + key, userId);
        jdbcTemplate.update("""
                INSERT INTO file_link (file_asset_id, project_id, business_type, business_id, created_by)
                VALUES (?, ?, 'PROJECT_DOCUMENT', ?, ?)
                """, fileId, projectId, projectId, userId);
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String validMetadata(String originalName, long sizeBytes) {
        return """
                {
                  "originalName":"%s",
                  "mediaType":"application/pdf",
                  "sizeBytes":%d,
                  "sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                }
                """.formatted(originalName, sizeBytes);
    }
}
