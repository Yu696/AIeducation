package com.aijiaoxue.controller;

import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.security.DataScopeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公共配置接口（匿名可访问）。
 *
 * <p>前端登录页需要展示系统名称与当前学期，因此该接口不需要登录。
 */
@RestController
@RequestMapping("/api")
public class ConfigController {

    private final DataScopeService dataScopeService;

    public ConfigController(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    /** 前端可读的公共配置 */
    @GetMapping("/config/public")
    public ApiResponse<Map<String, Object>> publicConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("systemName", dataScopeService.getConfig("system.name", "爱教学-教学质量全链路数字化管理平台"));
        data.put("currentSemester", dataScopeService.getConfig("current.semester", "2025-2026-1"));
        data.put("crossDepartment", dataScopeService.isCrossDepartmentEnabled());
        data.put("sprint", "Sprint 1");
        return ApiResponse.ok(data);
    }

    /** 健康检查，便于演示前确认服务已就绪 */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", "aijiaoxue-backend", "sprint", "Sprint 1"));
    }
}
