package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户。承载登录账号、角色与数据可见范围。
 *
 * <p>US-01：一个账号 → 一个角色码；department_code 决定教研室主任的可见教研室，
 * teacher_id 决定教师账号绑定的教师档案（数据范围 SELF 的依据）。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名，唯一 */
    private String username;

    /** BCrypt 口令哈希 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色码：ADMIN/DIRECTOR/TEACHER/SUPERVISOR/COURSE_ADMIN */
    private String roleCode;

    /** 教师账号绑定的教师档案 ID，非教师账号为空 */
    private Long teacherId;

    /** 所属教研室/院系代码，用于 DEPARTMENT 数据范围过滤 */
    private String departmentCode;

    /** 是否启用：1 启用，0 停用 */
    private Integer enabled;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
