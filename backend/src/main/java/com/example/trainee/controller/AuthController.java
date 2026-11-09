package com.example.trainee.controller;

import com.example.trainee.common.ApiResponse;
import com.example.trainee.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录/登出/当前用户。
 * TokenInterceptor 已排除 /api/auth/login，其它两个接口需要 token。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录 */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        var username = body.get("username");
        var password = body.get("password");
        var result = authService.login(username, password);
        return ApiResponse.ok(result);
    }

    /** 登出 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok(null, "已登出");
    }

    /** 当前登录用户资料 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(authService.me());
    }
}
