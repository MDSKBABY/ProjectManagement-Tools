package com.company.projectmanagement.identity.security;

import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 从数据库加载 Spring Security 所需的用户、角色和权限。
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private static final String USER_NOT_FOUND = "User was not found";

    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;

    public DatabaseUserDetailsService(
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper) {
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
    }

    /**
     * 只允许状态为 ACTIVE 且未软删除的用户进入认证流程。
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException(USER_NOT_FOUND);
        }

        AppUser user = appUserMapper.selectActiveByUsername(username.trim());
        if (user == null) {
            throw new UsernameNotFoundException(USER_NOT_FOUND);
        }

        List<SimpleGrantedAuthority> authorities = Stream.concat(
                        // Spring Security 约定角色使用 ROLE_ 前缀，细粒度权限保持数据库原始编码。
                        identityAccessMapper.selectRoleCodesByUserId(user.getId()).stream()
                                .map(code -> "ROLE_" + code),
                        identityAccessMapper.selectPermissionCodesByUserId(user.getId()).stream())
                .distinct()
                .sorted()
                .map(SimpleGrantedAuthority::new)
                .toList();

        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getDisplayName(),
                authorities);
    }
}
