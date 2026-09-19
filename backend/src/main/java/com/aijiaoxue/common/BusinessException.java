package com.aijiaoxue.common;

/**
 * 业务异常。由 GlobalExceptionHandler 统一转换为 ApiResponse。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(ApiResponse.CODE_BAD_REQUEST, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 资源不存在 */
    public static BusinessException notFound(String message) {
        return new BusinessException(ApiResponse.CODE_NOT_FOUND, message);
    }

    /** 业务冲突，例如课程编号重复、已开课课程不允许删除 */
    public static BusinessException conflict(String message) {
        return new BusinessException(ApiResponse.CODE_CONFLICT, message);
    }

    /** 越权访问 */
    public static BusinessException forbidden(String message) {
        return new BusinessException(ApiResponse.CODE_FORBIDDEN, message);
    }

    public int getCode() {
        return code;
    }
}
