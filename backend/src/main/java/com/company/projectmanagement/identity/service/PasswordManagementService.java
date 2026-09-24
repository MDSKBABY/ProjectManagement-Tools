package com.company.projectmanagement.identity.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.identity.security.AuthenticatedUser;
import com.company.projectmanagement.identity.web.ChangePasswordRequest;
import com.company.projectmanagement.identity.web.ResetPasswordRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 统一处理密码校验、哈希更新、审计和现有会话失效。 */
@Service
public class PasswordManagementService {

    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;

    public PasswordManagementService(
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper,
            PasswordEncoder passwordEncoder,
            SessionRegistry sessionRegistry) {
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
    }

    /** 校验当前密码后修改自己的密码，并使全部现有会话失效。 */
    @Transactional
    public void changeOwnPassword(String username, ChangePasswordRequest request) {
        AppUser user = appUserMapper.selectActiveByUsername(username);
        if (user == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        }
        if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CURRENT_PASSWORD_INVALID",
                    "当前密码不正确");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CURRENT_PASSWORD_INVALID",
                    "当前密码不正确");
        }
        validateNewPassword(request.newPassword(), user.getPasswordHash());

        appUserMapper.updatePasswordHash(user.getId(), passwordEncoder.encode(request.newPassword()));
        identityAccessMapper.recordPasswordChangedAudit(
                user.getId(), user.getId().toString(), user.getUsername());
        expireSessions(user.getId());
    }

    /** 管理员重置其他用户的密码，不允许绕过旧密码校验重置自己。 */
    @Transactional
    public void resetPassword(
            Long userId,
            ResetPasswordRequest request,
            String actorUsername) {
        AppUser actor = appUserMapper.selectActiveByUsername(actorUsername);
        if (actor == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        }
        if (actor.getId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SELF_PASSWORD_RESET_NOT_ALLOWED",
                    "请使用修改密码功能更新当前账号密码");
        }
        AppUser target = appUserMapper.selectForAdministrationById(userId);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        validateNewPassword(request.newPassword(), target.getPasswordHash());

        appUserMapper.updatePasswordHash(target.getId(), passwordEncoder.encode(request.newPassword()));
        identityAccessMapper.recordPasswordResetAudit(
                actor.getId(), target.getId().toString(), target.getUsername());
        expireSessions(target.getId());
    }

    private void validateNewPassword(String rawPassword, String existingHash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "新密码 UTF-8 编码后不能超过 72 字节");
        }
        if (passwordEncoder.matches(rawPassword, existingHash)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PASSWORD_REUSE_NOT_ALLOWED",
                    "新密码不能与当前密码相同");
        }
    }

    private void expireSessions(Long userId) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(AuthenticatedUser.class::isInstance)
                .map(AuthenticatedUser.class::cast)
                .filter(principal -> principal.getId().equals(userId))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
