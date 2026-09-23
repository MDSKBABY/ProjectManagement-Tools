package com.company.projectmanagement.project.domain;

/** 可加入项目的活动用户，只包含成员选择所需的最小公开字段。 */
public class ProjectMemberCandidate {

    private Long userId;
    private String username;
    private String displayName;

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
}
