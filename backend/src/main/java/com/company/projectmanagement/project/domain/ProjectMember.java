package com.company.projectmanagement.project.domain;

import com.company.projectmanagement.identity.domain.UserStatus;
import java.time.OffsetDateTime;

/** 项目成员查询模型，包含成员展示所需的非敏感用户字段。 */
public class ProjectMember {

    private Long projectId;
    private Long userId;
    private String username;
    private String displayName;
    private UserStatus userStatus;
    private ProjectMemberRole projectRole;
    private OffsetDateTime joinedAt;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }

    public ProjectMemberRole getProjectRole() {
        return projectRole;
    }

    public void setProjectRole(ProjectMemberRole projectRole) {
        this.projectRole = projectRole;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
