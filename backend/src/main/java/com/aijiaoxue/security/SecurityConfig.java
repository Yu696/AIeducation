package com.aijiaoxue.security;

import com.aijiaoxue.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * 安全配置。
 *
 * <p>US-01 验收标准②③的落地方式：
 * <ul>
 *   <li>功能级鉴权：接口上使用 {@code @PreAuthorize("hasAuthority('xxx')")} 校验权限码；</li>
 *   <li>越权拦截：未认证返回 401、权限不足返回 403，且响应体统一为 JSON，便于前端提示；</li>
 *   <li>数据级鉴权：由 {@link DataScopeService} 在业务层强制收窄查询范围。</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** 无需登录即可访问的路径 */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/config/public",
            "/api/health",
            "/index.html", "/", "/favicon.ico",
            "/assets/**", "/pages/**", "/error"
    };

    private final TokenAuthFilter tokenAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(TokenAuthFilter tokenAuthFilter, ObjectMapper objectMapper) {
        this.tokenAuthFilter = tokenAuthFilter;
        this.objectMapper = objectMapper;
    }

    /** 口令编码器：BCrypt */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden))
                .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 未认证：返回 401 JSON */
    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                   AuthenticationException ex) throws IOException {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                ApiResponse.fail(ApiResponse.CODE_UNAUTHORIZED, "未登录或登录已失效，请重新登录"));
    }

    /** 权限不足：返回 403 JSON（前端据此提示"无权限访问该功能"） */
    private void writeForbidden(HttpServletRequest request, HttpServletResponse response,
                                AccessDeniedException ex) throws IOException {
        writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                ApiResponse.fail(ApiResponse.CODE_FORBIDDEN, "无权限访问该功能（越权访问已拦截）"));
    }

    private void writeJson(HttpServletResponse response, int status, ApiResponse<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /** 允许前端在独立端口打开时跨域访问（开发期便利，Sprint 1 教学演示足够） */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
