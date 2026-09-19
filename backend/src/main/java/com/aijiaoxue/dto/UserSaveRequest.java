package com.aijiaoxue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建/修改用户请求（US-01）。
 *
 * <p>修改时 {@code password} 可为空表示不改口令。
 */
@Data
public class UserSaveRequest {

    @NotBlank(message = "登录名不能为空")
    private String username;

    /** 新建时必填；修改时留空表示不改口令 */
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "角色不能为空")
    private String roleCode;

    /** 教师账号需绑定教师档案 */
    private Long teacherId;

    /** 教研室主任/院系归属 */
    private String departmentCode;

    /** 是否启用：1 启用，0 停用 */
    private Integer enabled;
}
