package com.aijiaoxue.controller;

import com.aijiaoxue.audit.AuditLog;
import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.dto.LoginRequest;
import com.aijiaoxue.dto.UserInfo;
import com.aijiaoxue.security.AuthResult;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.CurrentUserHolder;
import com.aijiaoxue.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认证接口（US-01）。
 *
 * <p>契约见 docs/API契约.md 第 1 节：登录、注销、当前用户信息。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录：成功后返回令牌、用户上下文与权限码清单 */
    @PostMapping("/login")
    @AuditLog(action = "登录", targetType = "AUTH")
    public ApiResponse<AuthResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /** 注销：令牌立即失效 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout(CurrentUserHolder.get());
        return ApiResponse.ok();
    }

    /** 当前登录用户信息与权限码 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        CurrentUser cu = CurrentUserHolder.require();
        UserInfo info = authService.me(cu);
        List<String> permissions = authService.permissionsOf(cu);
        return ApiResponse.ok(Map.of("user", info, "permissions", permissions));
    }
}
