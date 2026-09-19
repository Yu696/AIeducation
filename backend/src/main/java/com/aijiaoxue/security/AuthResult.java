package com.aijiaoxue.security;

import com.aijiaoxue.dto.UserInfo;

import java.util.List;

/**
 * 登录结果：令牌 + 用户上下文 + 权限码清单。
 */
public class AuthResult {

    private final String token;
    private final UserInfo user;
    private final List<String> permissions;

    public AuthResult(String token, UserInfo user, List<String> permissions) {
        this.token = token;
        this.user = user;
        this.permissions = permissions;
    }

    public String getToken() {
        return token;
    }

    public UserInfo getUser() {
        return user;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}
