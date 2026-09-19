package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色—权限关联。存在记录时表示该角色权限已被人工覆写，
 * 覆写优先于 PermissionCodes 中的默认矩阵。
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleCode;

    private String permCode;
}
