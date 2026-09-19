package com.aijiaoxue.controller;

import com.aijiaoxue.audit.AuditLog;
import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.dto.PasswordResetRequest;
import com.aijiaoxue.dto.RolePermissionRequest;
import com.aijiaoxue.dto.UserSaveRequest;
import com.aijiaoxue.entity.SysAuditLog;
import com.aijiaoxue.entity.SysUser;
import com.aijiaoxue.security.CurrentUserHolder;
import com.aijiaoxue.security.PermissionCodes;
import com.aijiaoxue.security.PermissionService;
import com.aijiaoxue.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户与权限管理接口（US-01）。
 *
 * <p>契约见 docs/API契约.md 第 1 节。所有接口均以权限码做功能级鉴权，
 * 越权访问由 SecurityConfig 统一返回 403。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;
    private final PermissionService permissionService;

    public AdminController(UserService userService, PermissionService permissionService) {
        this.userService = userService;
        this.permissionService = permissionService;
    }

    // ---------------- 用户管理 ----------------

    /** 用户列表分页 */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_READ + "')")
    public ApiResponse<PageResult<Map<String, Object>>> users(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Integer enabled) {
        return ApiResponse.ok(userService.page(page, size, keyword, roleCode, enabled));
    }

    /** 新建用户 */
    @PostMapping("/users")
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_WRITE + "')")
    @AuditLog(action = "新增", targetType = "USER")
    public ApiResponse<SysUser> createUser(@Valid @RequestBody UserSaveRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    /** 修改用户 */
    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_WRITE + "')")
    @AuditLog(action = "修改", targetType = "USER", idParamIndex = 0)
    public ApiResponse<SysUser> updateUser(@PathVariable Long id,
                                           @Valid @RequestBody UserSaveRequest request) {
        return ApiResponse.ok(userService.update(id, request));
    }

    /** 重置口令 */
    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_WRITE + "')")
    @AuditLog(action = "重置口令", targetType = "USER", idParamIndex = 0)
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.getPassword());
        return ApiResponse.ok();
    }

    /** 删除用户 */
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.USER_WRITE + "')")
    @AuditLog(action = "删除", targetType = "USER", idParamIndex = 0)
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id, CurrentUserHolder.require());
        return ApiResponse.ok();
    }

    // ---------------- 角色与权限矩阵 ----------------

    /** 角色清单（含账号数） */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_READ + "')")
    public ApiResponse<List<Map<String, Object>>> roles() {
        return ApiResponse.ok(userService.roles());
    }

    /** 全部权限码（权限矩阵勾选框数据源） */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_READ + "')")
    public ApiResponse<List<Map<String, Object>>> permissions() {
        List<Map<String, Object>> list = new ArrayList<>();
        PermissionCodes.allWithName().forEach((code, name) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("permCode", code);
            m.put("permName", name);
            m.put("module", code.contains(":") ? code.substring(0, code.indexOf(':')) : code);
            list.add(m);
        });
        return ApiResponse.ok(list);
    }

    /** 某角色的权限：effective 为生效值，defaults 为代码内默认值，用于界面提示差异 */
    @GetMapping("/roles/{roleCode}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_READ + "')")
    public ApiResponse<Map<String, Object>> rolePermissions(@PathVariable String roleCode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roleCode", roleCode);
        data.put("effective", permissionService.effectivePermissions(roleCode));
        data.put("defaults", permissionService.defaultPermissions(roleCode));
        data.put("overridden", !permissionService.overriddenPermissions(roleCode).isEmpty());
        return ApiResponse.ok(data);
    }

    /** 保存角色权限（整体覆盖式） */
    @PutMapping("/roles/{roleCode}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_WRITE + "')")
    @AuditLog(action = "修改", targetType = "ROLE_PERMISSION", idParamIndex = 0)
    public ApiResponse<Map<String, Object>> saveRolePermissions(@PathVariable String roleCode,
                                                               @RequestBody RolePermissionRequest request) {
        permissionService.saveRolePermissions(roleCode, request.getPermissions());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roleCode", roleCode);
        data.put("effective", permissionService.effectivePermissions(roleCode));
        return ApiResponse.ok(data);
    }

    /** 重置角色权限为默认矩阵 */
    @DeleteMapping("/roles/{roleCode}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.ROLE_WRITE + "')")
    @AuditLog(action = "重置权限", targetType = "ROLE_PERMISSION", idParamIndex = 0)
    public ApiResponse<Void> resetRolePermissions(@PathVariable String roleCode) {
        permissionService.resetRolePermissions(roleCode);
        return ApiResponse.ok();
    }

    // ---------------- 操作日志 ----------------

    /** 操作日志分页 */
    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUDIT_READ + "')")
    public ApiResponse<PageResult<SysAuditLog>> auditLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType) {
        return ApiResponse.ok(userService.auditLogs(page, size, operator, action, targetType));
    }
}
