package com.company.projectmanagement.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 管理员创建普通用户的请求；初始密码只用于本次哈希，不会写入响应或日志。 */
public record CreateUserRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{3,64}")
        String username,
        @NotBlank
        @Size(min = 12, max = 72)
        String initialPassword,
        @NotBlank
        @Size(max = 100)
        String displayName,
        @Email
        @Size(max = 254)
        String email,
        @Pattern(regexp = "[0-9+() -]{6,32}")
        String mobile) {

    public CreateUserRequest {
        username = trimToNull(username);
        displayName = trimToNull(displayName);
        email = trimToNull(email);
        mobile = trimToNull(mobile);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
