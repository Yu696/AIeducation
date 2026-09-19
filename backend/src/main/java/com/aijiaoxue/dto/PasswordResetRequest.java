package com.aijiaoxue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重置密码请求。
 */
@Data
public class PasswordResetRequest {

    @NotBlank(message = "新密码不能为空")
    private String password;
}
