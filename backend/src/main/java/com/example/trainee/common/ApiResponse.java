package com.example.trainee.common;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体。code 复用 HTTP 语义状态码：
 * 200 成功；400 参数错误；401 登录态失效；403 无权限；404 不存在；
 * 409 名额已满；410 时间窗口关闭；422 状态不允许或校验失败；500 服务异常。
 */
@Data
@NoArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "操作成功", data);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(200, message, data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
