package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 阶段 0 模块 2 的独立冒烟测试。
 *
 * <p>测试会启动真实随机端口 Web 服务和临时 PostgreSQL，并通过 HTTP 而不是 MockMvc
 * 验证应用能够对外提供健康检查和统一认证错误。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BackendHealthTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /** 验证打包后的核心启动链路和匿名访问边界。 */
    @Test
    void startsARealHttpServerAndExposesOnlyTheHealthEndpointAnonymously() {
        ResponseEntity<JsonNode> health = restTemplate.getForEntity(
                url("/actuator/health"), JsonNode.class);
        ResponseEntity<JsonNode> protectedRoute = restTemplate.getForEntity(
                url("/not-yet-implemented"), JsonNode.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).isNotNull();
        assertThat(health.getBody().path("status").asText()).isEqualTo("UP");

        assertThat(protectedRoute.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(protectedRoute.getBody()).isNotNull();
        assertThat(protectedRoute.getBody().path("error").path("code").asText())
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
