package com.example.trainee.common;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器。业务异常 -> 直接返回对应状态码；
 * 参数解析异常 -> 400；其他 -> 500。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException e) {
        log.debug("[biz] code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode()).body(ApiResponse.fail(e.getCode(), e.getMessage(), e.getData()));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, ConstraintViolationException.class,
            MethodArgumentNotValidException.class, InvalidFormatException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Object>> handleParam(Exception e) {
        String msg = "参数错误";
        if (e instanceof MissingServletRequestParameterException ex) {
            msg = "缺少参数：" + ex.getParameterName();
        } else if (e instanceof ConstraintViolationException ex) {
            msg = ex.getConstraintViolations().iterator().next().getMessage();
        } else if (e instanceof MethodArgumentNotValidException ex) {
            msg = ex.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                    .orElse("参数校验失败");
        }
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception e) {
        log.error("[sys] {}", e.getMessage(), e);
        return ResponseEntity.status(500).body(ApiResponse.fail(500, "服务内部错误，请稍后重试"));
    }
}
