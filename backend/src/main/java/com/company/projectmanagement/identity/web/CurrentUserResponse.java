package com.company.projectmanagement.identity.web;

import com.company.projectmanagement.identity.security.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

public record CurrentUserResponse(
        Long id,
        String username,
        String displayName,
        List<String> roles,
        List<String> permissions) {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String FACTOR_PREFIX = "FACTOR_";

    public static CurrentUserResponse from(AuthenticatedUser user) {
        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        List<String> roles = authorities.stream()
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
        List<String> permissions = authorities.stream()
                .filter(authority -> !authority.startsWith(ROLE_PREFIX))
                .filter(authority -> !authority.startsWith(FACTOR_PREFIX))
                .toList();

        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                roles,
                permissions);
    }
}
