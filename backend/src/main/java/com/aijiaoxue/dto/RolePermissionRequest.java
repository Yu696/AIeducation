package com.aijiaoxue.dto;

import lombok.Data;

import java.util.List;

/**
 * 保存角色权限请求（US-01 角色—功能权限矩阵）。
 */
@Data
public class RolePermissionRequest {

    /** 目标权限码集合，整体覆盖式保存；空集合表示收回全部权限 */
    private List<String> permissions;
}
