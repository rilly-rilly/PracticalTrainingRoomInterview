package com.example.trainee.controller;

import com.example.trainee.common.ApiResponse;
import com.example.trainee.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生端业务接口。TokenInterceptor 覆盖所有 /api/** 路径。
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /** 获取当前学生所有意向 */
    @GetMapping("/intentions")
    public ApiResponse<List<Map<String, Object>>> listIntentions() {
        return ApiResponse.ok(studentService.listMyIntentions());
    }

    /**
     * 提交意向。
     * body 格式:
     * <pre>{
     *   "choices": [teacherId1, teacherId2, ...],   // 1-3 个导师 ID
     *   "intro": "自我介绍不少于 20 字...",
     *   "acceptInnerAdjust": true,                 // 是否接受组内调剂
     *   "acceptGlobalAdjust": true                 // 是否接受跨室调剂
     * }</pre>
     */
    @PostMapping("/intentions")
    public ApiResponse<List<Map<String, Object>>> submitIntentions(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> choices = (List<Long>) body.get("choices");
        String intro = (String) body.get("intro");
        Object innerObj = body.get("acceptInnerAdjust");
        Object globalObj = body.get("acceptGlobalAdjust");

        Boolean acceptInner = innerObj instanceof Boolean ? (Boolean) innerObj
                : innerObj != null ? Boolean.parseBoolean(innerObj.toString()) : null;
        Boolean acceptGlobal = globalObj instanceof Boolean ? (Boolean) globalObj
                : globalObj != null ? Boolean.parseBoolean(globalObj.toString()) : null;

        return ApiResponse.ok(studentService.submitIntentions(choices, intro, acceptInner, acceptGlobal));
    }

    /** 当前学生状态概览（意向列表 + 摘要 + 窗口 + 确认 + 调剂） */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(studentService.status());
    }

    /**
     * 手动确认某一个拟录取意向。
     * body: { "intentionId": 123 }
     */
    @PostMapping("/confirm")
    public ApiResponse<Map<String, Object>> confirm(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("intentionId");
        Long intentionId = idObj instanceof Number ? ((Number) idObj).longValue()
                : Long.parseLong(idObj.toString());
        return ApiResponse.ok(studentService.confirm(intentionId));
    }

    /** 调剂记录 */
    @GetMapping("/adjustments")
    public ApiResponse<Map<String, Object>> adjustments() {
        return ApiResponse.ok(studentService.adjustments());
    }

    /** 最终结果（公示 / 已录取 / 待确认 / 递补 ...） */
    @GetMapping("/final")
    public ApiResponse<Map<String, Object>> finalResult() {
        return ApiResponse.ok(studentService.finalResult());
    }
}
