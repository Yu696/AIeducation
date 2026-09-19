package com.aijiaoxue.common;

/**
 * 角色码常量。与 sys_role.role_code、sys_user.role_code 对应。
 *
 * <p>实验一已确认：目标用户为教研室主任、教师、教学督导、课程管理员/系统管理员，
 * <b>不含学生</b>（学生评教以汇总数据形式展示，不设学生账号）。
 */
public final class RoleCodes {

    /** 系统管理员：全量数据与全部功能 */
    public static final String ADMIN = "ADMIN";
    /** 教研室主任：本教研室数据（是否可跨院由 sys_config.cross_department 控制） */
    public static final String DIRECTOR = "DIRECTOR";
    /** 教师：仅本人课程、教学班与资源 */
    public static final String TEACHER = "TEACHER";
    /** 教学督导：全校开课与督导数据，只读 */
    public static final String SUPERVISOR = "SUPERVISOR";
    /** 课程管理员：课程库与开课，只读 */
    public static final String COURSE_ADMIN = "COURSE_ADMIN";

    private RoleCodes() {
    }
}
