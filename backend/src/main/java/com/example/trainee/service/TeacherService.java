package com.example.trainee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.trainee.common.BizException;
import com.example.trainee.common.LoginUser;
import com.example.trainee.config.TokenInterceptor;
import com.example.trainee.entity.*;
import com.example.trainee.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 导师端业务：查看报考、提交面试评分、做出录取决定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final IntentionMapper intentionMapper;
    private final TrainingRoomMapper trainingRoomMapper;
    private final InterviewMapper interviewMapper;
    private final AdmissionConfirmationMapper confirmationMapper;
    private final OperationLogMapper operationLogMapper;

    // ---------- 1. 报考列表 ----------

    public Map<String, Object> listApplicants(IntentionStatus statusFilter) {
        LoginUser user = TokenInterceptor.AuthContext.get();
        Long teacherId = user.getUserId();

        // 1) 报考列表
        List<Intention> intentions = intentionMapper.listByTeacher(teacherId, statusFilter);

        // 2) 装饰
        List<Map<String, Object>> applications = intentions.stream()
                .map(this::decorateIntention)
                .collect(Collectors.toList());

        // 3) 排序：interview.total 降序，同分时 studentNo 升序
        applications.sort((a, b) -> {
            Object ivA = a.get("interview");
            Object ivB = b.get("interview");
            int totalA = (ivA instanceof Map<?, ?> m && m.get("total") != null)
                    ? ((Number) m.get("total")).intValue() : 0;
            int totalB = (ivB instanceof Map<?, ?> m && m.get("total") != null)
                    ? ((Number) m.get("total")).intValue() : 0;
            if (totalB != totalA) return totalB - totalA;
            String noA = (String) ((Map<?, ?>) a.get("student")).get("studentNo");
            String noB = (String) ((Map<?, ?>) b.get("student")).get("studentNo");
            return String.valueOf(noA).compareTo(String.valueOf(noB));
        });

        // 4) 当前导师 profile
        Teacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) throw new BizException(404, "导师不存在");
        Map<String, Object> teacherProfile = buildTeacherProfile(teacher);

        // 5) 实训室信息
        Map<String, Object> room = new HashMap<>();
        if (teacher.getRoomId() != null) {
            TrainingRoom trainingRoom = trainingRoomMapper.selectById(teacher.getRoomId());
            int used = trainingRoomMapper.countOccupied(teacher.getRoomId());
            room.put("id", trainingRoom != null ? trainingRoom.getId() : teacher.getRoomId());
            room.put("name", trainingRoom != null ? trainingRoom.getName() : null);
            room.put("quota", trainingRoom != null ? trainingRoom.getQuota() : 0);
            room.put("used", used);
            room.put("remaining", trainingRoom != null
                    ? Math.max(0, trainingRoom.getQuota() - used) : 0);
        } else {
            room.put("id", null);
            room.put("name", null);
            room.put("quota", 0);
            room.put("used", 0);
            room.put("remaining", 0);
        }

        // 6) 各状态计数
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (IntentionStatus s : IntentionStatus.values()) {
            long cnt = intentions.stream().filter(i -> i.getStatus() == s).count();
            statusCounts.put(s.name(), cnt);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("applications", applications);
        result.put("teacher", teacherProfile);
        result.put("room", room);
        result.put("statusCounts", statusCounts);
        return result;
    }

    // ---------- 2. 报考详情 ----------

    public Map<String, Object> applicantDetail(Long intentionId) {
        Intention it = intentionMapper.selectById(intentionId);
        if (it == null) throw new BizException(404, "报考记录不存在");

        LoginUser user = TokenInterceptor.AuthContext.get();
        if (!user.getUserId().equals(it.getTeacherId())) {
            throw new BizException(403, "无权查看该报考");
        }

        return decorateIntention(it);
    }

    // ---------- 3. 提交面试 ----------

    @Transactional
    public Map<String, Object> submitInterview(Long intentionId, Integer attitude, Integer logic,
                                               Integer coding, Integer commitment, String comment) {
        Intention it = intentionMapper.selectById(intentionId);
        if (it == null) throw new BizException(404, "报考记录不存在");

        LoginUser user = TokenInterceptor.AuthContext.get();
        if (!user.getUserId().equals(it.getTeacherId())) {
            throw new BizException(403, "无权操作该报考");
        }

        if (it.getStatus() != IntentionStatus.待面试) {
            throw new BizException(422, "当前状态不允许提交面试评分：" + it.getStatus().name());
        }

        // 校验分数
        validateScore("attitude", attitude);
        validateScore("logic", logic);
        validateScore("coding", coding);
        validateScore("commitment", commitment);

        int total = attitude + logic + coding + commitment;

        Interview iv = new Interview();
        iv.setIntentionId(intentionId);
        iv.setAttitudeScore(attitude);
        iv.setLogicScore(logic);
        iv.setCodingScore(coding);
        iv.setCommitmentScore(commitment);
        iv.setTotal(total);
        iv.setComment(comment);
        iv.setCreateTime(LocalDateTime.now());
        interviewMapper.insert(iv);

        it.setStatus(IntentionStatus.面试完成);
        it.setUpdateTime(LocalDateTime.now());
        intentionMapper.updateById(it);

        writeOperationLog("teacher", user.getUserId(), it.getTeacherId(), "submitInterview",
                "intentionId=" + intentionId + ", total=" + total);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        return result;
    }

    // ---------- 4. 录取决定 ----------

    @Transactional
    public Map<String, Object> makeDecision(Long intentionId, String decision) {
        if (!"admit".equals(decision) && !"waitlist".equals(decision) && !"reject".equals(decision)) {
            throw new BizException(400, "decision 必须是 admit/waitlist/reject");
        }

        Intention it = intentionMapper.selectById(intentionId);
        if (it == null) throw new BizException(404, "报考记录不存在");

        LoginUser user = TokenInterceptor.AuthContext.get();
        if (!user.getUserId().equals(it.getTeacherId())) {
            throw new BizException(403, "无权操作该报考");
        }

        if (it.getStatus() != IntentionStatus.面试完成) {
            throw new BizException(422, "当前状态不允许做出录取决定：" + it.getStatus().name());
        }

        Map<String, Object> result = new HashMap<>();

        switch (decision) {
            case "admit" -> {
                TrainingRoom room = trainingRoomMapper.selectById(it.getRoomId());
                int used = trainingRoomMapper.countOccupied(it.getRoomId());
                if (room == null || used >= room.getQuota()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("roomId", it.getRoomId());
                    data.put("quota", room != null ? room.getQuota() : 0);
                    data.put("used", used);
                    throw new BizException(409, "名额已满", data);
                }
                it.setStatus(IntentionStatus.拟录取);
                it.setUpdateTime(LocalDateTime.now());
                intentionMapper.updateById(it);

                boolean autoConfirmed = autoConfirmIfSingle(it.getStudentId());

                writeOperationLog("teacher", user.getUserId(), it.getTeacherId(), "makeDecision:admit",
                        "intentionId=" + intentionId + ", autoConfirmed=" + autoConfirmed);

                result.put("status", it.getStatus().name());
                result.put("autoConfirmed", autoConfirmed);
            }
            case "waitlist" -> {
                // 查同 roomId 且 status=递补 的所有意向
                List<Intention> waitlistAll = intentionMapper.selectList(
                        new LambdaQueryWrapper<Intention>()
                                .eq(Intention::getRoomId, it.getRoomId())
                                .eq(Intention::getStatus, IntentionStatus.递补));
                int maxRank = waitlistAll.stream()
                        .mapToInt(i -> i.getWaitlistRank() != null ? i.getWaitlistRank() : 0)
                        .max()
                        .orElse(0);
                int newRank = maxRank + 1;

                it.setStatus(IntentionStatus.递补);
                it.setWaitlistRank(newRank);
                it.setUpdateTime(LocalDateTime.now());
                intentionMapper.updateById(it);

                writeOperationLog("teacher", user.getUserId(), it.getTeacherId(), "makeDecision:waitlist",
                        "intentionId=" + intentionId + ", rank=" + newRank);

                result.put("status", it.getStatus().name());
                result.put("rank", newRank);
            }
            case "reject" -> {
                it.setStatus(IntentionStatus.未录取);
                it.setUpdateTime(LocalDateTime.now());
                intentionMapper.updateById(it);

                writeOperationLog("teacher", user.getUserId(), it.getTeacherId(), "makeDecision:reject",
                        "intentionId=" + intentionId);

                result.put("status", it.getStatus().name());
            }
        }

        return result;
    }

    // ---------- 公共工具方法 ----------

    /** 装饰意向：附带学生信息、实训室信息、面试评分 */
    private Map<String, Object> decorateIntention(Intention it) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", it.getId());
        m.put("teacherId", it.getTeacherId());
        m.put("teacherNameSnapshot", it.getTeacherNameSnapshot());
        m.put("teacherNoSnapshot", it.getTeacherNoSnapshot());
        m.put("roomId", it.getRoomId());
        m.put("roomNameSnapshot", it.getRoomNameSnapshot());
        m.put("priority", it.getPriority());
        m.put("intro", it.getIntro());
        m.put("round", it.getRound());
        m.put("status", it.getStatus() != null ? it.getStatus().name() : null);
        m.put("waitlistRank", it.getWaitlistRank());
        m.put("submitTime", ldtToMs(it.getSubmitTime()));

        // 学生
        Student s = studentMapper.selectById(it.getStudentId());
        Map<String, Object> studentMap = new LinkedHashMap<>();
        if (s != null) {
            studentMap.put("id", s.getId());
            studentMap.put("studentNo", s.getStudentNo());
            studentMap.put("name", s.getName());
            studentMap.put("major", s.getMajor());
            studentMap.put("grade", s.getGrade());
        } else {
            studentMap.put("id", it.getStudentId());
        }
        m.put("student", studentMap);

        // 实训室信息（quota + used）
        TrainingRoom room = trainingRoomMapper.selectById(it.getRoomId());
        int used = trainingRoomMapper.countOccupied(it.getRoomId());
        Map<String, Object> roomMap = new LinkedHashMap<>();
        roomMap.put("id", it.getRoomId());
        roomMap.put("name", room != null ? room.getName() : null);
        roomMap.put("quota", room != null ? room.getQuota() : 0);
        roomMap.put("used", used);
        m.put("room", roomMap);

        // 面试
        Interview iv = interviewMapper.findByIntention(it.getId());
        if (iv != null) {
            Map<String, Object> ivMap = new LinkedHashMap<>();
            ivMap.put("attitude", iv.getAttitudeScore());
            ivMap.put("logic", iv.getLogicScore());
            ivMap.put("coding", iv.getCodingScore());
            ivMap.put("commitment", iv.getCommitmentScore());
            ivMap.put("total", iv.getTotal());
            ivMap.put("comment", iv.getComment());
            ivMap.put("createdAt", ldtToMs(iv.getCreateTime()));
            m.put("interview", ivMap);
        } else {
            m.put("interview", null);
        }

        m.put("window", windowOf("teacher_operation"));
        return m;
    }

    /** 若该学生只有一个拟录取，则自动确认该意向，返回是否发生了自动确认。 */
    private boolean autoConfirmIfSingle(Long studentId) {
        List<Intention> pendingConfirms = intentionMapper.selectList(
                new LambdaQueryWrapper<Intention>()
                        .eq(Intention::getStudentId, studentId)
                        .eq(Intention::getStatus, IntentionStatus.拟录取));
        if (pendingConfirms.size() == 1) {
            Intention target = pendingConfirms.get(0);
            target.setStatus(IntentionStatus.已确认);
            target.setUpdateTime(LocalDateTime.now());
            intentionMapper.updateById(target);

            AdmissionConfirmation ac = new AdmissionConfirmation();
            ac.setStudentId(studentId);
            ac.setIntentionId(target.getId());
            ac.setMode(ConfirmMode.auto);
            ac.setConfirmTime(LocalDateTime.now());
            confirmationMapper.insert(ac);
            return true;
        }
        return false;
    }

    /** 时间窗口占位信息（复用 StudentService 语义）。 */
    private Map<String, Object> windowOf(String key) {
        Map<String, Object> w = new HashMap<>();
        w.put("key", key);
        w.put("open", true);
        w.put("reason", null);
        return w;
    }

    // ---------- 私有辅助 ----------

    private long ldtToMs(LocalDateTime ldt) {
        return ldt == null ? 0L : ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void validateScore(String field, Integer val) {
        if (val == null || val < 1 || val > 10) {
            throw new BizException(422, "分数必须是 1-10 之间的整数：" + field);
        }
    }

    private Map<String, Object> buildTeacherProfile(Teacher t) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", t.getId());
        p.put("name", t.getName());
        p.put("no", t.getTeacherNo());
        p.put("title", t.getTitle());
        p.put("roomId", t.getRoomId());
        return p;
    }

    private void writeOperationLog(String operatorRole, Long operatorId, Long _teacherId,
                                   String action, String detail) {
        OperationLog logEntry = new OperationLog();
        logEntry.setOperatorRole(operatorRole);
        logEntry.setOperatorId(operatorId);
        logEntry.setAction(action);
        logEntry.setDetail(detail);
        logEntry.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(logEntry);
    }
}
