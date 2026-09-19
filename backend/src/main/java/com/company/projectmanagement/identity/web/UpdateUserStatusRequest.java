package com.company.projectmanagement.identity.web;

import com.company.projectmanagement.identity.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

/** 管理员启用或停用用户的请求；允许的具体状态由业务层再次校验。 */
public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
