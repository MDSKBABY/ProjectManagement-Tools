package com.company.projectmanagement.project.web;

import com.company.projectmanagement.project.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 创建项目输入；负责人固定为当前用户，不能由客户端冒充指定。 */
public record CreateProjectRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{1,63}")
        String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String customerName,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
        @Size(max = 5000) String description) {

    public CreateProjectRequest {
        code = trimToNull(code);
        name = trimToNull(name);
        customerName = trimToNull(customerName);
        description = trimToNull(description);
        tags = tags == null ? null : tags.stream().map(CreateProjectRequest::trimToNull).toList();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
