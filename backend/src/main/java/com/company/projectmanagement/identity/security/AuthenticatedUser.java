package com.company.projectmanagement.identity.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security 会话中保存的最小用户快照。
 *
 * <p>对象只携带认证和授权所需字段；密码哈希仅在认证阶段短暂存在，并可主动清除。
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String username;
    private final String displayName;
    private final List<GrantedAuthority> authorities;
    private String passwordHash;

    public AuthenticatedUser(
            Long id,
            String username,
            String passwordHash,
            String displayName,
            Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.authorities = List.copyOf(authorities);
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        // 认证完成后移除密码哈希，避免敏感凭据长期停留在 Session 或内存对象中。
        passwordHash = null;
    }
}
