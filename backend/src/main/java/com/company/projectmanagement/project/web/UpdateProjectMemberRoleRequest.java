package com.company.projectmanagement.project.web;

import com.company.projectmanagement.project.domain.ProjectMemberRole;
import jakarta.validation.constraints.NotNull;

/** 调整项目成员角色请求；负责人交接不复用此接口。 */
public record UpdateProjectMemberRoleRequest(@NotNull ProjectMemberRole role) {
}
