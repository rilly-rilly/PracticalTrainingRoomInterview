package com.example.trainee.common;

/**
 * 带状态码的业务异常。全局异常处理器捕获后写入 ApiResponse。
 * 见 docs/03 错误码区分表。
 */
public class BizException extends RuntimeException {
    private final int code;
    private final Object data;

    public BizException(int code, String message) {
        this(code, message, null);
    }

    public BizException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
