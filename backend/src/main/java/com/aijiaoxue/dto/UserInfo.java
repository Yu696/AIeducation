package com.aijiaoxue.dto;

import com.aijiaoxue.entity.SysUser;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前登录用户信息（/api/auth/login 与 /api/auth/me 的 user 字段）。
 *
 * <p>只暴露前端需要的字段，<b>不包含口令哈希</b>。
 */
@Data
public class UserInfo {

    private Long id;
    private String username;
    private String realName;
    private String roleCode;
    private String roleName;
    private Long teacherId;
    private String teacherName;
    private String departmentCode;
    private String departmentName;
    /** 数据可见范围：ALL / DEPARTMENT / SELF */
    private String dataScope;
    private LocalDateTime lastLoginTime;

    /** 由实体构造，roleName 与 dataScope 由调用方补齐 */
    public static UserInfo from(SysUser user) {
        UserInfo info = new UserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setRealName(user.getRealName());
        info.setRoleCode(user.getRoleCode());
        info.setTeacherId(user.getTeacherId());
        info.setDepartmentCode(user.getDepartmentCode());
        info.setLastLoginTime(user.getLastLoginTime());
        return info;
    }
}
