package com.company.projectmanagement.identity.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.identity.domain.UserStatus;
import com.company.projectmanagement.identity.service.UserAdministrationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理入口；路径级的 user:manage 校验由安全配置统一执行。
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdministrationController {

    private final UserAdministrationService userAdministrationService;

    public UserAdministrationController(UserAdministrationService userAdministrationService) {
        this.userAdministrationService = userAdministrationService;
    }

    /** 分页查询用户，页码从 1 开始。 */
    @GetMapping
    PageResponse<UserSummaryResponse> listUsers(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status) {
        return userAdministrationService.listUsers(page, pageSize, keyword, status);
    }

    /** 创建用户并返回 201 和新资源地址。 */
    @PostMapping
    ResponseEntity<UserSummaryResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication) {
        UserSummaryResponse created = userAdministrationService.createUser(
                request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.id()))
                .body(created);
    }

    /** 仅修改用户启用/停用状态，不承担锁定和解锁流程。 */
    @PatchMapping("/{id}/status")
    UserSummaryResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            Authentication authentication) {
        return userAdministrationService.updateStatus(id, request, authentication.getName());
    }
}
