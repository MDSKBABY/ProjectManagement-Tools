package com.company.projectmanagement.record.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.environment.domain.EnvironmentFingerprint;
import com.company.projectmanagement.environment.mapper.EnvironmentFingerprintMapper;
import com.company.projectmanagement.environment.web.EnvironmentFingerprintResponse;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.record.domain.DeploymentRecord;
import com.company.projectmanagement.record.domain.DeploymentResult;
import com.company.projectmanagement.record.mapper.DeploymentRecordMapper;
import com.company.projectmanagement.record.web.CreateDeploymentRecordRequest;
import com.company.projectmanagement.record.web.DeploymentRecordResponse;
import com.company.projectmanagement.record.web.DeploymentRecordSummaryResponse;
import com.company.projectmanagement.record.web.SimilarDeploymentResponse;
import com.company.projectmanagement.server.domain.ServerRecord;
import com.company.projectmanagement.server.mapper.ServerRecordMapper;
import com.company.projectmanagement.server.web.ServerResponse;
import com.company.projectmanagement.solution.domain.DeploymentSolution;
import com.company.projectmanagement.solution.mapper.DeploymentSolutionMapper;
import com.company.projectmanagement.solution.web.DeploymentSolutionResponse;
import com.company.projectmanagement.solution.web.DeploymentSolutionStepResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 在一个事务中写入执行人、来源关联和三份不可变快照。 */
@Service
public class DeploymentRecordService {
    private static final int MAX_SIMILARITY_CANDIDATES = 500;
    private final DeploymentRecordMapper mapper;
    private final ServerRecordMapper serverMapper;
    private final DeploymentSolutionMapper solutionMapper;
    private final EnvironmentFingerprintMapper fingerprintMapper;
    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final ObjectMapper objectMapper;

    public DeploymentRecordService(
            DeploymentRecordMapper mapper,
            ServerRecordMapper serverMapper,
            DeploymentSolutionMapper solutionMapper,
            EnvironmentFingerprintMapper fingerprintMapper,
            ProjectMapper projectMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.serverMapper = serverMapper;
        this.solutionMapper = solutionMapper;
        this.fingerprintMapper = fingerprintMapper;
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeploymentRecordResponse create(
            Long projectId, CreateDeploymentRecordRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        ServerRecord server = serverMapper.selectById(projectId, request.serverId());
        if (server == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVER_NOT_FOUND", "服务器不存在");
        }
        DeploymentSolution solution = solutionMapper.selectById(projectId, request.solutionId());
        if (solution == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DEPLOYMENT_SOLUTION_NOT_FOUND", "部署方案不存在");
        }
        EnvironmentFingerprint fingerprint = fingerprintMapper.selectById(
                projectId, solution.getEnvironmentFingerprintId());
        if (fingerprint == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_FINGERPRINT_NOT_FOUND", "环境指纹不存在");
        }
        if (request.result() == DeploymentResult.FAILED
                && !StringUtils.hasText(request.exceptionNotes())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "DEPLOYMENT_RECORD_EXCEPTION_REQUIRED", "失败记录必须填写异常说明");
        }

        List<DeploymentSolutionStepResponse> steps = solutionMapper.selectSteps(solution.getId())
                .stream().map(DeploymentSolutionStepResponse::from).toList();
        DeploymentRecord record = new DeploymentRecord();
        record.setProjectId(projectId);
        record.setServerId(server.getId());
        record.setSolutionId(solution.getId());
        record.setEnvironmentFingerprintId(fingerprint.getId());
        record.setResult(request.result());
        record.setExecutedBy(actor.user().getId());
        record.setExecutedAt(request.executedAt());
        record.setExceptionNotes(normalizeNullable(request.exceptionNotes()));
        record.setNotes(normalizeNullable(request.notes()));
        record.setServerSnapshotJson(json(ServerResponse.from(server)));
        record.setEnvironmentSnapshotJson(json(
                EnvironmentFingerprintResponse.from(fingerprint, objectMapper)));
        record.setSolutionSnapshotJson(json(
                DeploymentSolutionResponse.from(solution, steps)));
        record.setCreatedBy(actor.user().getId());
        mapper.insert(record);
        mapper.recordAudit(actor.user().getId(), "DEPLOYMENT_RECORD_CREATED",
                record.getId().toString(), projectId);
        return DeploymentRecordResponse.from(
                mapper.selectById(projectId, record.getId()), objectMapper);
    }

    public PageResponse<DeploymentRecordSummaryResponse> list(
            Long projectId, int page, int pageSize, DeploymentResult result, Long serverId,
            Boolean baseline, OffsetDateTime executedFrom, OffsetDateTime executedTo,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        validateTimeRange(executedFrom, executedTo);
        long total = mapper.count(projectId, result, serverId, baseline, executedFrom, executedTo);
        List<DeploymentRecordSummaryResponse> data = mapper.selectPage(
                        projectId, result, serverId, baseline, executedFrom, executedTo,
                        (page - 1) * pageSize, pageSize)
                .stream().map(DeploymentRecordSummaryResponse::from).toList();
        return PageResponse.of(data, page, pageSize, total);
    }

    public DeploymentRecordResponse detail(
            Long projectId, Long recordId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        return DeploymentRecordResponse.from(requireRecord(projectId, recordId), objectMapper);
    }

