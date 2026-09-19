package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 权限码字典。用于「用户与权限管理」页渲染权限矩阵勾选框。
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限码，如 course:read */
    private String permCode;

    /** 权限中文名 */
    private String permName;

    /** 所属模块 */
    private String module;
}
