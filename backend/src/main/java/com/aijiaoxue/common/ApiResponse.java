package com.aijiaoxue.common;

/**
 * 统一响应包。
 *
 * <p>契约见 docs/API契约.md 第 0 节：{@code {"code":0,"message":"ok","data":{}}}。
 * code=0 表示成功，其余为业务或鉴权错误码。
 *
 * @param <T> 业务数据类型
 */
public class ApiResponse<T> {

    /** 成功 */
    public static final int CODE_OK = 0;
    /** 参数校验失败 */
    public static final int CODE_BAD_REQUEST = 400;
    /** 未登录或令牌失效 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 无权限（越权访问） */
    public static final int CODE_FORBIDDEN = 403;
    /** 资源不存在 */
    public static final int CODE_NOT_FOUND = 404;
    /** 业务冲突，如课程编号重复 */
    public static final int CODE_CONFLICT = 409;
    /** 服务器内部错误 */
    public static final int CODE_ERROR = 500;

    private int code;
    private String message;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(CODE_OK, "ok", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(CODE_OK, "ok", null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
