package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志。所有写操作由 AuditLogAspect 自动落库，
 * 满足实验一风险对策「AI 输出先记录、再审查、后使用」的可追溯要求。
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人登录名 */
    private String operator;

    /** 操作人角色码 */
    private String roleCode;

    /** 动作：新增/修改/删除/导入/导出/登录 */
    private String action;

    /** 目标类型，如 COURSE、OFFERING、USER */
    private String targetType;

    /** 目标主键 */
    private String targetId;

    /** 操作详情摘要 */
    private String detail;

    /** 来源 IP */
    private String ip;

    private LocalDateTime createdAt;
}
