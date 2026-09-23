package com.company.projectmanagement.solution.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.deployment.mapper.DeploymentAssetMapper;
import com.company.projectmanagement.environment.mapper.EnvironmentFingerprintMapper;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.solution.domain.DeploymentSolution;
import com.company.projectmanagement.solution.domain.DeploymentSolutionStatus;
import com.company.projectmanagement.solution.domain.DeploymentSolutionStep;
import com.company.projectmanagement.solution.mapper.DeploymentSolutionMapper;
import com.company.projectmanagement.solution.web.DeploymentSolutionResponse;
import com.company.projectmanagement.solution.web.DeploymentSolutionStepResponse;
import com.company.projectmanagement.solution.web.DeploymentSolutionSummaryResponse;
import com.company.projectmanagement.solution.web.SaveDeploymentSolutionRequest;
import com.company.projectmanagement.solution.web.SaveDeploymentSolutionStepRequest;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 以聚合方式保存部署方案，并锁定每个步骤使用的资产版本。 */
@Service
public class DeploymentSolutionService {
    private final DeploymentSolutionMapper mapper;
    private final EnvironmentFingerprintMapper fingerprintMapper;
    private final DeploymentAssetMapper assetMapper;
    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;

    public DeploymentSolutionService(
            DeploymentSolutionMapper mapper,
            EnvironmentFingerprintMapper fingerprintMapper,
            DeploymentAssetMapper assetMapper,
            ProjectMapper projectMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper) {
        this.mapper = mapper;
        this.fingerprintMapper = fingerprintMapper;
        this.assetMapper = assetMapper;
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
    }

    public PageResponse<DeploymentSolutionSummaryResponse> list(
            Long projectId, int page, int pageSize, String keyword,
            DeploymentSolutionStatus status, Long fingerprintId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        String normalizedKeyword = normalizeNullable(keyword);
        long total = mapper.count(projectId, normalizedKeyword, status, fingerprintId);
        List<DeploymentSolutionSummaryResponse> data = mapper.selectPage(
                        projectId, normalizedKeyword, status, fingerprintId,
                        (page - 1) * pageSize, pageSize)
                .stream().map(DeploymentSolutionSummaryResponse::from).toList();
        return PageResponse.of(data, page, pageSize, total);
    }

    public DeploymentSolutionResponse detail(
            Long projectId, Long solutionId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        return response(requireSolution(projectId, solutionId));
    }

    @Transactional
    public DeploymentSolutionResponse create(
            Long projectId, SaveDeploymentSolutionRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        validateReferences(projectId, request);
        DeploymentSolution solution = map(request, new DeploymentSolution());
        solution.setProjectId(projectId);
        solution.setCreatedBy(actor.user().getId());
        solution.setUpdatedBy(actor.user().getId());
        mapper.insert(solution);
        insertSteps(solution.getId(), request.steps());
        mapper.recordAudit(actor.user().getId(), "DEPLOYMENT_SOLUTION_CREATED",
                solution.getId().toString(), projectId);
        return response(mapper.selectById(projectId, solution.getId()));
    }

    @Transactional
    public DeploymentSolutionResponse update(
            Long projectId, Long solutionId, SaveDeploymentSolutionRequest request,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        requireSolution(projectId, solutionId);
        validateReferences(projectId, request);
        DeploymentSolution solution = map(request, new DeploymentSolution());
        solution.setId(solutionId);
        solution.setProjectId(projectId);
        solution.setUpdatedBy(actor.user().getId());
        mapper.update(solution);
        mapper.deleteSteps(solutionId);
        insertSteps(solutionId, request.steps());
        mapper.recordAudit(actor.user().getId(), "DEPLOYMENT_SOLUTION_UPDATED",
                solutionId.toString(), projectId);
        return response(mapper.selectById(projectId, solutionId));
    }

