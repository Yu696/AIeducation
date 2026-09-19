package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色定义。data_scope 决定数据可见范围：ALL / DEPARTMENT / SELF。
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色码，唯一 */
    private String roleCode;

    /** 角色中文名 */
    private String roleName;

    /** 角色说明 */
    private String description;

    /** 数据可见范围：ALL / DEPARTMENT / SELF */
    private String dataScope;

    /** 展示排序 */
    private Integer sortNo;
}
