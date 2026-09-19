package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 独立验证 JSON 登录接口的完整 Session 生命周期。
 *
 * <p>本测试不调用管理员用户管理接口，避免其他模块的行为掩盖认证会话缺陷。
 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=session-admin",
        "app.bootstrap-admin.password=Test-only-session-password-123!",
        "app.bootstrap-admin.display-name=会话测试管理员"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthenticationSessionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void completesTheLoginCurrentUserAndLogoutLifecycle() throws Exception {
        MvcResult initialCsrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        MockHttpSession session = (MockHttpSession) initialCsrfResult.getRequest().getSession(false);
        JsonNode initialCsrf = responseJson(initialCsrfResult);
        String sessionIdBeforeLogin = session.getId();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(initialCsrf.get("headerName").asText(), initialCsrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "SESSION-ADMIN",
                                  "password": "Test-only-session-password-123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("session-admin"))
                .andExpect(jsonPath("$.displayName").value("会话测试管理员"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        session = (MockHttpSession) loginResult.getRequest().getSession(false);
        // 登录成功必须轮换 Session ID，防止攻击者预先固定受害者的会话。
        assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("session-admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        // 登录会轮换 CSRF 令牌，登录前取得的令牌不能再用于写请求。
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .header(initialCsrf.get("headerName").asText(), initialCsrf.get("token").asText()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        MvcResult refreshedCsrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode refreshedCsrf = responseJson(refreshedCsrfResult);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .header(refreshedCsrf.get("headerName").asText(), refreshedCsrf.get("token").asText()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsMissingOrIncorrectCredentialsWithStableResponses() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        JsonNode csrf = responseJson(csrfResult);

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"session-admin","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing-user","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("用户名或密码错误"));
    }

    @Test
    void requiresCsrfAndValidLoginInput() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"session-admin","password":"Test-only-session-password-123!"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        JsonNode csrf = responseJson(csrfResult);

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /** 把响应统一解析为 JSON，避免测试重复处理字符编码和解析异常。 */
    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
