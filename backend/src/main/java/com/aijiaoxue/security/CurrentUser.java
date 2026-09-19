package com.aijiaoxue.security;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.entity.SysUser;

/**
 * 当前登录用户（SecurityContext principal）。
 *
 * <p>封装用户的角色码、教研室、绑定教师档案，业务层据此做
 * 「功能权限（权限码）+ 数据权限（可见范围）」双重控制。
 */
public class CurrentUser {

    private final Long userId;
    private final String username;
    private final String realName;
    private final String roleCode;
    private final Long teacherId;
    private final String departmentCode;
    private final String token;

    private CurrentUser(Long userId, String username, String realName, String roleCode,
                        Long teacherId, String departmentCode, String token) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.roleCode = roleCode;
        this.teacherId = teacherId;
        this.departmentCode = departmentCode;
        this.token = token;
    }

    public static CurrentUser from(SysUser user, String token) {
        return new CurrentUser(user.getId(), user.getUsername(), user.getRealName(),
                user.getRoleCode(), user.getTeacherId(), user.getDepartmentCode(), token);
    }

    /** 是否教师角色（数据范围 SELF） */
    public boolean isTeacher() {
        return RoleCodesRef.TEACHER.equals(roleCode);
    }

    /** 是否教研室主任（数据范围 DEPARTMENT） */
    public boolean isDirector() {
        return RoleCodesRef.DIRECTOR.equals(roleCode);
    }

    public boolean isAdmin() {
        return RoleCodesRef.ADMIN.equals(roleCode);
    }

    /**
     * 取绑定教师档案 ID，教师账号未绑定时抛出越权异常。
     * 避免因配置缺失导致 SELF 过滤失效而泄露他人数据。
     */
    public Long requireTeacherId() {
        if (teacherId == null) {
            throw BusinessException.forbidden("当前账号未绑定教师档案，无法查看个人教学数据");
        }
        return teacherId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRealName() {
        return realName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public String getToken() {
        return token;
    }

    /** 内部引用角色码常量，避免静态导入歧义 */
    private static final class RoleCodesRef {
        private static final String TEACHER = com.aijiaoxue.common.RoleCodes.TEACHER;
        private static final String DIRECTOR = com.aijiaoxue.common.RoleCodes.DIRECTOR;
        private static final String ADMIN = com.aijiaoxue.common.RoleCodes.ADMIN;
    }
}
