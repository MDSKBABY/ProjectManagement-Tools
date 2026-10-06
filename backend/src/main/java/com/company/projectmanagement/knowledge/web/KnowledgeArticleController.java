package com.company.projectmanagement.knowledge.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.knowledge.domain.KnowledgeArticleStatus;
import com.company.projectmanagement.knowledge.service.KnowledgeArticleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目技术知识的草稿、检索、提交和审核接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/knowledge-articles")
public class KnowledgeArticleController {
    private final KnowledgeArticleService service;

    public KnowledgeArticleController(KnowledgeArticleService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<KnowledgeArticleSummaryResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) KnowledgeArticleStatus status,
            @RequestParam(required = false) String tag,
            Principal principal) {
        return service.list(projectId, page, pageSize, keyword, status, tag, principal.getName());
    }

    @GetMapping("/{articleId}")
    public KnowledgeArticleResponse detail(
            @PathVariable Long projectId,
            @PathVariable Long articleId,
            Principal principal) {
        return service.detail(projectId, articleId, principal.getName());
    }

    @PostMapping
    public ResponseEntity<KnowledgeArticleResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveKnowledgeArticleRequest request,
            Principal principal) {
        KnowledgeArticleResponse response = service.create(projectId, request, principal.getName());
        return ResponseEntity.created(URI.create(
                        "/api/v1/projects/%d/knowledge-articles/%d"
                                .formatted(projectId, response.id())))
                .body(response);
    }

    @PutMapping("/{articleId}")
    public KnowledgeArticleResponse update(
            @PathVariable Long projectId,
            @PathVariable Long articleId,
            @Valid @RequestBody SaveKnowledgeArticleRequest request,
            Principal principal) {
        return service.update(projectId, articleId, request, principal.getName());
    }

    @PostMapping("/{articleId}/submit")
    public KnowledgeArticleResponse submit(
            @PathVariable Long projectId,
            @PathVariable Long articleId,
            Principal principal) {
        return service.submit(projectId, articleId, principal.getName());
    }

    @PostMapping("/{articleId}/review")
    public KnowledgeArticleResponse review(
            @PathVariable Long projectId,
            @PathVariable Long articleId,
            @Valid @RequestBody ReviewKnowledgeArticleRequest request,
            Principal principal) {
        return service.review(projectId, articleId, request, principal.getName());
    }

    @DeleteMapping("/{articleId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long articleId,
            Principal principal) {
        service.delete(projectId, articleId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
