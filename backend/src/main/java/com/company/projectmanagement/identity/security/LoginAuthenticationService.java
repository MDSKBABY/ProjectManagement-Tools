package com.company.projectmanagement.identity.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * 对用户名密码认证做统一封装，并确保所有失败场景返回相同提示。
 */
@Service
public class LoginAuthenticationService {

    private static final String INVALID_CREDENTIALS = "用户名或密码错误";

    private final AuthenticationManager authenticationManager;

    public LoginAuthenticationService(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /**
     * 校验账号密码并返回已经加载角色和权限的当前用户。
     *
     * @param username 用户名，匹配时忽略大小写
     * @param rawPassword 仅用于本次校验的原始密码
     * @return 已认证用户；返回前凭据会由认证管理器按配置清除
     */
    public AuthenticatedUser authenticate(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        try {
            Authentication result = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username.trim(), rawPassword));
            return (AuthenticatedUser) result.getPrincipal();
        } catch (AuthenticationException exception) {
            // 不区分账号不存在、停用或密码错误，避免向攻击者泄露账号状态。
            throw new BadCredentialsException(INVALID_CREDENTIALS, exception);
        }
    }
}
