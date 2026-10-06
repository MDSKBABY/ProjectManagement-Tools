package com.company.projectmanagement.knowledge.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.file.domain.FileAsset;
import com.company.projectmanagement.file.domain.FileStatus;
import com.company.projectmanagement.file.mapper.FileAssetMapper;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.knowledge.domain.KnowledgeArticle;
import com.company.projectmanagement.knowledge.domain.KnowledgeArticleStatus;
import com.company.projectmanagement.knowledge.mapper.KnowledgeArticleMapper;
import com.company.projectmanagement.knowledge.web.KnowledgeArticleResponse;
import com.company.projectmanagement.knowledge.web.KnowledgeArticleSummaryResponse;
import com.company.projectmanagement.knowledge.web.KnowledgeAttachmentResponse;
import com.company.projectmanagement.knowledge.web.ReviewKnowledgeArticleRequest;
import com.company.projectmanagement.knowledge.web.SaveKnowledgeArticleRequest;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 技术知识服务，守住项目隔离、草稿归属、审核角色和附件完整性。 */
@Service
public class KnowledgeArticleService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final KnowledgeArticleMapper mapper;
    private final FileAssetMapper fileAssetMapper;
    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final ObjectMapper objectMapper;

    public KnowledgeArticleService(
            KnowledgeArticleMapper mapper,
            FileAssetMapper fileAssetMapper,
            ProjectMapper projectMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.fileAssetMapper = fileAssetMapper;
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.objectMapper = objectMapper;
    }

    public PageResponse<KnowledgeArticleSummaryResponse> list(
            Long projectId, int page, int pageSize, String keyword,
            KnowledgeArticleStatus status, String tag, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        String normalizedKeyword = normalizeNullable(keyword);
        String normalizedTag = normalizeNullable(tag);
        long total = mapper.count(projectId, normalizedKeyword, status, normalizedTag);
        List<KnowledgeArticleSummaryResponse> data = mapper.selectPage(
                        projectId, normalizedKeyword, status, normalizedTag,
                        (page - 1) * pageSize, pageSize)
                .stream()
                .map(article -> KnowledgeArticleSummaryResponse.from(article, readTags(article)))
                .toList();
        return PageResponse.of(data, page, pageSize, total);
    }

    public KnowledgeArticleResponse detail(
            Long projectId, Long articleId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        return response(projectId, requireArticle(projectId, articleId));
    }

    @Transactional
    public KnowledgeArticleResponse create(
            Long projectId, SaveKnowledgeArticleRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        List<Long> attachmentIds = validateAttachments(projectId, request.attachmentIds());
        KnowledgeArticle article = map(request, new KnowledgeArticle());
        article.setProjectId(projectId);
        article.setStatus(KnowledgeArticleStatus.DRAFT);
        article.setCreatedBy(actor.user().getId());
        article.setUpdatedBy(actor.user().getId());
        mapper.insert(article);
        replaceAttachments(projectId, article.getId(), attachmentIds, actor.user().getId());
        mapper.recordAudit(actor.user().getId(), "KNOWLEDGE_ARTICLE_CREATED",
                article.getId().toString(), projectId);
        return response(projectId, requireArticle(projectId, article.getId()));
    }

    @Transactional
    public KnowledgeArticleResponse update(
            Long projectId, Long articleId, SaveKnowledgeArticleRequest request,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        KnowledgeArticle current = requireArticle(projectId, articleId);
        requireEditor(projectId, current, actor);
        if (current.getStatus() != KnowledgeArticleStatus.DRAFT
                && current.getStatus() != KnowledgeArticleStatus.REJECTED) {
            throw locked();
        }
        List<Long> attachmentIds = validateAttachments(projectId, request.attachmentIds());
        KnowledgeArticle article = map(request, current);
        article.setUpdatedBy(actor.user().getId());
        if (mapper.update(article) == 0) {
            throw locked();
        }
        replaceAttachments(projectId, articleId, attachmentIds, actor.user().getId());
        mapper.recordAudit(actor.user().getId(), "KNOWLEDGE_ARTICLE_UPDATED",
                articleId.toString(), projectId);
        return response(projectId, requireArticle(projectId, articleId));
    }

    @Transactional
    public KnowledgeArticleResponse submit(
            Long projectId, Long articleId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        KnowledgeArticle article = requireArticle(projectId, articleId);
        requireEditor(projectId, article, actor);
        if (article.getStatus() != KnowledgeArticleStatus.DRAFT
                && article.getStatus() != KnowledgeArticleStatus.REJECTED) {
            throw locked();
        }
        if (mapper.selectAttachments(projectId, articleId).isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "KNOWLEDGE_ATTACHMENT_REQUIRED",
                    "正式提交技术知识前至少需要一个可用附件");
        }
        if (mapper.submit(projectId, articleId, actor.user().getId()) == 0) {
            throw locked();
        }
        mapper.recordAudit(actor.user().getId(), "KNOWLEDGE_ARTICLE_SUBMITTED",
                articleId.toString(), projectId);
        return response(projectId, requireArticle(projectId, articleId));
    }

    @Transactional
    public KnowledgeArticleResponse review(
            Long projectId, Long articleId, ReviewKnowledgeArticleRequest request,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        if (!actor.administrator()
                && !projectMapper.canManage(projectId, actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "KNOWLEDGE_REVIEW_ACCESS_DENIED",
                    "只有项目负责人、项目经理或管理员可以审核技术知识");
        }
        KnowledgeArticle article = requireArticle(projectId, articleId);
        if (article.getStatus() != KnowledgeArticleStatus.PENDING_REVIEW) {
            throw locked();
        }
        if (request.status() != KnowledgeArticleStatus.APPROVED
                && request.status() != KnowledgeArticleStatus.REJECTED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "KNOWLEDGE_REVIEW_STATUS_INVALID",
                    "审核结果只能是通过或驳回");
        }
        String comment = normalizeNullable(request.comment());
        if (request.status() == KnowledgeArticleStatus.REJECTED && comment == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "KNOWLEDGE_REVIEW_COMMENT_REQUIRED",
                    "驳回技术知识时必须填写原因");
        }
        if (mapper.review(projectId, articleId, request.status(), comment,
                actor.user().getId()) == 0) {
            throw locked();
        }
        mapper.recordAudit(actor.user().getId(),
                request.status() == KnowledgeArticleStatus.APPROVED
                        ? "KNOWLEDGE_ARTICLE_APPROVED"
                        : "KNOWLEDGE_ARTICLE_REJECTED",
                articleId.toString(), projectId);
        return response(projectId, requireArticle(projectId, articleId));
    }

    @Transactional
    public void delete(Long projectId, Long articleId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        KnowledgeArticle article = requireArticle(projectId, articleId);
        requireEditor(projectId, article, actor);
        if (article.getStatus() != KnowledgeArticleStatus.DRAFT
                && article.getStatus() != KnowledgeArticleStatus.REJECTED) {
            throw locked();
        }
        mapper.softDelete(projectId, articleId, actor.user().getId());
        mapper.deleteAttachments(projectId, articleId);
        mapper.recordAudit(actor.user().getId(), "KNOWLEDGE_ARTICLE_DELETED",
                articleId.toString(), projectId);
    }

    private KnowledgeArticle map(
            SaveKnowledgeArticleRequest request, KnowledgeArticle article) {
        article.setTitle(request.title().trim());
        article.setScenario(request.scenario().trim());
        article.setSymptom(request.symptom().trim());
        article.setCause(request.cause().trim());
        article.setSolution(request.solution().trim());
        article.setApplicableConditions(normalizeNullable(request.applicableConditions()));
        article.setTagsJson(writeTags(normalizeTags(request.tags())));
        return article;
    }

    private List<Long> validateAttachments(Long projectId, List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = List.copyOf(new LinkedHashSet<>(requestedIds));
        for (Long fileId : ids) {
            FileAsset file = fileAssetMapper.selectProjectFile(projectId, fileId);
            if (file == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "文件不存在");
            }
            if (file.getStatus() != FileStatus.AVAILABLE) {
                throw new ApiException(
                        HttpStatus.CONFLICT, "FILE_NOT_AVAILABLE", "文件尚未上传完成");
            }
        }
        return ids;
    }

    private void replaceAttachments(
            Long projectId, Long articleId, List<Long> attachmentIds, Long actorUserId) {
        mapper.deleteAttachments(projectId, articleId);
        attachmentIds.forEach(fileId -> mapper.linkAttachment(
                fileId, projectId, articleId, actorUserId));
    }

    private KnowledgeArticleResponse response(Long projectId, KnowledgeArticle article) {
        List<KnowledgeAttachmentResponse> attachments = mapper
                .selectAttachments(projectId, article.getId())
                .stream().map(KnowledgeAttachmentResponse::from).toList();
        return KnowledgeArticleResponse.from(article, readTags(article), attachments);
    }

    private void requireEditor(Long projectId, KnowledgeArticle article, Actor actor) {
        if (!actor.administrator()
                && !article.getCreatedBy().equals(actor.user().getId())
                && !projectMapper.canManage(projectId, actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "KNOWLEDGE_ARTICLE_ACCESS_DENIED",
                    "只能维护自己创建的技术知识草稿");
        }
    }

    private void requireVisibleProject(Long projectId, Actor actor) {
        if (projectMapper.selectVisibleById(
                projectId, actor.user().getId(), actor.administrator()) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
        }
    }

    private void requireWritableProject(Long projectId, Actor actor) {
        requireVisibleProject(projectId, actor);
        if (!actor.administrator()
                && !projectMapper.canWriteFiles(projectId, actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "KNOWLEDGE_ARTICLE_ACCESS_DENIED",
                    "无权维护该项目的技术知识");
        }
    }

    private KnowledgeArticle requireArticle(Long projectId, Long articleId) {
        KnowledgeArticle article = mapper.selectById(projectId, articleId);
        if (article == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "KNOWLEDGE_ARTICLE_NOT_FOUND",
                    "技术知识不存在");
        }
        return article;
    }

    private Actor requireActor(String username) {
        AppUser user = appUserMapper.selectActiveByUsername(username);
        if (user == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        }
        boolean administrator = identityAccessMapper.selectRoleCodesByUserId(user.getId())
                .contains("ADMIN");
        return new Actor(user, administrator);
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        tags.stream().map(String::trim).filter(StringUtils::hasText).forEach(unique::add);
        return List.copyOf(unique);
    }

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化技术知识标签", exception);
        }
    }

    private List<String> readTags(KnowledgeArticle article) {
        try {
            return objectMapper.readValue(
                    article.getTagsJson() == null ? "[]" : article.getTagsJson(), STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取技术知识标签", exception);
        }
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ApiException locked() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "KNOWLEDGE_ARTICLE_LOCKED",
                "当前审核状态不允许修改该技术知识");
    }

    private record Actor(AppUser user, boolean administrator) { }
}
