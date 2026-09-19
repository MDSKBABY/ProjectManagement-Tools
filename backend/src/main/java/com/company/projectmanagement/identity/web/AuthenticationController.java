package com.company.projectmanagement.identity.web;

import com.company.projectmanagement.identity.security.AuthenticatedUser;
import com.company.projectmanagement.identity.security.LoginAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供基于服务端 Session Cookie 的 JSON 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final LoginAuthenticationService loginAuthenticationService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextLogoutHandler logoutHandler;

    public AuthenticationController(
            LoginAuthenticationService loginAuthenticationService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            SecurityContextLogoutHandler logoutHandler) {
        this.loginAuthenticationService = loginAuthenticationService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.logoutHandler = logoutHandler;
    }

    /** 返回前端执行写请求所需的当前 CSRF 令牌。 */
    @GetMapping("/csrf")
    CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return CsrfTokenResponse.from(csrfToken);
    }

    /**
     * 登录并显式保存安全上下文；认证策略会在此轮换 Session ID 和 CSRF 令牌。
     */
    @PostMapping("/login")
    CurrentUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthenticatedUser user = loginAuthenticationService.authenticate(
                request.username(), request.password());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities());

        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return CurrentUserResponse.from(user);
    }

    /** 返回当前会话中的用户、角色和权限，不包含密码信息。 */
    @GetMapping("/me")
    CurrentUserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return CurrentUserResponse.from(user);
    }

    /** 清理安全上下文并使服务端会话失效。 */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
