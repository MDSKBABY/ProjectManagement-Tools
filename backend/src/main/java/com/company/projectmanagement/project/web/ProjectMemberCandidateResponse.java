package com.company.projectmanagement.project.web;

import com.company.projectmanagement.project.domain.ProjectMemberCandidate;

/** 成员候选响应刻意不暴露联系方式和账号安全信息。 */
public record ProjectMemberCandidateResponse(
        Long userId,
        String username,
        String displayName) {

    public static ProjectMemberCandidateResponse from(ProjectMemberCandidate candidate) {
        return new ProjectMemberCandidateResponse(
                candidate.getUserId(), candidate.getUsername(), candidate.getDisplayName());
    }
}
