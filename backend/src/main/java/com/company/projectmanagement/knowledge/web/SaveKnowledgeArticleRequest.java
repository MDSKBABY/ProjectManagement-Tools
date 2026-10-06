package com.company.projectmanagement.knowledge.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 创建或修订技术知识草稿；正式提交时由业务层校验附件。 */
public record SaveKnowledgeArticleRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 500) String scenario,
        @NotBlank @Size(max = 10000) String symptom,
        @NotBlank @Size(max = 10000) String cause,
        @NotBlank @Size(max = 20000) String solution,
        @Size(max = 5000) String applicableConditions,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
        @Size(max = 10) List<@Positive Long> attachmentIds) {
}
