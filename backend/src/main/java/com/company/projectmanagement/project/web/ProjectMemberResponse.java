package com.company.projectmanagement.project.web;

import com.company.projectmanagement.identity.domain.UserStatus;
import com.company.projectmanagement.project.domain.ProjectMember;
import com.company.projectmanagement.project.domain.ProjectMemberRole;
import java.time.OffsetDateTime;

/** 项目成员响应不包含手机号、邮箱或认证信息。 */
public record ProjectMemberResponse(
        Long userId,
        String username,
        String displayName,
        UserStatus status,
        ProjectMemberRole role,
        OffsetDateTime joinedAt) {

    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getUserId(),
                member.getUsername(),
                member.getDisplayName(),
                member.getUserStatus(),
                member.getProjectRole(),
                member.getJoinedAt());
    }
}
