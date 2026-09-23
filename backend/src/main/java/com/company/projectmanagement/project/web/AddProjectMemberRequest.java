package com.company.projectmanagement.project.web;

import com.company.projectmanagement.project.domain.ProjectMemberRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 添加项目成员请求；OWNER 角色由业务层明确拒绝。 */
public record AddProjectMemberRequest(
        @NotNull @Positive Long userId,
        @NotNull ProjectMemberRole role) {
}
