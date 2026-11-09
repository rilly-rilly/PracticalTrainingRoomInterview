package com.example.trainee.controller;

import com.example.trainee.common.ApiResponse;
import com.example.trainee.service.PublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公开门户接口。TokenInterceptor 已排除 /api/public/** 和 /api/rooms/**。
 */
@RestController
@RequiredArgsConstructor
public class PublicController {

    private final PublicService publicService;

    /** 首页聚合 */
    @GetMapping("/api/public/home")
    public ApiResponse<Map<String, Object>> home() {
        return ApiResponse.ok(publicService.home());
    }

    /** 演示账号 */
    @GetMapping("/api/public/demo-accounts")
    public ApiResponse<List<Map<String, Object>>> demoAccounts() {
        return ApiResponse.ok(publicService.demoAccounts());
    }

    /** 重置演示数据（空实现） */
    @PostMapping("/api/public/reset")
    public ApiResponse<Void> reset() {
        publicService.resetDemo();
        return ApiResponse.ok(null, "演示数据已重置");
    }

    /** 实训室列表 */
    @GetMapping("/api/rooms")
    public ApiResponse<List<Map<String, Object>>> listRooms() {
        return ApiResponse.ok(publicService.listRooms());
    }

    /** 实训室详情 */
    @GetMapping("/api/rooms/{id}")
    public ApiResponse<Map<String, Object>> roomDetail(@PathVariable Long id) {
        return ApiResponse.ok(publicService.roomDetail(id));
    }
}
