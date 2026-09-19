package com.aijiaoxue.security;

import com.aijiaoxue.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类。
 */
public final class CurrentUserHolder {

    private CurrentUserHolder() {
    }

    /**
     * 获取当前登录用户。
     *
     * @throws BusinessException 未登录时抛出 401，由 GlobalExceptionHandler 转换为统一响应
     */
    public static CurrentUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser cu)) {
            throw new BusinessException(com.aijiaoxue.common.ApiResponse.CODE_UNAUTHORIZED, "未登录或登录已失效，请重新登录");
        }
        return cu;
    }

    /** 当前用户，未登录返回 null（用于匿名接口） */
    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser cu) {
            return cu;
        }
        return null;
    }
}
