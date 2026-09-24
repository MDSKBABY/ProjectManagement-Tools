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
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
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
            CsrfTokenRepository csrfTokenRepository,
            SessionRegistry sessionRegistry) {
        // 登录成功后轮换 Session ID、CSRF 令牌并登记会话，便于密码变更后统一失效。
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)));
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
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
            SessionRegistry sessionRegistry,
            ObjectMapper objectMapper) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/auth/csrf",
                                "/api/auth/login")
                        .permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/logout", "/api/auth/password")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/audit-logs")
                        .hasAuthority("audit:read")
                        .requestMatchers("/api/v1/admin/users", "/api/v1/admin/users/**")
                        .hasAuthority("user:manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/projects/*/files")
                        .hasAuthority("file:read")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/projects/*/files/*/download",
                                "/api/v1/projects/*/files/groups/*/versions")
                        .hasAuthority("file:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/*/files/metadata")
                        .hasAuthority("file:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/projects/*/files/*/chunks/*")
                        .hasAuthority("file:write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/*/files/*/complete")
                        .hasAuthority("file:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/projects/*/files/*/upload")
                        .hasAuthority("file:write")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/projects/*/deployment-assets",
                                "/api/v1/projects/*/deployment-assets/**")
                        .hasAuthority("deployment_asset:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/*/deployment-assets")
                        .hasAuthority("deployment_asset:write")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/projects/*/servers/*/credential")
                        .hasAuthority("server_credential:read")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/projects/*/servers/*/credential")
                        .hasAuthority("server_credential:manage")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/projects/*/servers",
                                "/api/v1/projects/*/servers/*")
                        .hasAuthority("server:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/*/servers")
                        .hasAuthority("server:write")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/projects/*/servers/*")
                        .hasAuthority("server:write")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/projects/*/servers/*")
                        .hasAuthority("server:write")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/projects/*/environment-fingerprints",
                                "/api/v1/projects/*/environment-fingerprints/*")
                        .hasAuthority("environment_fingerprint:read")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/projects/*/environment-fingerprints")
                        .hasAuthority("environment_fingerprint:write")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/projects/*/environment-fingerprints/*")
                        .hasAuthority("environment_fingerprint:write")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/projects/*/environment-fingerprints/*")
                        .hasAuthority("environment_fingerprint:write")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/projects/*/deployment-solutions",
                                "/api/v1/projects/*/deployment-solutions/*")
                        .hasAuthority("deployment_solution:read")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/projects/*/deployment-solutions")
                        .hasAuthority("deployment_solution:write")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/projects/*/deployment-solutions/*")
                        .hasAuthority("deployment_solution:write")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/projects/*/deployment-solutions/*")
                        .hasAuthority("deployment_solution:write")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/projects/*/deployment-records",
                                "/api/v1/projects/*/deployment-records/**")
                        .hasAuthority("deployment_record:read")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/projects/*/deployment-records")
                        .hasAuthority("deployment_record:write")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/projects/*/deployment-records/*/baseline")
                        .hasAuthority("deployment_record:write")
                        .requestMatchers(
                                HttpMethod.GET, "/api/v1/projects/*/members/candidates")
                        .hasAuthority("project:manage_members")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/projects/*/work-items",
                                "/api/v1/projects/*/work-items/**")
                        .hasAuthority("work_item:read")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/projects/*/work-items",
                                "/api/v1/projects/*/work-items/*/status-transitions")
                        .hasAuthority("work_item:write")
                        .requestMatchers(
                                HttpMethod.PATCH, "/api/v1/projects/*/work-items/*")
                        .hasAuthority("work_item:write")
                        .requestMatchers(
                                HttpMethod.DELETE, "/api/v1/projects/*/work-items/*")
                        .hasAuthority("work_item:delete")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/projects/*/work-item-relations",
                                "/api/v1/projects/*/work-item-relations/**")
                        .hasAuthority("work_item:read")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/projects/*/work-item-relations")
                        .hasAuthority("work_item:write")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/projects/*/work-item-relations/*")
                        .hasAuthority("work_item:write")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/projects/*/work-item-reminders",
                                "/api/v1/projects/*/work-item-reminders/**")
                        .hasAuthority("work_item:read")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/projects/*/work-item-reminders",
                                "/api/v1/projects/*/work-item-reminders/*/dismiss")
                        .hasAuthority("work_item:write")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/projects/*/work-item-reminders/*")
                        .hasAuthority("work_item:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/projects", "/api/v1/projects/**")
                        .hasAuthority("project:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/*/members")
                        .hasAuthority("project:manage_members")
                        .requestMatchers(
                                HttpMethod.PUT, "/api/v1/projects/*/members/*")
                        .hasAuthority("project:manage_members")
                        .requestMatchers(
                                HttpMethod.DELETE, "/api/v1/projects/*/members/*")
                        .hasAuthority("project:manage_members")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects")
                        .hasAuthority("project:create")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/projects/**")
                        .hasAuthority("project:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/projects/**")
                        .hasAuthority("project:delete")
                        // 新接口必须在此明确声明授权规则，否则保持默认拒绝。
                        .anyRequest().denyAll())
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event -> writeError(
                                event.getResponse(),
                                objectMapper,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED",
                                "会话已失效，请重新登录")))
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
