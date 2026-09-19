package com.aijiaoxue.controller;

import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.security.CurrentUserHolder;
import com.aijiaoxue.security.PermissionCodes;
import com.aijiaoxue.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台接口（US-02 / US-03 / US-04）。
 *
 * <p>契约见 docs/API契约.md 第 8 节。四个角色工作台统一返回
 * {@code {cards, charts, ...角色专属数据}}，前端用同一套渲染逻辑处理。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 按当前登录角色返回对应工作台数据 */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('" + PermissionCodes.DASHBOARD_READ + "')")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(dashboardService.summary(CurrentUserHolder.require()));
    }

    /** 教研室主任工作台 */
    @GetMapping("/director")
    @PreAuthorize("hasAuthority('" + PermissionCodes.DASHBOARD_DIRECTOR + "')")
    public ApiResponse<Map<String, Object>> director() {
        return ApiResponse.ok(dashboardService.director(CurrentUserHolder.require()));
    }

    /** 教师工作台 */
    @GetMapping("/teacher")
    @PreAuthorize("hasAuthority('" + PermissionCodes.DASHBOARD_TEACHER + "')")
    public ApiResponse<Map<String, Object>> teacher() {
        return ApiResponse.ok(dashboardService.teacher(CurrentUserHolder.require()));
    }

    /** 教学督导工作台 */
    @GetMapping("/supervisor")
    @PreAuthorize("hasAuthority('" + PermissionCodes.DASHBOARD_SUPERVISOR + "')")
    public ApiResponse<Map<String, Object>> supervisor() {
        return ApiResponse.ok(dashboardService.supervisor(CurrentUserHolder.require()));
    }

    /** 系统管理员工作台 */
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('" + PermissionCodes.DASHBOARD_ADMIN + "')")
    public ApiResponse<Map<String, Object>> admin() {
        return ApiResponse.ok(dashboardService.admin(CurrentUserHolder.require()));
    }
}
