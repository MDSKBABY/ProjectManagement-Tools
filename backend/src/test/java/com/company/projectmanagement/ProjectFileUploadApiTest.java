package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.projectmanagement.identity.domain.AppUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
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

/** 验证固定分片、合并校验、版本链和下载时重新鉴权。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=file-upload-admin",
        "app.bootstrap-admin.password=Test-only-file-upload-password-123!",
        "app.bootstrap-admin.display-name=文件上传管理员",
        "app.file-storage.root=/tmp/project-management-file-upload-api-test",
        "app.file-storage.chunk-size-bytes=6"
})
@AutoConfigureMockMvc
@Testcontainers
class ProjectFileUploadApiTest {

    private static final Path STORAGE_ROOT = Path.of("/tmp/project-management-file-upload-api-test");

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
    void cleanTestData() throws IOException {
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'file-upload-admin'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'FILE_ASSET'");
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
    void uploadsChunksIdempotentlyCompletesWithSha256AndDownloads() throws Exception {
        AppUser owner = insertUser("upload-owner", "上传负责人");
        long projectId = insertProjectWithOwner("UPLOAD-SUCCESS", owner);
        byte[] content = "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode asset = reserve(projectId, owner, "hello.txt", content.length, sha256(content), null);
        long fileId = asset.get("id").asLong();

        uploadChunk(projectId, fileId, 0, "hello ".getBytes(), owner)
                .andExpect(status().isNoContent());
        uploadChunk(projectId, fileId, 0, "hello ".getBytes(), owner)
                .andExpect(status().isNoContent());
        uploadChunk(projectId, fileId, 1, "world".getBytes(), owner)
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/projects/{projectId}/files/{fileId}/complete", projectId, fileId)
                        .with(actor(owner.getUsername(), "file:write"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.sha256").value(sha256(content)));

        mockMvc.perform(get("/api/v1/projects/{projectId}/files/{fileId}/download", projectId, fileId)
                        .with(actor(owner.getUsername(), "file:read")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("hello.txt")))
                .andExpect(content().bytes(content));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM file_upload_chunk WHERE file_asset_id = ?",
                Integer.class,
                fileId)).isZero();
        assertThat(auditCount("FILE_UPLOAD_COMPLETED", fileId)).isEqualTo(1);
        assertThat(auditCount("FILE_DOWNLOADED", fileId)).isEqualTo(1);
    }

    @Test
    void rejectsOutOfRangeWrongSizedAndConflictingChunks() throws Exception {
        AppUser owner = insertUser("chunk-owner", "分片负责人");
        long projectId = insertProjectWithOwner("UPLOAD-CHUNK", owner);
        JsonNode asset = reserve(projectId, owner, "chunk.bin", 11, null, null);
        long fileId = asset.get("id").asLong();

        uploadChunk(projectId, fileId, 2, "extra".getBytes(), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_CHUNK_OUT_OF_RANGE"));
        uploadChunk(projectId, fileId, 0, "short".getBytes(), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_CHUNK_SIZE_INVALID"));
        uploadChunk(projectId, fileId, 0, "first!".getBytes(), owner)
                .andExpect(status().isNoContent());
        uploadChunk(projectId, fileId, 0, "second".getBytes(), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_CHUNK_CONFLICT"));
    }

    @Test
    void rejectsChecksumMismatchAndDoesNotExposeFailedContent() throws Exception {
        AppUser owner = insertUser("checksum-owner", "校验负责人");
        long projectId = insertProjectWithOwner("UPLOAD-CHECKSUM", owner);
        JsonNode asset = reserve(projectId, owner, "checksum.txt", 5, sha256("other".getBytes()), null);
        long fileId = asset.get("id").asLong();

        uploadChunk(projectId, fileId, 0, "hello".getBytes(), owner)
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/projects/{projectId}/files/{fileId}/complete", projectId, fileId)
                        .with(actor(owner.getUsername(), "file:write"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("FILE_CHECKSUM_MISMATCH"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM file_asset WHERE id = ?", String.class, fileId)).isEqualTo("FAILED");
        mockMvc.perform(get("/api/v1/projects/{projectId}/files/{fileId}/download", projectId, fileId)
                        .with(actor(owner.getUsername(), "file:read")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_AVAILABLE"));
    }

    @Test
    void createsImmutableVersionsAndHidesDownloadsFromProjectOutsiders() throws Exception {
        AppUser owner = insertUser("version-owner", "版本负责人");
        AppUser outsider = insertUser("version-outsider", "项目外用户");
        long projectId = insertProjectWithOwner("UPLOAD-VERSION", owner);
        byte[] firstContent = "first".getBytes();
        JsonNode first = reserve(projectId, owner, "manual.txt", 5, sha256(firstContent), null);
        long firstId = first.get("id").asLong();
        uploadChunk(projectId, firstId, 0, firstContent, owner).andExpect(status().isNoContent());
        complete(projectId, firstId, owner);

        JsonNode second = reserve(
                projectId,
                owner,
                "manual.txt",
                6,
                sha256("second".getBytes()),
                first.get("fileGroupId").asText());
        assertThat(second.get("version").asInt()).isEqualTo(2);

        mockMvc.perform(get("/api/v1/projects/{projectId}/files/groups/{fileGroupId}/versions",
                        projectId, first.get("fileGroupId").asText())
                        .with(actor(owner.getUsername(), "file:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].version").value(1));

        mockMvc.perform(get("/api/v1/projects/{projectId}/files/{fileId}/download", projectId, firstId)
                        .with(actor(outsider.getUsername(), "file:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void cancelsAnOpenUploadAndRemovesRecordedChunks() throws Exception {
        AppUser owner = insertUser("cancel-owner", "取消负责人");
        long projectId = insertProjectWithOwner("UPLOAD-CANCEL", owner);
        JsonNode asset = reserve(projectId, owner, "cancel.bin", 11, null, null);
        long fileId = asset.get("id").asLong();
        uploadChunk(projectId, fileId, 0, "first!".getBytes(), owner)
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/projects/{projectId}/files/{fileId}/upload", projectId, fileId)
                        .with(actor(owner.getUsername(), "file:write"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM file_asset WHERE id = ?", String.class, fileId)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM file_upload_chunk WHERE file_asset_id = ?",
                Integer.class,
                fileId)).isZero();
        assertThat(auditCount("FILE_UPLOAD_CANCELLED", fileId)).isEqualTo(1);
    }

    private JsonNode reserve(
            long projectId,
            AppUser owner,
            String name,
            long size,
            String sha256,
            String fileGroupId) throws Exception {
        var input = new java.util.LinkedHashMap<String, Object>();
        input.put("originalName", name);
        input.put("mediaType", "application/octet-stream");
        input.put("sizeBytes", size);
        if (sha256 != null) input.put("sha256", sha256);
        if (fileGroupId != null) input.put("fileGroupId", fileGroupId);
        String request = objectMapper.writeValueAsString(input);
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/files/metadata", projectId)
                        .with(actor(owner.getUsername(), "file:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions uploadChunk(
            long projectId, long fileId, int index, byte[] bytes, AppUser owner) throws Exception {
        return mockMvc.perform(put(
                        "/api/v1/projects/{projectId}/files/{fileId}/chunks/{index}",
                        projectId,
                        fileId,
                        index)
                .with(actor(owner.getUsername(), "file:write"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(bytes));
    }

    private void complete(long projectId, long fileId, AppUser owner) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/files/{fileId}/complete", projectId, fileId)
                        .with(actor(owner.getUsername(), "file:write"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private AppUser insertUser(String username, String displayName) {
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, display_name)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, username,
                "{bcrypt}$2a$12$test-hash-not-used-by-mock-authentication", displayName);
        AppUser user = new AppUser();
        user.setId(userId);
        user.setUsername(username);
        user.setDisplayName(displayName);
        return user;
    }

    private long insertProjectWithOwner(String code, AppUser owner) {
        long projectId = jdbcTemplate.queryForObject("""
                INSERT INTO project (code, name, status, owner_id, created_by)
                VALUES (?, ?, 'PLANNING', ?, ?) RETURNING id
                """, Long.class, code, code, owner.getId(), owner.getId());
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, project_role, created_by)
                VALUES (?, ?, 'OWNER', ?)
                """, projectId, owner.getId(), owner.getId());
        return projectId;
    }

    private int auditCount(String action, long fileId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND resource_type = 'FILE_ASSET'
                  AND resource_id = ? AND outcome = 'SUCCESS'
                """, Integer.class, action, Long.toString(fileId));
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String sha256(byte[] content) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
