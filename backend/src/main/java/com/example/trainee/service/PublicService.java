package com.example.trainee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.trainee.common.BizException;
import com.example.trainee.entity.Notice;
import com.example.trainee.entity.ScheduleItem;
import com.example.trainee.entity.Student;
import com.example.trainee.entity.Teacher;
import com.example.trainee.entity.TrainingRoom;
import com.example.trainee.mapper.NoticeMapper;
import com.example.trainee.mapper.ScheduleItemMapper;
import com.example.trainee.mapper.StudentMapper;
import com.example.trainee.mapper.TeacherMapper;
import com.example.trainee.mapper.TrainingRoomMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开门户接口：首页、实训室列表/详情、演示账号。
 * 这些接口在 WebMvcConfig 中已排除 token 拦截。
 */
@Service
@RequiredArgsConstructor
public class PublicService {

    private final ScheduleItemMapper scheduleMapper;
    private final NoticeMapper noticeMapper;
    private final TrainingRoomMapper roomMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 首页聚合数据 */
    public Map<String, Object> home() {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        // 1) schedules
        List<Map<String, Object>> schedules = new ArrayList<>();
        List<ScheduleItem> items = scheduleMapper.selectList(
                new LambdaQueryWrapper<ScheduleItem>().orderByAsc(ScheduleItem::getStepNo));
        for (var item : items) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", item.getId());
            m.put("step", item.getStepNo());
            m.put("key", item.getKeyValue());
            m.put("title", item.getTitle());
            m.put("startAt", toEpochMilli(item.getStartTime()));
            m.put("endAt", toEpochMilli(item.getEndTime()));
            m.put("description", item.getDescription());
            String state;
            if (item.getStartTime() != null && now.isBefore(item.getStartTime())) {
                state = "未开始";
            } else if (item.getEndTime() != null && now.isAfter(item.getEndTime())) {
                state = "已结束";
            } else {
                state = "进行中";
            }
            m.put("state", state);
            schedules.add(m);
        }
        result.put("steps", schedules);

