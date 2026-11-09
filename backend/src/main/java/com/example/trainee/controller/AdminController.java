package com.example.trainee.controller;

import com.example.trainee.common.ApiResponse;
import com.example.trainee.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员端业务接口。
 * 全部在 /api/admin 下，TokenInterceptor 不排除，需要登录。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ================================================================
    // 数据导入
    // ================================================================

    /**
     * 批量导入学生/导师。
     * body:
     * {
     *   "type": "student" | "teacher",
     *   "strict": true | false,
     *   "rows": [{"姓名":"","学号":"","专业":"","年级":""}]
     * }
     */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importData(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        Boolean strictObj = (Boolean) body.get("strict");
        boolean strict = Boolean.TRUE.equals(strictObj);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> rows = (List<Map<String, String>>) body.get("rows");

        Map<String, Object> data = adminService.importData(type, rows, strict);
        Object failures = data.get("failures");
        if (failures instanceof List<?> f && !f.isEmpty()) {
            return ApiResponse.fail(200, "部分导入失败", data);
        }
        return ApiResponse.ok(data);
    }

    // ================================================================
    // 时间节点
    // ================================================================

    @GetMapping("/schedules")
    public ApiResponse<List<Map<String, Object>>> listSchedules() {
        return ApiResponse.ok(adminService.listSchedules());
    }

    /**
     * 更新时间节点。
     * body: { "title", "description", "startAt": Long(ms), "endAt": Long(ms) }
     */
    @PutMapping("/schedules/{id}")
    public ApiResponse<Map<String, Object>> updateSchedule(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        Long startAt = toLong(body.get("startAt"));
        Long endAt = toLong(body.get("endAt"));
        return ApiResponse.ok(adminService.updateSchedule(id, title, description, startAt, endAt));
    }

    // ================================================================
    // 公告
    // ================================================================

    @GetMapping("/notices")
    public ApiResponse<List<Map<String, Object>>> listNotices() {
        return ApiResponse.ok(adminService.listNotices());
    }

    /**
     * 发布公告。body: { "title", "content", "level"? }
     */
    @PostMapping("/notices")
    public ApiResponse<Map<String, Object>> createNotice(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String level = (String) body.get("level");
        return ApiResponse.ok(adminService.createNotice(title, content, level));
    }

    @DeleteMapping("/notices/{id}")
    public ApiResponse<Void> deleteNotice(@PathVariable Long id) {
        adminService.deleteNotice(id);
        return ApiResponse.ok(null);
    }

    // ================================================================
    // 录取总览
    // ================================================================

    @GetMapping("/admissions")
    public ApiResponse<Map<String, Object>> admissionsOverview() {
        return ApiResponse.ok(adminService.admissionsOverview());
    }

    /**
     * 调整实训室名额。body: { "quota": Integer }
     */
    @PutMapping("/rooms/{id}/quota")
    public ApiResponse<Map<String, Object>> updateRoomQuota(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Integer quota = toInt(body.get("quota"));
        return ApiResponse.ok(adminService.updateRoomQuota(id, quota));
    }

    /**
     * 逾期放弃并自动补录。body: { "force": true | false }
     */
    @PostMapping("/admissions/expire")
    public ApiResponse<Map<String, Object>> expireConfirmations(@RequestBody Map<String, Object> body) {
        boolean force = Boolean.TRUE.equals(body.get("force"));
        return ApiResponse.ok(adminService.expireConfirmations(force));
    }

    // ================================================================
    // 调剂
    // ================================================================

    @GetMapping("/adjustments")
    public ApiResponse<Map<String, Object>> adjustmentsOverview() {
        return ApiResponse.ok(adminService.adjustmentsOverview());
    }

    /**
     * 执行调剂。body: { "type": "inner" | "global" | "interview" | "supplement" }
     */
    @PostMapping("/adjustments/run")
    public ApiResponse<Map<String, Object>> runAdjustment(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        return ApiResponse.ok(adminService.runAdjustment(type));
    }

    // ================================================================
    // 公示
    // ================================================================

    @GetMapping("/publicity")
    public ApiResponse<Map<String, Object>> publicityOverview() {
        return ApiResponse.ok(adminService.publicityOverview());
    }

    @PostMapping("/publicity/publish")
    public ApiResponse<Map<String, Object>> publishPublicity() {
        return ApiResponse.ok(adminService.publishPublicity());
    }

    /**
     * 结束公示。body: { "force": true | false }
     */
    @PostMapping("/publicity/finalize")
    public ApiResponse<Map<String, Object>> finalizePublicity(@RequestBody Map<String, Object> body) {
        boolean force = Boolean.TRUE.equals(body.get("force"));
        return ApiResponse.ok(adminService.finalizePublicity(force));
    }

    // ================================================================
    // 操作日志
    // ================================================================

    @GetMapping("/logs")
    public ApiResponse<List<Map<String, Object>>> listLogs() {
        return ApiResponse.ok(adminService.listLogs());
    }

    // ================================================================
    // 辅助
    // ================================================================

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }
}
