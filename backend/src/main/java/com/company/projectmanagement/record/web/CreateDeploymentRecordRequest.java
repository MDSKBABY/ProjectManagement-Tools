package com.company.projectmanagement.record.web;

import com.company.projectmanagement.record.domain.DeploymentResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 创建部署记录时，执行人由服务端从登录会话取得。 */
public record CreateDeploymentRecordRequest(
        @NotNull Long serverId,
        @NotNull Long solutionId,
        @NotNull @PastOrPresent OffsetDateTime executedAt,
        @NotNull DeploymentResult result,
        @Size(max = 5000) String exceptionNotes,
        @Size(max = 5000) String notes) {
}
