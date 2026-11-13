package com.example.trainee.service;

import com.example.trainee.entity.*;
import com.example.trainee.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 意向记录装饰器 — 将 Intention 实体 + 关联快照 + 联查数据
 * 转换为前端/mock 契约一致的扁平 Map。
 * <p>
 * 三个 Service（Admin / Student / Teacher）统一调用本组件，
 * 从根源消除各自维护一套导致的契约漂移。
 */
@Component
@RequiredArgsConstructor
public class IntentionDecorator {

    private final StudentMapper studentMapper;
    private final TrainingRoomMapper trainingRoomMapper;
    private final InterviewMapper interviewMapper;

    public Map<String, Object> decorate(Intention it) {
        Map<String, Object> m = new LinkedHashMap<>();

        Student s = it.getStudentId() != null ? studentMapper.selectById(it.getStudentId()) : null;
        m.put("id", it.getId());
        m.put("studentId", it.getStudentId());
        m.put("studentNo", s != null ? s.getStudentNo() : null);
        m.put("studentName", s != null ? s.getName() : null);
        m.put("major", s != null ? s.getMajor() : null);
        m.put("grade", s != null ? s.getGrade() : null);

        m.put("teacherId", it.getTeacherId());
        m.put("teacherNo", it.getTeacherNoSnapshot());
        m.put("teacherName", it.getTeacherNameSnapshot());

        m.put("roomId", it.getRoomId());
        m.put("roomName", it.getRoomNameSnapshot());
        TrainingRoom room = it.getRoomId() != null ? trainingRoomMapper.selectById(it.getRoomId()) : null;
        m.put("roomQuota", room != null ? room.getQuota() : null);
        m.put("roomUsed", it.getRoomId() != null ? trainingRoomMapper.countOccupied(it.getRoomId()) : 0);

        m.put("priority", it.getPriority());
        m.put("status", it.getStatus() != null ? it.getStatus().name() : null);
        m.put("intro", it.getIntro());
        m.put("acceptInnerAdjust", it.getAcceptInnerAdjust() != null && it.getAcceptInnerAdjust() == 1);
        m.put("acceptGlobalAdjust", it.getAcceptGlobalAdjust() != null && it.getAcceptGlobalAdjust() == 1);
        m.put("submittedAt", toEpochMilli(it.getSubmitTime()));
        m.put("round", it.getRound());
        m.put("rank", it.getWaitlistRank());

        Interview iv = interviewMapper.findByIntention(it.getId());
        if (iv != null) {
            Map<String, Object> ivM = new LinkedHashMap<>();
            ivM.put("attitude", iv.getAttitudeScore());
            ivM.put("logic", iv.getLogicScore());
            ivM.put("coding", iv.getCodingScore());
            ivM.put("commitment", iv.getCommitmentScore());
            ivM.put("total", iv.getTotal());
            ivM.put("comment", iv.getComment());
            ivM.put("createdAt", toEpochMilli(iv.getCreateTime()));
            m.put("interview", ivM);
        } else {
            m.put("interview", null);
        }

        return m;
    }

    public static long toEpochMilli(LocalDateTime ldt) {
        return ldt == null ? 0 : ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
