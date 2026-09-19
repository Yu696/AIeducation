package com.aijiaoxue.common;

/**
 * 数据可见范围。对应 sys_role.data_scope 与 sys_user.department_code / teacher_id。
 */
public enum DataScope {

    /** 全量数据：系统管理员、教学督导 */
    ALL,

    /** 本教研室数据：教研室主任（是否可跨院由 sys_config.cross_department 控制） */
    DEPARTMENT,

    /** 仅本人数据：教师 */
    SELF
}
