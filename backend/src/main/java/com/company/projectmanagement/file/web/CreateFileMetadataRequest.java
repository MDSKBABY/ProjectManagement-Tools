package com.company.projectmanagement.file.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 预留文件元数据；原始文件名仅作展示，不能包含任何路径分隔符。 */
public record CreateFileMetadataRequest(
        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "^[^/\\\\\\x00]+$", message = "文件名不能包含路径分隔符")
        String originalName,
        @Size(max = 255) String mediaType,
        @NotNull @Min(0) @Max(2_147_483_648L) Long sizeBytes,
        @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "SHA-256 格式不正确") String sha256,
        String fileGroupId) {
}
