package com.company.projectmanagement.server.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 凭据只允许通过独立接口提交，不会进入服务器档案响应。 */
public record SaveServerCredentialRequest(
        @NotBlank @Size(max = 200) String username,
        @NotBlank @Size(max = 4096) String password) {
}