    @Transactional
    public void delete(Long projectId, Long solutionId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        requireSolution(projectId, solutionId);
        mapper.softDelete(projectId, solutionId, actor.user().getId());
        mapper.recordAudit(actor.user().getId(), "DEPLOYMENT_SOLUTION_DELETED",
                solutionId.toString(), projectId);
    }

    private void validateReferences(Long projectId, SaveDeploymentSolutionRequest request) {
        if (fingerprintMapper.selectById(projectId, request.fingerprintId()) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_FINGERPRINT_NOT_FOUND",
                    "环境指纹不存在");
        }
        HashSet<Integer> orders = new HashSet<>();
        for (SaveDeploymentSolutionStepRequest step : request.steps()) {
            if (!orders.add(step.stepOrder())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "DEPLOYMENT_SOLUTION_STEP_ORDER_DUPLICATE", "方案步骤顺序不能重复");
            }
            if (assetMapper.selectById(projectId, step.assetId()) == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "DEPLOYMENT_ASSET_NOT_FOUND",
                        "部署资产版本不存在");
            }
        }
    }

    private void insertSteps(Long solutionId, List<SaveDeploymentSolutionStepRequest> requests) {
        requests.stream().sorted(java.util.Comparator.comparingInt(SaveDeploymentSolutionStepRequest::stepOrder))
                .forEach(request -> {
                    DeploymentSolutionStep step = new DeploymentSolutionStep();
                    step.setSolutionId(solutionId);
                    step.setStepOrder(request.stepOrder());
                    step.setTitle(request.title().trim());
                    step.setInstructions(request.instructions().trim());
                    step.setDeploymentAssetId(request.assetId());
                    step.setParametersTemplate(normalizeNullable(request.parametersTemplate()));
                    mapper.insertStep(step);
                });
    }

    private static DeploymentSolution map(
            SaveDeploymentSolutionRequest request, DeploymentSolution solution) {
        solution.setEnvironmentFingerprintId(request.fingerprintId());
        solution.setName(request.name().trim());
        solution.setScenario(request.scenario().trim());
        solution.setArchitectureDescription(request.architectureDescription().trim());
        solution.setPrerequisites(request.prerequisites().trim());
        solution.setRollbackSteps(request.rollbackSteps().trim());
        solution.setRiskNotes(request.riskNotes().trim());
        solution.setStatus(request.status());
        return solution;
    }

    private DeploymentSolutionResponse response(DeploymentSolution solution) {
        List<DeploymentSolutionStepResponse> steps = mapper.selectSteps(solution.getId())
                .stream().map(DeploymentSolutionStepResponse::from).toList();
        return DeploymentSolutionResponse.from(solution, steps);
    }

    private DeploymentSolution requireSolution(Long projectId, Long solutionId) {
        DeploymentSolution solution = mapper.selectById(projectId, solutionId);
        if (solution == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DEPLOYMENT_SOLUTION_NOT_FOUND",
                    "部署方案不存在");
        }
        return solution;
    }

    private void requireVisibleProject(Long projectId, Actor actor) {
        if (projectMapper.selectVisibleById(projectId, actor.user().getId(), actor.administrator()) == null) {
            throw projectNotFound();
        }
    }

    private void requireWritableProject(Long projectId, Actor actor) {
        Project project = projectMapper.selectByIdIncludingDeleted(projectId);
        if (project == null || project.getDeletedAt() != null) throw projectNotFound();
        if (!actor.administrator() && !projectMapper.canWriteFiles(projectId, actor.user().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED",
                    "无权维护该项目的部署方案");
        }
    }

    private Actor requireActor(String username) {
        AppUser user = appUserMapper.selectActiveByUsername(username);
        if (user == null) throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        boolean administrator = identityAccessMapper.selectRoleCodesByUserId(user.getId()).contains("ADMIN");
        return new Actor(user, administrator);
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
    }

    private record Actor(AppUser user, boolean administrator) { }
}
