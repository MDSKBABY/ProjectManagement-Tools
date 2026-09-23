package com.company.projectmanagement.project.web;

import com.company.projectmanagement.project.domain.ProjectStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ProjectResponse(
        Long id,
        String code,
        String name,
        String customerName,
        String description,
        ProjectStatus status,
        OwnerResponse owner,
        LocalDate startDate,
        LocalDate endDate,
        List<String> tags,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record OwnerResponse(Long id, String displayName) {
    }
}
