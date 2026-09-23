package com.company.projectmanagement.project.domain;

/** 项目内角色；OWNER 仅能由项目创建或后续负责人交接流程维护。 */
public enum ProjectMemberRole {
    OWNER,
    MANAGER,
    MEMBER,
    VIEWER
}
