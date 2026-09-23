package com.company.projectmanagement.project.web;

import com.company.projectmanagement.project.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 全量更新项目的可编辑字段；项目编码和负责人不在此接口中变更。 */
public record UpdateProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String customerName,
        @NotNull ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
        @Size(max = 5000) String description) {

    public UpdateProjectRequest {
        name = trimToNull(name);
        customerName = trimToNull(customerName);
        description = trimToNull(description);
        tags = tags == null ? null : tags.stream().map(UpdateProjectRequest::trimToNull).toList();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
