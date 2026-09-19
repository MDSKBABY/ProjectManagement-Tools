package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.domain.UserStatus;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 独立验证管理员用户管理 API 的查询、创建、状态修改和安全边界。
 *
 * <p>测试通过 Spring Security 模拟登录身份，但业务层仍会从真实数据库确认操作者存在。
 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=admin-api",
        "app.bootstrap-admin.password=Test-only-admin-api-password-123!",
        "app.bootstrap-admin.display-name=接口测试管理员"
})
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class UserAdministrationApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private IdentityAccessMapper identityAccessMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void listsAndFiltersUsersWithoutExposingPasswordHashes() throws Exception {
        AppUser active = insertUser("search-active", "待查询启用用户", null, UserStatus.ACTIVE);
        insertUser("search-disabled", "待查询停用用户", null, UserStatus.DISABLED);

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(administrator())
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("keyword", "启用")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(active.getId()))
                .andExpect(jsonPath("$.data[0].username").value("search-active"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.pageSize").value(20))
                .andExpect(jsonPath("$.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(1));
    }

    @Test
    void enforcesAuthenticationPermissionAndPaginationValidation() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(user("ordinary-user").authorities(() -> "project:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(administrator())
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createsAVisitorWithHashedPasswordAndAuditRecord() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(administrator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest("csrf-required", "csrf-required@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/users")
                        .with(administrator())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "  new-api-user  ",
                                  "initialPassword": "New-api-user-password-123!",
                                  "displayName": "  新接口用户  ",
                                  "email": "new-api-user@example.com",
                                  "mobile": "+86 13800000000"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/admin/users/")))
                .andExpect(jsonPath("$.username").value("new-api-user"))
                .andExpect(jsonPath("$.displayName").value("新接口用户"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.initialPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        long createdUserId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id")
                .asLong();
        AppUser created = appUserMapper.selectActiveByUsername("NEW-API-USER");
        AppUser administrator = appUserMapper.selectActiveByUsername("admin-api");

        assertThat(created.getId()).isEqualTo(createdUserId);
        assertThat(created.getPasswordHash()).isNotEqualTo("New-api-user-password-123!");
        assertThat(passwordEncoder.matches(
                "New-api-user-password-123!", created.getPasswordHash())).isTrue();
        assertThat(identityAccessMapper.selectRoleCodesByUserId(createdUserId))
                .containsExactly("VISITOR");
        assertThat(identityAccessMapper.countSuccessfulUserAudit(
                administrator.getId(),
                "USER_CREATED",
                Long.toString(createdUserId),
                "new-api-user"))
                .isEqualTo(1);
    }

    @Test
    void rejectsDuplicateFieldsAndInvalidPasswords() throws Exception {
        insertUser("existing-user", "已存在用户", "existing@example.com", UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(administrator())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest("EXISTING-USER", "different@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USERNAME_EXISTS"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(administrator())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest("different-user", "EXISTING@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_EXISTS"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(administrator())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest("weak-password-user", "weak@example.com")
                                .replace("Valid-password-123!", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // 30 个汉字少于 72 个字符但超过 BCrypt 的 72 字节限制。
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(administrator())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest("long-byte-password", "bytes@example.com")
                                .replace("Valid-password-123!", "密".repeat(30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void changesStatusIdempotentlyAndWritesOneAuditRecord() throws Exception {
        AppUser target = insertUser("status-target", "状态目标用户", null, UserStatus.ACTIVE);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(patch("/api/v1/admin/users/{id}/status", target.getId())
                            .with(administrator())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"DISABLED"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(target.getId()))
                    .andExpect(jsonPath("$.status").value("DISABLED"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        AppUser administrator = appUserMapper.selectActiveByUsername("admin-api");
        assertThat(appUserMapper.selectForAdministrationById(target.getId()).getStatus())
                .isEqualTo(UserStatus.DISABLED);
        assertThat(identityAccessMapper.countSuccessfulUserAudit(
                administrator.getId(),
                "USER_STATUS_CHANGED",
                target.getId().toString(),
                "status-target"))
                .isEqualTo(1);
    }

    @Test
    void rejectsSelfDisableInvalidStatusAndMissingUser() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("admin-api");

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", administrator.getId())
                        .with(administrator())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SELF_DISABLE_NOT_ALLOWED"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", administrator.getId())
                        .with(administrator())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"LOCKED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", 999999L)
                        .with(administrator())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    private UserRequestPostProcessor administrator() {
        return user("admin-api").authorities(() -> "user:manage");
    }

    /** 插入测试用户；调用方所在事务会在单个测试结束后回滚数据。 */
    private AppUser insertUser(
            String username,
            String displayName,
            String email,
            UserStatus status) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Test-only-user-password-123!"));
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setStatus(status);
        appUserMapper.insertSelective(user);
        return user;
    }

    private static String validCreateRequest(String username, String email) {
        return """
                {
                  "username": "%s",
                  "initialPassword": "Valid-password-123!",
                  "displayName": "待创建用户",
                  "email": "%s"
                }
                """.formatted(username, email);
    }
}