        // 2) notices
        List<Map<String, Object>> notices = new ArrayList<>();
        List<Notice> ns = noticeMapper.selectList(
                new LambdaQueryWrapper<Notice>().orderByDesc(Notice::getPublishTime));
        for (var n : ns) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("level", n.getLevel());
            m.put("publishedAt", toEpochMilli(n.getPublishTime()));
            notices.add(m);
        }
        result.put("notices", notices);

        // 3) rooms（首页简要）
        result.put("rooms", buildRoomBriefList());

        // 4) intentWindow
        result.put("intentWindow", findIntentWindow(items, now));

        return result;
    }

    /** 全部实训室列表，含剩余名额和导师数 */
    public List<Map<String, Object>> listRooms() {
        List<Map<String, Object>> list = new ArrayList<>();
        List<TrainingRoom> rooms = roomMapper.selectList(null);
        for (var room : rooms) {
            Map<String, Object> m = buildRoomBrief(room);
            long teacherCount = teacherMapper.selectCount(
                    new LambdaQueryWrapper<Teacher>().eq(Teacher::getRoomId, room.getId()));
            m.put("teacherCount", teacherCount);
            list.add(m);
        }
        return list;
    }

    /** 实训室详情 */
    public Map<String, Object> roomDetail(Long id) {
        TrainingRoom room = roomMapper.selectById(id);
        if (room == null) {
            throw new BizException(404, "实训室不存在");
        }
        int remaining = Math.max(0, room.getQuota() - roomMapper.countOccupied(id));

        List<Map<String, Object>> teachers = new ArrayList<>();
        List<Teacher> ts = teacherMapper.selectList(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getRoomId, id));
        for (var t : ts) {
            Map<String, Object> tm = new HashMap<>();
            tm.put("id", t.getId());
            tm.put("name", t.getName());
            tm.put("teacherNo", t.getTeacherNo());
            tm.put("title", t.getTitle());
            teachers.add(tm);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", room.getId());
        result.put("code", room.getRoomCode());
        result.put("name", room.getName());
        result.put("direction", room.getDirection());
        result.put("quota", room.getQuota());
        result.put("remaining", remaining);
        result.put("training", room.getTrainingPlan());
        result.put("assessment", room.getAssessmentRequirement());
        result.put("tags", parseTags(room.getTags()));
        result.put("description", room.getDescription());
        result.put("teachers", teachers);
        return result;
    }

    /** 演示账号 */
    public List<Map<String, Object>> demoAccounts() {
        List<Map<String, Object>> list = new ArrayList<>();

        // admin
        Map<String, Object> admin = new HashMap<>();
        admin.put("role", "admin");
        admin.put("username", "admin");
        admin.put("password", "admin123");
        admin.put("description", "超级管理员");
        list.add(admin);

        // 导师
        List<Teacher> teachers = teacherMapper.selectList(null);
        for (var t : teachers) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", "teacher");
            m.put("username", t.getTeacherNo());
            m.put("password", "Teach@2026");
            m.put("description", t.getName() + "（" + t.getTitle() + "）");
            list.add(m);
        }

        // 学生
        List<Student> students = studentMapper.selectList(
                new LambdaQueryWrapper<Student>().last("LIMIT 5"));
        for (var s : students) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", "student");
            m.put("username", s.getStudentNo());
            m.put("password", "Stu@2026");
            m.put("description", s.getName() + "（" + s.getMajor() + "）");
            list.add(m);
        }

        return list;
    }

    /** 重置演示数据（空实现） */
    public void resetDemo() {
        // 空实现，演示时重启应用即可
    }

    // ---------- 辅助 ----------

    private List<Map<String, Object>> buildRoomBriefList() {
        List<Map<String, Object>> list = new ArrayList<>();
        List<TrainingRoom> rooms = roomMapper.selectList(null);
        for (var room : rooms) {
            list.add(buildRoomBrief(room));
        }
        return list;
    }

    private Map<String, Object> buildRoomBrief(TrainingRoom room) {
        int remaining = Math.max(0, room.getQuota() - roomMapper.countOccupied(room.getId()));
        Map<String, Object> m = new HashMap<>();
        m.put("id", room.getId());
        m.put("code", room.getRoomCode());
        m.put("name", room.getName());
        m.put("direction", room.getDirection());
        m.put("quota", room.getQuota());
        m.put("remaining", remaining);
        m.put("tags", parseTags(room.getTags()));
        m.put("training", room.getTrainingPlan());
        m.put("assessment", room.getAssessmentRequirement());
        m.put("description", room.getDescription());
        return m;
    }

    private Map<String, Object> findIntentWindow(List<ScheduleItem> items, LocalDateTime now) {
        Map<String, Object> result = new HashMap<>();
        ScheduleItem intent = null;
        for (var it : items) {
            if ("intent".equals(it.getKeyValue())) {
                intent = it;
                break;
            }
        }
        if (intent == null) {
            result.put("key", "intent");
            result.put("step", null);
            result.put("title", null);
            result.put("startAt", null);
            result.put("endAt", null);
            result.put("isOpen", false);
            return result;
        }
        boolean isOpen = intent.getStartTime() != null
                && intent.getEndTime() != null
                && !now.isBefore(intent.getStartTime())
                && !now.isAfter(intent.getEndTime());
        result.put("key", "intent");
        result.put("step", intent.getStepNo());
        result.put("title", intent.getTitle());
        result.put("startAt", toEpochMilli(intent.getStartTime()));
        result.put("endAt", toEpochMilli(intent.getEndTime()));
        result.put("isOpen", isOpen);
        return result;
    }

    /** LocalDateTime -> 毫秒时间戳，null 安全 */
    private Long toEpochMilli(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 数据库 tags 存的是 JSON 字符串（H2 VARCHAR 或 MySQL JSON），
     * 前端期望 List<String>。解析失败返回空列表。
     */
    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
