package com.company.projectmanagement.project.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.domain.ProjectMember;
import com.company.projectmanagement.project.domain.ProjectMemberRole;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.project.mapper.ProjectMemberMapper;
import com.company.projectmanagement.project.web.AddProjectMemberRequest;
import com.company.projectmanagement.project.web.ProjectMemberResponse;
import com.company.projectmanagement.project.web.ProjectMemberCandidateResponse;
import com.company.projectmanagement.project.web.UpdateProjectMemberRoleRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 项目成员业务层，集中保护 OWNER 关系和项目级授权。 */
@Service
public class ProjectMemberService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;

    public ProjectMemberService(
            ProjectMapper projectMapper,
            ProjectMemberMapper projectMemberMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
    }

    /** 成员列表沿用项目详情的可见性规则，无权访问时隐藏项目存在性。 */
    public PageResponse<ProjectMemberResponse> list(
            Long projectId,
            int page,
            int pageSize,
            String keyword,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        if (projectMapper.selectVisibleById(
                projectId, actor.user().getId(), actor.administrator()) == null) {
            throw projectNotFound();
        }
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long totalItems = projectMemberMapper.countByProject(projectId, normalizedKeyword);
        var members = projectMemberMapper.selectByProject(
                        projectId, normalizedKeyword, (page - 1) * pageSize, pageSize)
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();
        return PageResponse.of(members, page, pageSize, totalItems);
    }

    /** 仅成员管理员可搜索活动且尚未加入本项目的用户。 */
    public PageResponse<ProjectMemberCandidateResponse> listCandidates(
            Long projectId,
            int page,
            int pageSize,
            String keyword,
            String actorUsername) {
        requireManager(projectId, actorUsername);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long totalItems = projectMemberMapper.countCandidates(projectId, normalizedKeyword);
        var candidates = projectMemberMapper.selectCandidates(
                        projectId, normalizedKeyword, (page - 1) * pageSize, pageSize)
                .stream()
                .map(ProjectMemberCandidateResponse::from)
                .toList();
        return PageResponse.of(candidates, page, pageSize, totalItems);
    }

    /** 添加成员和审计记录必须在同一事务内提交。 */
    @Transactional
    public ProjectMemberResponse add(
            Long projectId,
            AddProjectMemberRequest request,
            String actorUsername) {
        Actor actor = requireManager(projectId, actorUsername);
        rejectOwnerRole(request.role());
        AppUser target = appUserMapper.selectActiveById(request.userId());
        if (target == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROJECT_MEMBER_USER_UNAVAILABLE",
                    "只能添加正常启用的用户");
        }
        if (projectMemberMapper.selectOne(projectId, request.userId()) != null) {
            throw memberExists();
        }
        try {
            projectMemberMapper.insert(
                    projectId, request.userId(), request.role(), actor.user().getId());
        } catch (DuplicateKeyException exception) {
            // 预检查用于友好提示，联合主键负责阻止并发重复添加。
            throw memberExists();
        }
        projectMemberMapper.recordAudit(
                actor.user().getId(),
                "PROJECT_MEMBER_ADDED",
                projectId.toString(),
                request.userId(),
                null,
                request.role().name());
        return ProjectMemberResponse.from(projectMemberMapper.selectOne(projectId, request.userId()));
    }

    /** 普通角色调整不能用于负责人交接，也不会对未变化角色重复审计。 */
    @Transactional
    public ProjectMemberResponse updateRole(
            Long projectId,
            Long userId,
            UpdateProjectMemberRoleRequest request,
            String actorUsername) {
        Actor actor = requireManager(projectId, actorUsername);
        rejectOwnerRole(request.role());
        ProjectMember existing = requireMember(projectId, userId);
        protectOwner(existing);
        if (existing.getProjectRole() == request.role()) {
            return ProjectMemberResponse.from(existing);
        }
        if (projectMemberMapper.updateRole(projectId, userId, request.role()) == 0) {
            throw memberNotFound();
        }
        projectMemberMapper.recordAudit(
                actor.user().getId(),
                "PROJECT_MEMBER_ROLE_CHANGED",
                projectId.toString(),
                userId,
                existing.getProjectRole().name(),
                request.role().name());
        return ProjectMemberResponse.from(projectMemberMapper.selectOne(projectId, userId));
    }

    /** 移除普通成员为幂等操作；OWNER 即使由管理员操作也受到保护。 */
    @Transactional
    public void remove(Long projectId, Long userId, String actorUsername) {
        Actor actor = requireManager(projectId, actorUsername);
        ProjectMember existing = projectMemberMapper.selectOne(projectId, userId);
        if (existing == null) {
            return;
        }
        protectOwner(existing);
        if (projectMemberMapper.deleteNonOwner(projectId, userId) == 0) {
            return;
        }
        projectMemberMapper.recordAudit(
                actor.user().getId(),
                "PROJECT_MEMBER_REMOVED",
                projectId.toString(),
                userId,
                existing.getProjectRole().name(),
                null);
    }

    private Actor requireManager(Long projectId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        Project project = projectMapper.selectByIdIncludingDeleted(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw projectNotFound();
        }
        if (!actor.administrator()
                && !projectMapper.canManage(projectId, actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "无权管理该项目");
        }
        return actor;
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

    private ProjectMember requireMember(Long projectId, Long userId) {
        ProjectMember member = projectMemberMapper.selectOne(projectId, userId);
        if (member == null) {
            throw memberNotFound();
        }
        return member;
    }

    private static void rejectOwnerRole(ProjectMemberRole role) {
        if (role == ProjectMemberRole.OWNER) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROJECT_OWNER_ROLE_RESERVED",
                    "OWNER 角色只能通过负责人交接设置");
        }
    }

    private static void protectOwner(ProjectMember member) {
        if (member.getProjectRole() == ProjectMemberRole.OWNER) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROJECT_OWNER_PROTECTED",
                    "项目负责人不能通过成员管理接口修改或移除");
        }
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
    }

    private static ApiException memberNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "PROJECT_MEMBER_NOT_FOUND", "项目成员不存在");
    }

    private static ApiException memberExists() {
        return new ApiException(
                HttpStatus.CONFLICT, "PROJECT_MEMBER_EXISTS", "用户已经是项目成员");
    }

    private record Actor(AppUser user, boolean administrator) {
    }
}
