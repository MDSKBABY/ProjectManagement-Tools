package com.company.projectmanagement.identity.service;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.domain.UserStatus;
import com.company.projectmanagement.identity.mapper.AppRoleMapper;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.identity.web.CreateUserRequest;
import com.company.projectmanagement.identity.web.UpdateUserStatusRequest;
import com.company.projectmanagement.identity.web.UserSummaryResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理员用户管理业务层，负责事务、密码保护、默认角色和审计一致性。
 */
@Service
public class UserAdministrationService {

    private final AppUserMapper appUserMapper;
    private final AppRoleMapper appRoleMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAdministrationService(
            AppUserMapper appUserMapper,
            AppRoleMapper appRoleMapper,
            IdentityAccessMapper identityAccessMapper,
            PasswordEncoder passwordEncoder) {
        this.appUserMapper = appUserMapper;
        this.appRoleMapper = appRoleMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 按关键词和状态分页查询未软删除用户。 */
    public PageResponse<UserSummaryResponse> listUsers(
            int page, int pageSize, String keyword, UserStatus status) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long totalItems = appUserMapper.countForAdministration(normalizedKeyword, status);
        List<UserSummaryResponse> data = appUserMapper.selectForAdministration(
                        normalizedKeyword, status, (page - 1) * pageSize, pageSize)
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
        return PageResponse.of(data, page, pageSize, totalItems);
    }

    /**
     * 创建最低权限用户；用户、VISITOR 角色和审计记录必须在同一事务内成功。
     */
    @Transactional
    public UserSummaryResponse createUser(CreateUserRequest request, String actorUsername) {
        validateUniqueFields(request.username(), request.email());
        if (request.initialPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "初始密码 UTF-8 编码后不能超过 72 字节");
        }

        AppUser actor = appUserMapper.selectActiveByUsername(actorUsername);
        Long visitorRoleId = appRoleMapper.selectActiveIdByCode("VISITOR");
        if (actor == null || visitorRoleId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        }

        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.initialPassword()));
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());
        user.setMobile(request.mobile());
        try {
            appUserMapper.insertSelective(user);
        } catch (DuplicateKeyException exception) {
            // 预检查改善提示，数据库唯一索引仍负责阻止并发请求绕过检查。
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USER_CONFLICT",
                    "用户名或邮箱已存在");
        }

        identityAccessMapper.assignRole(user.getId(), visitorRoleId, actor.getId());
        identityAccessMapper.recordUserCreatedAudit(
                actor.getId(), user.getId().toString(), user.getUsername());
        return UserSummaryResponse.from(appUserMapper.selectForAdministrationById(user.getId()));
    }

    private void validateUniqueFields(String username, String email) {
        if (appUserMapper.existsActiveUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }
        if (email != null && appUserMapper.existsActiveEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "邮箱已存在");
        }
    }

    /**
     * 启用或停用用户。重复设置同一状态视为幂等成功，不重复写审计日志。
     */
    @Transactional
    public UserSummaryResponse updateStatus(
            Long userId, UpdateUserStatusRequest request, String actorUsername) {
        if (request.status() != UserStatus.ACTIVE && request.status() != UserStatus.DISABLED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "用户状态只能设置为 ACTIVE 或 DISABLED");
        }

        AppUser actor = appUserMapper.selectActiveByUsername(actorUsername);
        if (actor == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        }
        AppUser target = appUserMapper.selectForAdministrationById(userId);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        if (actor.getId().equals(userId) && request.status() == UserStatus.DISABLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SELF_DISABLE_NOT_ALLOWED",
                    "不能停用当前登录账号");
        }
        if (target.getStatus() == request.status()) {
            return UserSummaryResponse.from(target);
        }

        appUserMapper.updateStatusForAdministration(userId, request.status());
        identityAccessMapper.recordUserStatusChangedAudit(
                actor.getId(),
                userId.toString(),
                target.getUsername(),
                target.getStatus(),
                request.status());
        return UserSummaryResponse.from(appUserMapper.selectForAdministrationById(userId));
    }
}
