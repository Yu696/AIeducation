package com.aijiaoxue.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。把各类异常统一转换为 ApiResponse，
 * 保证前端只需处理 {code,message,data} 一种结构。
 *
 * <p>注意：鉴权类异常必须同时设置正确的 HTTP 状态码（401/403），
 * 否则 Spring Security 的 @PreAuthorize 失败会被本处理器"驯化"为 200，
 * 前端将无法据状态码区分"无权限"与其他业务错误。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常：响应体 code 保持业务码，同时把业务码映射为对应的 HTTP 状态码。
     *
     * <p>必要性：数据级权限（如教师访问他人教学班、主任访问他院数据）由业务层抛出
     * code=403 的 BusinessException。若统一按 200 返回，前端与测试都无法从 HTTP 状态码
     * 判断越权，违反 US-01 验收标准③「越权访问被拦截并给出提示」。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    /** 业务码 → HTTP 状态码 */
    private HttpStatus toHttpStatus(int code) {
        return switch (code) {
            case ApiResponse.CODE_BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case ApiResponse.CODE_UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case ApiResponse.CODE_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ApiResponse.CODE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ApiResponse.CODE_CONFLICT -> HttpStatus.CONFLICT;
            default -> HttpStatus.OK;
        };
    }

    /** 登录口令错误：返回 401，前端据此提示"用户名或密码错误" */
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ApiResponse.CODE_UNAUTHORIZED, e.getMessage()));
    }

    /** 未登录 / 令牌失效：返回 401 */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(
            org.springframework.security.core.AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ApiResponse.CODE_UNAUTHORIZED, "未登录或登录已失效，请重新登录"));
    }

    /** 越权访问（US-01 验收标准③：越权访问被拦截并给出提示）：返回 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("越权访问被拦截: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ApiResponse.CODE_FORBIDDEN, "无权限访问该功能（越权访问已拦截）"));
    }

    /** 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ApiResponse.fail(ApiResponse.CODE_BAD_REQUEST, "参数校验失败：" + msg);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ApiResponse.fail(ApiResponse.CODE_BAD_REQUEST, "参数校验失败：" + msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResponse.fail(ApiResponse.CODE_BAD_REQUEST, "缺少必填参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ApiResponse.fail(ApiResponse.CODE_BAD_REQUEST, "参数类型不正确：" + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return ApiResponse.fail(ApiResponse.CODE_BAD_REQUEST, "请求体格式不正确");
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.fail(ApiResponse.CODE_ERROR, "服务器内部错误：" + e.getMessage());
    }
}
