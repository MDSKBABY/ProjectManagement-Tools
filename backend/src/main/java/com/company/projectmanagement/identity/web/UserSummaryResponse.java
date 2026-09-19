package com.company.projectmanagement.identity.web;

import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.domain.UserStatus;
import java.time.OffsetDateTime;

public record UserSummaryResponse(
        Long id,
        String username,
        String displayName,
        String email,
        String mobile,
        UserStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static UserSummaryResponse from(AppUser user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getMobile(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
