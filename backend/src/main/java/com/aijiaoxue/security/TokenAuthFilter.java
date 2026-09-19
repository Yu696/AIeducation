package com.aijiaoxue.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 令牌认证过滤器。
 *
 * <p>从 {@code Authorization: Bearer &lt;token&gt;} 解析令牌，校验通过后把
 * 用户信息与<b>有效权限码</b>写入 SecurityContext，供 {@code @PreAuthorize}
 * 基于权限码做功能级鉴权（US-01 验收标准②）。
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final PermissionService permissionService;

    public TokenAuthFilter(TokenService tokenService, PermissionService permissionService) {
        this.tokenService = tokenService;
        this.permissionService = permissionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            var user = tokenService.resolve(token);
            if (user != null) {
                // 权限码 + 角色码一起作为 granted authority，便于同时支持 hasAuthority 与 hasRole
                List<SimpleGrantedAuthority> authorities = permissionService.effectivePermissions(user.getRoleCode())
                        .stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(java.util.stream.Collectors.toList());
                authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRoleCode()));

                CurrentUser principal = CurrentUser.from(user, token);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 提取 Bearer 令牌 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
