package com.company.projectmanagement.config;

import com.company.projectmanagement.common.web.ApiErrorResponse;
import com.company.projectmanagement.identity.security.DatabaseUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * 集中定义认证、会话、CSRF 和接口授权规则。
 *
 * <p>安全规则放在同一处，便于审查新增接口是否被正确保护；未明确放行的路由默认拒绝。
 */
@Configuration
public class SecurityConfiguration {

    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    PasswordEncoder passwordEncoder() {
        // DelegatingPasswordEncoder 会把算法标识写入哈希，便于将来平滑升级密码算法。
        PasswordEncoder bcrypt = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
    }

    @Bean
    AuthenticationManager authenticationManager(
            DatabaseUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            CsrfTokenRepository csrfTokenRepository) {
        // 登录成功后同时轮换 Session ID 和 CSRF 令牌，阻断会话固定攻击。
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    SecurityContextLogoutHandler securityContextLogoutHandler() {
        return new SecurityContextLogoutHandler();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            ObjectMapper objectMapper) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/auth/csrf",
                                "/api/auth/login")
                        .permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/v1/admin/users", "/api/v1/admin/users/**")
                        .hasAuthority("user:manage")
                        // 新接口必须在此明确声明授权规则，否则保持默认拒绝。
                        .anyRequest().denyAll())
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED",
                                "请先登录"))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_FORBIDDEN,
                                "ACCESS_DENIED",
                                "无权访问")))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private static void writeError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            String code,
            String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(code, message));
    }
}