    @Transactional
    public DeploymentRecordResponse updateBaseline(
            Long projectId, Long recordId, boolean baseline, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        DeploymentRecord record = requireRecord(projectId, recordId);
        if (baseline && record.getResult() != DeploymentResult.SUCCESS) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "DEPLOYMENT_BASELINE_REQUIRES_SUCCESS", "只有成功的部署记录才能设为基线");
        }
        mapper.updateBaseline(projectId, recordId, baseline);
        mapper.recordAudit(actor.user().getId(), baseline
                        ? "DEPLOYMENT_BASELINE_ENABLED" : "DEPLOYMENT_BASELINE_DISABLED",
                recordId.toString(), projectId);
        return DeploymentRecordResponse.from(mapper.selectById(projectId, recordId), objectMapper);
    }

    public List<SimilarDeploymentResponse> similar(
            Long projectId, Long fingerprintId, int limit, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        EnvironmentFingerprint fingerprint = fingerprintMapper.selectById(projectId, fingerprintId);
        if (fingerprint == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "ENVIRONMENT_FINGERPRINT_NOT_FOUND", "环境指纹不存在");
        }
        JsonNode target = objectMapper.valueToTree(
                EnvironmentFingerprintResponse.from(fingerprint, objectMapper));
        return mapper.selectBaselineCandidates(projectId, MAX_SIMILARITY_CANDIDATES).stream()
                .map(record -> score(record, target))
                .sorted(Comparator.comparingInt(SimilarDeploymentResponse::score).reversed()
                        .thenComparing(item -> item.record().executedAt(), Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    private SimilarDeploymentResponse score(DeploymentRecord record, JsonNode target) {
        JsonNode candidate;
        try {
            candidate = objectMapper.readTree(record.getEnvironmentSnapshotJson());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取部署基线的环境快照", exception);
        }
        ScoreDetails details = new ScoreDetails();
        details.compareText("操作系统", target, candidate, "operatingSystem", 15);
        details.compareText("系统版本", target, candidate, "osVersion", 5);
        details.compareText("处理器架构", target, candidate, "architecture", 15);
        details.compareText("运行时", target, candidate, "runtimeName", 6);
        details.compareText("运行时版本", target, candidate, "runtimeVersion", 4);
        details.compareText("数据库", target, candidate, "databaseName", 10);
        details.compareText("数据库版本", target, candidate, "databaseVersion", 5);
        details.compareText("环境类型", target, candidate, "environment", 10);
        details.compareText("网络区域", target, candidate, "networkZone", 5);
        details.compareSet("中间件", target, candidate, "middlewares", 15);
        details.compareSet("标签", target, candidate, "tags", 10);
        return new SimilarDeploymentResponse(DeploymentRecordSummaryResponse.from(record),
                details.score, List.copyOf(details.matched), List.copyOf(details.different));
    }

    private DeploymentRecord requireRecord(Long projectId, Long recordId) {
        DeploymentRecord record = mapper.selectById(projectId, recordId);
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "DEPLOYMENT_RECORD_NOT_FOUND", "部署记录不存在");
        }
        return record;
    }

    private void requireVisibleProject(Long projectId, Actor actor) {
        if (projectMapper.selectVisibleById(projectId, actor.user().getId(), actor.administrator()) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
        }
    }

    private static void validateTimeRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_EXECUTION_TIME_RANGE", "开始时间不能晚于结束时间");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法生成部署记录快照", exception);
        }
    }

    private void requireWritableProject(Long projectId, Actor actor) {
        Project project = projectMapper.selectByIdIncludingDeleted(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
        }
        if (!actor.administrator() && !projectMapper.canWriteFiles(projectId, actor.user().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "无权记录该项目的部署执行");
        }
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

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Actor(AppUser user, boolean administrator) { }

    private static final class ScoreDetails {
        private int score;
        private final List<String> matched = new ArrayList<>();
        private final List<String> different = new ArrayList<>();

        private void compareText(
                String label, JsonNode target, JsonNode candidate, String field, int weight) {
            String targetValue = text(target, field);
            String candidateValue = text(candidate, field);
            if (normalize(targetValue).equals(normalize(candidateValue))) {
                score += weight;
                matched.add(label + ": " + display(targetValue));
            } else {
                different.add(label + ": 当前 " + display(targetValue)
                        + " / 基线 " + display(candidateValue));
            }
        }

        private void compareSet(
                String label, JsonNode target, JsonNode candidate, String field, int weight) {
            Set<String> targetValues = normalizedSet(target.path(field));
            Set<String> candidateValues = normalizedSet(candidate.path(field));
            Set<String> union = new HashSet<>(targetValues);
            union.addAll(candidateValues);
            Set<String> intersection = new HashSet<>(targetValues);
            intersection.retainAll(candidateValues);
            double ratio = union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
            score += (int) Math.round(weight * ratio);
            if (targetValues.equals(candidateValues)) {
                matched.add(label + ": " + (targetValues.isEmpty() ? "未配置" : String.join("、", targetValues)));
            } else {
                different.add(label + ": 相同 " + intersection.size() + " / 合计 " + union.size());
            }
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.path(field);
            return value.isMissingNode() || value.isNull() ? "" : value.asText();
        }

        private static Set<String> normalizedSet(JsonNode values) {
            Set<String> result = new HashSet<>();
            if (values.isArray()) {
                values.forEach(value -> result.add(normalize(value.asText())));
            }
            result.remove("");
            return result;
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }

        private static String display(String value) {
            return StringUtils.hasText(value) ? value.trim() : "未配置";
        }
    }
}
