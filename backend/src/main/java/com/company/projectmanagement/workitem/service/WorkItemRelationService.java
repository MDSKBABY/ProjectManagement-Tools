package com.company.projectmanagement.workitem.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.workitem.domain.WorkItem;
import com.company.projectmanagement.workitem.domain.WorkItemRelation;
import com.company.projectmanagement.workitem.domain.WorkItemRelationType;
import com.company.projectmanagement.workitem.mapper.WorkItemMapper;
import com.company.projectmanagement.workitem.mapper.WorkItemRelationMapper;
import com.company.projectmanagement.workitem.web.CreateWorkItemRelationRequest;
import com.company.projectmanagement.workitem.web.WorkItemRelationResponse;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 工作项关系业务层，统一守住方向语义、项目边界、去重和无环约束。 */
@Service
public class WorkItemRelationService {

    private final WorkItemRelationMapper relationMapper;
    private final WorkItemMapper workItemMapper;
    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;

    public WorkItemRelationService(
            WorkItemRelationMapper relationMapper,
            WorkItemMapper workItemMapper,
            ProjectMapper projectMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper) {
        this.relationMapper = relationMapper;
        this.workItemMapper = workItemMapper;
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
    }

    /** 关系列表可按任一端工作项或关系类型筛选。 */
    public PageResponse<WorkItemRelationResponse> list(
            Long projectId,
            int page,
            int pageSize,
            Long workItemId,
            WorkItemRelationType type,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireReadableProject(projectId, actor);
        if (workItemId != null) {
            requireWorkItem(projectId, workItemId);
        }
        long total = relationMapper.count(projectId, workItemId, type);
        List<WorkItemRelationResponse> data = relationMapper.selectPage(
                        projectId, workItemId, type, (page - 1) * pageSize, pageSize)
                .stream()
                .map(WorkItemRelationResponse::from)
                .toList();
        return PageResponse.of(data, page, pageSize, total);
    }

    /**
     * 一个项目的关系创建使用项目行锁串行化，使判重与判环结果在并发下仍然有效。
     */
    @Transactional
    public WorkItemRelationResponse create(
            Long projectId,
            CreateWorkItemRelationRequest request,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireManageableProject(projectId, actor);
        Long sourceId = request.sourceWorkItemId();
        Long targetId = request.targetWorkItemId();
        if (sourceId.equals(targetId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "WORK_ITEM_RELATION_SELF_NOT_ALLOWED",
                    "工作项不能与自身建立关系");
        }

        relationMapper.lockProject(projectId);
        lockEndpoints(projectId, sourceId, targetId);
        requireWorkItem(projectId, sourceId);
        requireWorkItem(projectId, targetId);
        boolean hierarchy = request.type() == WorkItemRelationType.PARENT_CHILD;
        if (relationMapper.existsGraphEdge(projectId, sourceId, targetId, hierarchy)) {
            throw relationExists();
        }
        if (relationMapper.wouldCreateCycle(projectId, sourceId, targetId, hierarchy)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WORK_ITEM_RELATION_CYCLE",
                    "该关系会产生循环依赖");
        }

        WorkItemRelation relation = new WorkItemRelation();
        relation.setProjectId(projectId);
        relation.setSourceWorkItemId(sourceId);
        relation.setTargetWorkItemId(targetId);
        relation.setType(request.type());
        relation.setCreatedBy(actor.user().getId());
        try {
            relationMapper.insert(relation);
        } catch (DuplicateKeyException exception) {
            // 预检改善错误语义，唯一索引仍负责并发下的最终保护。
            throw relationExists();
        }
        relationMapper.recordAudit(
                actor.user().getId(),
                "WORK_ITEM_RELATION_CREATED",
                relation.getId().toString(),
                projectId,
                relation.getType(),
                sourceId,
                targetId);
        return WorkItemRelationResponse.from(requireRelation(projectId, relation.getId()));
    }

    /** 删除关系为幂等软删除，保留创建与删除审计。 */
    @Transactional
    public void delete(Long projectId, Long relationId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireManageableProject(projectId, actor);
        relationMapper.lockProject(projectId);
        WorkItemRelation relation = relationMapper.selectByIdIncludingDeleted(projectId, relationId);
        if (relation == null) {
            throw relationNotFound();
        }
        if (relation.getDeletedAt() != null) {
            return;
        }
        if (relationMapper.softDelete(projectId, relationId, actor.user().getId()) == 0) {
            return;
        }
        relationMapper.recordAudit(
                actor.user().getId(),
                "WORK_ITEM_RELATION_DELETED",
                relationId.toString(),
                projectId,
                relation.getType(),
                relation.getSourceWorkItemId(),
                relation.getTargetWorkItemId());
    }

    private void lockEndpoints(Long projectId, Long sourceId, Long targetId) {
        Long first = Math.min(sourceId, targetId);
        Long second = Math.max(sourceId, targetId);
        if (relationMapper.lockActiveWorkItem(projectId, first) == null
                || relationMapper.lockActiveWorkItem(projectId, second) == null) {
            throw workItemNotFound();
        }
    }

    private Project requireReadableProject(Long projectId, Actor actor) {
        Project project = projectMapper.selectVisibleById(
                projectId, actor.user().getId(), actor.administrator());
        if (project == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
        }
        return project;
    }

    private void requireManageableProject(Long projectId, Actor actor) {
        requireReadableProject(projectId, actor);
        if (!actor.administrator()
                && !projectMapper.canManage(projectId, actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "WORK_ITEM_ACCESS_DENIED",
                    "无权维护工作项关系");
        }
    }

    private WorkItem requireWorkItem(Long projectId, Long workItemId) {
        WorkItem item = workItemMapper.selectById(projectId, workItemId);
        if (item == null) {
            throw workItemNotFound();
        }
        return item;
    }

    private WorkItemRelation requireRelation(Long projectId, Long relationId) {
        WorkItemRelation relation = relationMapper.selectById(projectId, relationId);
        if (relation == null) {
            throw relationNotFound();
        }
        return relation;
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

    private static ApiException relationExists() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "WORK_ITEM_RELATION_EXISTS",
                "同方向的工作项关系已存在");
    }

    private static ApiException relationNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "WORK_ITEM_RELATION_NOT_FOUND",
                "工作项关系不存在");
    }

    private static ApiException workItemNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "工作项不存在");
    }

    private record Actor(AppUser user, boolean administrator) {
    }
}
