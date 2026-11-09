package com.example.trainee.controller;

import com.example.trainee.common.ApiResponse;
import com.example.trainee.entity.IntentionStatus;
import com.example.trainee.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 导师端接口。TokenInterceptor 统一拦截，需携带 Bearer token。
 */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    /** 报考列表，可按状态过滤 */
    @GetMapping("/applicants")
    public ApiResponse<Map<String, Object>> listApplicants(
            @RequestParam(value = "status", required = false) IntentionStatus status) {
        return ApiResponse.ok(teacherService.listApplicants(status));
    }

    /** 报考详情 */
    @GetMapping("/applicants/{id}")
    public ApiResponse<Map<String, Object>> applicantDetail(@PathVariable Long id) {
        return ApiResponse.ok(teacherService.applicantDetail(id));
    }

    /** 提交面试评分 */
    @PostMapping("/interviews")
    public ApiResponse<Map<String, Object>> submitInterview(@RequestBody Map<String, Object> body) {
        Long intentionId = toLong(body.get("intentionId"));
        Integer attitude = toInt(body.get("attitude"));
        Integer logic = toInt(body.get("logic"));
        Integer coding = toInt(body.get("coding"));
        Integer commitment = toInt(body.get("commitment"));
        String comment = (String) body.get("comment");
        return ApiResponse.ok(teacherService.submitInterview(intentionId, attitude, logic, coding, commitment, comment));
    }

    /** 做出录取决定 */
    @PostMapping("/decision")
    public ApiResponse<Map<String, Object>> makeDecision(@RequestBody Map<String, Object> body) {
        Long intentionId = toLong(body.get("intentionId"));
        String decision = (String) body.get("decision");
        return ApiResponse.ok(teacherService.makeDecision(intentionId, decision));
    }

    // ---------- 请求体字段转换 ----------

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return Long.valueOf(v.toString());
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return Integer.valueOf(v.toString());
    }
}
