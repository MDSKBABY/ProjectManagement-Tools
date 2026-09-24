package com.company.projectmanagement.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 已登录用户修改自己密码的请求。 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 256) String currentPassword,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {}
