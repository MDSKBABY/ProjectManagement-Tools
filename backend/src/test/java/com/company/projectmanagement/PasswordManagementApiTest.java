package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.domain.UserStatus;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 独立验证用户修改密码、管理员重置密码与会话失效边界。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=password-admin",
        "app.bootstrap-admin.password=Test-only-password-admin-123!",
        "app.bootstrap-admin.display-name=密码测试管理员"
})
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class PasswordManagementApiTest {

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
    void changesOwnPasswordAuditsAndExpiresTheCurrentSession() throws Exception {
        LoginSession login = login("password-admin", "Test-only-password-admin-123!");

        mockMvc.perform(patch("/api/auth/password")
                        .session(login.session())
                        .header(login.csrfHeader(), login.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"wrong-current-password",
                                  "newPassword":"New-self-password-123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_INVALID"));

        mockMvc.perform(patch("/api/auth/password")
                        .session(login.session())
                        .header(login.csrfHeader(), login.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"Test-only-password-admin-123!",
                                  "newPassword":"New-self-password-123!"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(login.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        AppUser administrator = appUserMapper.selectActiveByUsername("password-admin");
        assertThat(passwordEncoder.matches(
                "New-self-password-123!", administrator.getPasswordHash())).isTrue();
        assertThat(identityAccessMapper.countSuccessfulUserAudit(
                administrator.getId(),
                "PASSWORD_CHANGED",
                administrator.getId().toString(),
                administrator.getUsername()))
                .isEqualTo(1);
    }

    @Test
    void administratorResetsAnotherUsersPasswordAndExpiresTheirSession() throws Exception {
        AppUser target = insertUser("password-target", "密码重置目标", "Old-target-password-123!");
        LoginSession targetLogin = login("password-target", "Old-target-password-123!");

        mockMvc.perform(patch("/api/v1/admin/users/{id}/password", target.getId())
                        .with(user("password-admin").authorities(() -> "user:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Reset-target-password-123!"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(targetLogin.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        AppUser updated = appUserMapper.selectActiveByUsername("password-target");
        AppUser administrator = appUserMapper.selectActiveByUsername("password-admin");
        assertThat(passwordEncoder.matches(
                "Reset-target-password-123!", updated.getPasswordHash())).isTrue();
        assertThat(identityAccessMapper.countSuccessfulUserAudit(
                administrator.getId(),
                "PASSWORD_RESET",
                target.getId().toString(),
                target.getUsername()))
                .isEqualTo(1);
    }

    @Test
    void rejectsWeakReusedAndSelfResetPasswords() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("password-admin");

        mockMvc.perform(patch("/api/v1/admin/users/{id}/password", administrator.getId())
                        .with(user("password-admin").authorities(() -> "user:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Another-admin-password-123!"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SELF_PASSWORD_RESET_NOT_ALLOWED"));

        mockMvc.perform(patch("/api/auth/password")
                        .with(user("password-admin"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"Test-only-password-admin-123!",
                                  "newPassword":"Test-only-password-admin-123!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_REUSE_NOT_ALLOWED"));

        mockMvc.perform(patch("/api/auth/password")
                        .with(user("password-admin"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private LoginSession login(String username, String password) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        JsonNode csrf = responseJson(csrfResult);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestBody(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        session = (MockHttpSession) loginResult.getRequest().getSession(false);

        MvcResult refreshedCsrf = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode token = responseJson(refreshedCsrf);
        return new LoginSession(
                session,
                token.get("headerName").asText(),
                token.get("token").asText());
    }

    private AppUser insertUser(String username, String displayName, String rawPassword) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);
        appUserMapper.insertSelective(user);
        return user;
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record LoginRequestBody(String username, String password) {}

    private record LoginSession(MockHttpSession session, String csrfHeader, String csrfToken) {}
}
