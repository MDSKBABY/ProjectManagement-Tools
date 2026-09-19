package com.company.projectmanagement.identity.bootstrap;

import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppRoleMapper;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 在全新数据库中按环境变量创建首位管理员。
 *
 * <p>初始化器不会覆盖任何已有用户，也不会在重复启动时修改密码或提升权限。
 */
@Component
public class InitialAdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAdminBootstrap.class);
    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;

    private final String configuredUsername;
    private final String configuredPassword;
    private final String configuredDisplayName;
    private final AppUserMapper appUserMapper;
    private final AppRoleMapper appRoleMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final PasswordEncoder passwordEncoder;

    public InitialAdminBootstrap(
            @Value("${app.bootstrap-admin.username:}") String configuredUsername,
            @Value("${app.bootstrap-admin.password:}") String configuredPassword,
            @Value("${app.bootstrap-admin.display-name:系统管理员}") String configuredDisplayName,
            AppUserMapper appUserMapper,
            AppRoleMapper appRoleMapper,
            IdentityAccessMapper identityAccessMapper,
            PasswordEncoder passwordEncoder) {
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
        this.configuredDisplayName = configuredDisplayName;
        this.appUserMapper = appUserMapper;
        this.appRoleMapper = appRoleMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 原子完成管理员创建、ADMIN 角色绑定和审计记录。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        boolean hasUsername = StringUtils.hasText(configuredUsername);
        boolean hasPassword = StringUtils.hasText(configuredPassword);

        if (!hasUsername && !hasPassword) {
            LOGGER.info("未配置首位管理员，跳过初始化");
            return;
        }
        if (!hasUsername || !hasPassword) {
            throw new IllegalStateException(
                    "INITIAL_ADMIN_USERNAME 和 INITIAL_ADMIN_PASSWORD 必须同时配置");
        }

        if (appUserMapper.countAllUsers() > 0) {
            // 只要出现过用户就停止引导，避免遗留环境变量接管现有系统。
            LOGGER.info("数据库中已存在用户，跳过首位管理员初始化且不会覆盖现有账号");
            return;
        }

        String username = configuredUsername.trim();
        String displayName = configuredDisplayName.trim();
        validate(username, configuredPassword, displayName);

        Long administratorRoleId = appRoleMapper.selectActiveIdByCode("ADMIN");
        if (administratorRoleId == null) {
            throw new IllegalStateException("缺少 ADMIN 基础角色，无法初始化首位管理员");
        }

        AppUser administrator = new AppUser();
        administrator.setUsername(username);
        administrator.setPasswordHash(passwordEncoder.encode(configuredPassword));
        administrator.setDisplayName(displayName);
        appUserMapper.insertSelective(administrator);

        identityAccessMapper.assignRole(
                administrator.getId(), administratorRoleId, null);
        identityAccessMapper.recordSuccessfulSystemAudit(
                "INITIAL_ADMIN_CREATED", "USER", administrator.getId().toString());

        LOGGER.info("首位管理员已安全初始化；请移除初始化环境变量并妥善保管密码");
    }

    private static void validate(String username, String password, String displayName) {
        if (!username.matches("[A-Za-z0-9._-]{3,64}")) {
            throw new IllegalStateException(
                    "INITIAL_ADMIN_USERNAME 必须为 3-64 位字母、数字、点、下划线或连字符");
        }
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < MINIMUM_PASSWORD_LENGTH
                || passwordBytes > MAXIMUM_BCRYPT_PASSWORD_BYTES) {
            throw new IllegalStateException(
                    "INITIAL_ADMIN_PASSWORD 至少 12 个字符且 UTF-8 编码后不能超过 72 字节");
        }
        if (!StringUtils.hasText(displayName) || displayName.length() > 100) {
            throw new IllegalStateException("INITIAL_ADMIN_DISPLAY_NAME 必须为 1-100 个字符");
        }
    }
}
