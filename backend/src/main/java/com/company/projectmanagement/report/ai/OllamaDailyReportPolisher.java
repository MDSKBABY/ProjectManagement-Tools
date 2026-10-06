package com.company.projectmanagement.report.ai;

import com.company.projectmanagement.common.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Ollama 调用边界。任何网络或协议错误都转为可理解的 503，业务数据不会在这里写入。 */
@Component
public class OllamaDailyReportPolisher implements DailyReportPolisher {
    private final boolean enabled;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaDailyReportPolisher(
            @Value("${app.ollama.enabled:false}") boolean enabled,
            @Value("${app.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.ollama.model:qwen2.5:3b}") String model,
            @Value("${app.ollama.timeout-seconds:30}") long timeoutSeconds,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public String polish(String originalContent) {
        if (!enabled) {
            throw unavailable(null);
        }
        try {
            String prompt = "请将下面的项目日报润色为简洁、专业的中文，忠于原意，"
                    + "不要编造事实，只输出润色后的正文：\n\n" + originalContent;
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model, "prompt", prompt, "stream", false));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable(null);
            }
            JsonNode json = objectMapper.readTree(response.body());
            String result = json.path("response").asText();
            if (!StringUtils.hasText(result)) {
                throw unavailable(null);
            }
            return result.trim();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw unavailable(exception);
        }
    }

    private static ApiException unavailable(Exception cause) {
        ApiException exception = new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OLLAMA_UNAVAILABLE",
                "日报已保留，但 AI 润色服务暂时不可用，请稍后重试");
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
