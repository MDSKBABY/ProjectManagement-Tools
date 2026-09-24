package com.company.projectmanagement.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员为其他用户设置新密码的请求。 */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 12, max = 72) String newPassword) {}
