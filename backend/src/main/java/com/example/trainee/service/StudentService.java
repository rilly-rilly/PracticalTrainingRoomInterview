package com.example.trainee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
 * 学生端业务：意向填报 / 状态查询 / 确认入组 / 调剂 / 最终结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final IntentionMapper intentionMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final TrainingRoomMapper trainingRoomMapper;
    private final ScheduleItemMapper scheduleItemMapper;
    private final InterviewMapper interviewMapper;
    private final AdmissionConfirmationMapper confirmationMapper;
    private final OperationLogMapper operationLogMapper;
    private final AdjustmentRecordMapper adjustmentRecordMapper;
    private final PublicityResultMapper publicityResultMapper;

    // ---------- 时间工具 ----------

    private static long toEpochMilli(LocalDateTime ldt) {
        return ldt == null ? 0 : ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    // ================================================================
    // 1. listMyIntentions
    // ================================================================

    public List<Map<String, Object>> listMyIntentions() {
        Long userId = TokenInterceptor.AuthContext.get().getUserId();
        List<Intention> list = intentionMapper.listByStudent(userId);
        return list.stream().map(this::decorateIntention).collect(Collectors.toList());
    }

    // ================================================================
    // 2. submitIntentions
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> submitIntentions(List<Long> choices, String intro,
                                                     Boolean acceptInnerAdjust, Boolean acceptGlobalAdjust) {
        LoginUser loginUser = TokenInterceptor.AuthContext.get();
        Long userId = loginUser.getUserId();

        // 校验窗口
        Map<String, Object> windowObj = windowOf("intent");
        if (windowObj == null || !Boolean.TRUE.equals(windowObj.get("isOpen"))) {
            throw new BizException(410, "意向填报时间窗口已关闭", Map.of("window", windowObj));
        }

        // 校验 choices
        if (choices == null || choices.isEmpty() || choices.size() > 3) {
            throw new BizException(400, "请选择 1-3 个志愿导师");
        }
        Set<Long> dedup = new HashSet<>(choices);
        if (dedup.size() != choices.size()) {
            throw new BizException(400, "志愿导师不能重复");
        }

        // 校验 intro
        String trimmedIntro = intro == null ? "" : intro.trim();
        if (trimmedIntro.length() < 20) {
            throw new BizException(400, "自我介绍至少 20 字");
        }

        // 校验是否已经提交 round=1
        Long count = intentionMapper.selectCount(
                new LambdaQueryWrapper<Intention>()
                        .eq(Intention::getStudentId, userId)
                        .eq(Intention::getRound, 1));
        if (count != null && count > 0) {
            throw new BizException(422, "本轮意向已提交，不可重复填报");
        }

        // 校验每个 teacherId 存在
        List<Teacher> teachers = new ArrayList<>();
        for (Long teacherId : choices) {
            Teacher t = teacherMapper.selectById(teacherId);
            if (t == null) {
                throw new BizException(400, "导师不存在: " + teacherId);
            }
            teachers.add(t);
        }

        // 生成意向
        boolean innerAdj = Boolean.TRUE.equals(acceptInnerAdjust);
        boolean globalAdj = Boolean.TRUE.equals(acceptGlobalAdjust);
        LocalDateTime submitTime = now();

        for (int i = 0; i < choices.size(); i++) {
            Teacher t = teachers.get(i);
            TrainingRoom room = t.getRoomId() != null ? trainingRoomMapper.selectById(t.getRoomId()) : null;

            Intention it = new Intention();
            it.setStudentId(userId);
            it.setTeacherId(t.getId());
            it.setRoomId(t.getRoomId());
            it.setTeacherNameSnapshot(t.getName());
            it.setTeacherNoSnapshot(t.getTeacherNo());
            it.setRoomNameSnapshot(room != null ? room.getName() : null);
            it.setPriority(i + 1);
            it.setIntro(trimmedIntro);
            it.setAcceptInnerAdjust(innerAdj ? 1 : 0);
            it.setAcceptGlobalAdjust(globalAdj ? 1 : 0);
            it.setRound(1);
            it.setStatus(IntentionStatus.待面试);
            it.setSubmitTime(submitTime);
            it.setWaitlistRank(null);
            it.setUpdateTime(submitTime);
            intentionMapper.insert(it);
        }

        // OperationLog
        Student s = studentMapper.selectById(userId);
        writeLog("student", userId, s != null ? s.getName() : null,
                "提交意向", "志愿数=" + choices.size() + ", acceptInner=" + innerAdj + ", acceptGlobal=" + globalAdj);

        return listMyIntentions();
    }

    // ================================================================
    // 3. status
    // ================================================================

    public Map<String, Object> status() {
        LoginUser loginUser = TokenInterceptor.AuthContext.get();
        Long userId = loginUser.getUserId();

        List<Map<String, Object>> intentions = listMyIntentions();

        // summary
        long admits = intentions.stream()
                .filter(m -> IntentionStatus.拟录取.name().equals(m.get("status"))).count();
        long confirmed = intentions.stream()
                .filter(m -> IntentionStatus.已确认.name().equals(m.get("status"))).count();

        // accept flags from original entity
        List<Intention> raw = intentionMapper.listByStudent(userId);
        boolean acceptInner = raw.stream().anyMatch(i -> i.getAcceptInnerAdjust() != null && i.getAcceptInnerAdjust() == 1);
        boolean acceptGlobal = raw.stream().anyMatch(i -> i.getAcceptGlobalAdjust() != null && i.getAcceptGlobalAdjust() == 1);

        // confirmedIntentionId
        Long confirmedIntentionId = raw.stream()
                .filter(i -> i.getStatus() == IntentionStatus.已确认)
                .map(Intention::getId).findFirst().orElse(null);

        // autoConfirmed
        boolean autoConfirmed = confirmationMapper.selectList(
                new LambdaQueryWrapper<AdmissionConfirmation>()
                        .eq(AdmissionConfirmation::getStudentId, userId)
                        .eq(AdmissionConfirmation::getMode, ConfirmMode.auto)
        ).size() > 0;

        // hasWaitlist
        boolean hasWaitlist = raw.stream().anyMatch(i -> i.getStatus() == IntentionStatus.递补);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", intentions.size());
        summary.put("totalIntentions", intentions.size());
        summary.put("admits", admits);
        summary.put("confirmed", confirmed);
        summary.put("needChoose", admits >= 2);
        summary.put("autoConfirmed", autoConfirmed);
        summary.put("confirmedIntentionId", confirmedIntentionId);
        summary.put("hasWaitlist", hasWaitlist);
        summary.put("waitlistRank", raw.stream()
                .filter(i -> i.getStatus() == IntentionStatus.递补 && i.getWaitlistRank() != null)
                .map(Intention::getWaitlistRank)
                .collect(Collectors.toList()));
        summary.put("acceptInnerAdjust", acceptInner);
        summary.put("acceptGlobalAdjust", acceptGlobal);

        // confirm window
        Map<String, Object> confirmWindow = windowOf("confirm");

        // confirmations
        List<AdmissionConfirmation> confirmations = confirmationMapper.selectList(
                new LambdaQueryWrapper<AdmissionConfirmation>()
                        .eq(AdmissionConfirmation::getStudentId, userId)
        );
        List<Map<String, Object>> confirmationsView = confirmations.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("intentionId", c.getIntentionId());
            m.put("mode", c.getMode() != null ? c.getMode().name() : null);
            m.put("confirmTime", toEpochMilli(c.getConfirmTime()));
            return m;
        }).collect(Collectors.toList());

        // adjustments
        List<AdjustmentRecord> adjustments = adjustmentRecordMapper.listByStudent(userId);
        List<Map<String, Object>> adjustmentsView = adjustments.stream().map(this::adjustmentRecordToMap).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intentions", intentions);
        result.put("summary", summary);
        result.put("confirmWindow", confirmWindow);
        result.put("confirmations", confirmationsView);
        result.put("adjustments", adjustmentsView);
        return result;
    }

    // ================================================================
    // 4. confirm
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirm(Long intentionId) {
        LoginUser loginUser = TokenInterceptor.AuthContext.get();
        Long userId = loginUser.getUserId();

        Intention target = intentionMapper.selectById(intentionId);
        if (target == null) {
            throw new BizException(404, "意向不存在");
        }
        if (!target.getStudentId().equals(userId)) {
            throw new BizException(403, "无权操作");
        }
        if (target.getStatus() != IntentionStatus.拟录取) {
            throw new BizException(422, "当前意向状态不可确认");
        }

        // 若有 2+ 拟录取，校验 confirm 窗口
        Long admitCount = intentionMapper.selectCount(
                new LambdaQueryWrapper<Intention>()
                        .eq(Intention::getStudentId, userId)
                        .eq(Intention::getStatus, IntentionStatus.拟录取));
        if (admitCount != null && admitCount >= 2) {
            Map<String, Object> win = windowOf("confirm");
            if (win == null || !Boolean.TRUE.equals(win.get("isOpen"))) {
                throw new BizException(410, "确认时间窗口已关闭", Map.of("window", win));
            }
        }

        LocalDateTime now = now();

        // target -> 已确认
        target.setStatus(IntentionStatus.已确认);
        target.setUpdateTime(now);
        intentionMapper.updateById(target);

        AdmissionConfirmation conf = new AdmissionConfirmation();
        conf.setStudentId(userId);
        conf.setIntentionId(target.getId());
        conf.setMode(ConfirmMode.manual);
        conf.setConfirmTime(now);
        confirmationMapper.insert(conf);

        // 其他同 studentId 且 status=拟录取 的 -> 已放弃
        List<Intention> others = intentionMapper.selectList(
                new LambdaQueryWrapper<Intention>()
                        .eq(Intention::getStudentId, userId)
                        .eq(Intention::getStatus, IntentionStatus.拟录取)
                        .ne(Intention::getId, target.getId()));

        List<Long> givenUpIds = new ArrayList<>();
        for (Intention o : others) {
            o.setStatus(IntentionStatus.已放弃);
            o.setUpdateTime(now);
            intentionMapper.updateById(o);

            AdmissionConfirmation gc = new AdmissionConfirmation();
            gc.setStudentId(userId);
            gc.setIntentionId(o.getId());
            gc.setMode(ConfirmMode.give_up);
            gc.setConfirmTime(now);
            confirmationMapper.insert(gc);

            givenUpIds.add(o.getId());
        }

        // OperationLog
        Student s = studentMapper.selectById(userId);
        writeLog("student", userId, s != null ? s.getName() : null,
                "确认入组", "confirmed=" + target.getId() + ", givenUp=" + givenUpIds);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("confirmedIntentionId", target.getId());
        result.put("givenUp", givenUpIds);
        return result;
    }

    // ================================================================
    // 5. adjustments
    // ================================================================

    public Map<String, Object> adjustments() {
        LoginUser loginUser = TokenInterceptor.AuthContext.get();
        Long userId = loginUser.getUserId();

        List<AdjustmentRecord> records = adjustmentRecordMapper.listByStudent(userId);
        List<Intention> raw = intentionMapper.listByStudent(userId);

        // accept flags
        boolean acceptInner = raw.stream().anyMatch(i -> i.getAcceptInnerAdjust() != null && i.getAcceptInnerAdjust() == 1);
        boolean acceptGlobal = raw.stream().anyMatch(i -> i.getAcceptGlobalAdjust() != null && i.getAcceptGlobalAdjust() == 1);

        // rejected: 全部未录取
        boolean rejected = !raw.isEmpty() && raw.stream().allMatch(i -> i.getStatus() == IntentionStatus.未录取);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalIntentions", raw.size());
        summary.put("rejected", rejected);
        summary.put("acceptInnerAdjust", acceptInner);
        summary.put("acceptGlobalAdjust", acceptGlobal);

        List<Map<String, Object>> list = records.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("round", a.getRound());
            m.put("type", a.getType() != null ? a.getType().name() : null);
            m.put("fromRoom", a.getFromRoomSnapshot());
            m.put("toRoom", a.getToRoomSnapshot());
            m.put("toTeacher", a.getToTeacherSnapshot());
            m.put("result", a.getResultSnapshot());
            m.put("createTime", toEpochMilli(a.getCreateTime()));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("records", list);
        return result;
    }

    // ================================================================
    // 6. finalResult
    // ================================================================

    public Map<String, Object> finalResult() {
        LoginUser loginUser = TokenInterceptor.AuthContext.get();
        Long userId = loginUser.getUserId();

        List<Intention> raw = intentionMapper.listByStudent(userId);
        List<Map<String, Object>> intentions = raw.stream().map(this::decorateIntention).collect(Collectors.toList());

        // 找到已确认或已录取的目标意向
        Intention targetIntention = raw.stream()
                .filter(i -> i.getStatus() == IntentionStatus.已确认 || i.getStatus() == IntentionStatus.已录取)
                .findFirst().orElse(null);

        PublicityResult publicity = publicityResultMapper.findByStudent(userId);

        // 计算 stage / finalStatus
        String stage;
        String finalStatus;

        if (targetIntention != null) {
            if (targetIntention.getStatus() == IntentionStatus.已录取) {
                stage = "已录取";
                finalStatus = "已录取";
            } else if (publicity != null) {
                if (publicity.getStatus() == PublicityStatus.PUBLISHING) {
                    stage = "公示中";
                    finalStatus = "公示中";
                } else {
                    stage = "待公示";
                    finalStatus = "待公示";
                }
            } else {
                stage = "考核进行中";
                finalStatus = "考核进行中";
            }
        } else {
            // 没有 confirmed/enrolled
            boolean waitlist = raw.stream().anyMatch(i -> i.getStatus() == IntentionStatus.递补);
            boolean hasAdmit = raw.stream().anyMatch(i -> i.getStatus() == IntentionStatus.拟录取);
            boolean allRejected = !raw.isEmpty() && raw.stream().allMatch(i -> i.getStatus() == IntentionStatus.未录取
                    || i.getStatus() == IntentionStatus.已放弃);
            boolean submitted = !raw.isEmpty();

            if (waitlist) {
                stage = "递补中";
                finalStatus = "递补中";
            } else if (hasAdmit) {
                stage = "待确认";
                finalStatus = "待确认";
            } else if (allRejected) {
                // 看是否接受调剂
                boolean acceptGlobal = raw.stream().anyMatch(i -> i.getAcceptGlobalAdjust() != null && i.getAcceptGlobalAdjust() == 1);
                stage = acceptGlobal ? "等待调剂" : "未录取";
                finalStatus = acceptGlobal ? "等待调剂" : "未录取";
            } else if (submitted) {
                stage = "考核进行中";
                finalStatus = "考核进行中";
            } else {
                stage = "未填报";
                finalStatus = "未填报";
            }
        }

        // publicity view
        Map<String, Object> publicityView = null;
        if (publicity != null) {
            publicityView = new LinkedHashMap<>();
            publicityView.put("id", publicity.getId());
            publicityView.put("studentId", publicity.getStudentId());
            publicityView.put("intentionId", publicity.getIntentionId());
            publicityView.put("room", publicity.getRoomSnapshot());
            publicityView.put("teacher", publicity.getTeacherSnapshot());
            publicityView.put("startAt", toEpochMilli(publicity.getStartTime()));
            publicityView.put("endAt", toEpochMilli(publicity.getEndTime()));
            publicityView.put("status", publicity.getStatus() != null ? publicity.getStatus().name() : null);
        }

        // adjustments
        List<AdjustmentRecord> adjustments = adjustmentRecordMapper.listByStudent(userId);
        List<Map<String, Object>> adjustmentsView = adjustments.stream().map(this::adjustmentRecordToMap).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", stage);
        result.put("finalStatus", finalStatus);
        result.put("intention", targetIntention != null ? decorateIntention(targetIntention) : null);
        result.put("publicity", publicityView);
        result.put("intentions", intentions);
        result.put("adjustments", adjustmentsView);
        return result;
    }

    // ================================================================
    // 7. decorateIntention
    // ================================================================

    public Map<String, Object> decorateIntention(Intention it) {
        Map<String, Object> m = new LinkedHashMap<>();

        // student info
        Student s = it.getStudentId() != null ? studentMapper.selectById(it.getStudentId()) : null;
        m.put("id", it.getId());
        m.put("studentId", it.getStudentId());
        m.put("studentNo", s != null ? s.getStudentNo() : null);
        m.put("studentName", s != null ? s.getName() : null);
        m.put("major", s != null ? s.getMajor() : null);
        m.put("grade", s != null ? s.getGrade() : null);

        // teacher info (优先用快照)
        m.put("teacherId", it.getTeacherId());
        m.put("teacherNo", it.getTeacherNoSnapshot());
        m.put("teacherName", it.getTeacherNameSnapshot());

        // room info
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

        // interview
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

    // ================================================================
    // 8. windowOf
    // ================================================================

    public Map<String, Object> windowOf(String key) {
        ScheduleItem item = scheduleItemMapper.selectOne(
                new LambdaQueryWrapper<ScheduleItem>().eq(ScheduleItem::getKeyValue, key));
        if (item == null) return null;

        LocalDateTime now = now();
        boolean isOpen = item.getStartTime() != null && item.getEndTime() != null
                && !now.isBefore(item.getStartTime()) && !now.isAfter(item.getEndTime());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("step", item.getStepNo());
        m.put("title", item.getTitle());
        m.put("startAt", toEpochMilli(item.getStartTime()));
        m.put("endAt", toEpochMilli(item.getEndTime()));
        m.put("isOpen", isOpen);
        return m;
    }

    // ================================================================
    // 9. autoConfirmIfSingle
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public boolean autoConfirmIfSingle(Long studentId) {
        List<Intention> admits = intentionMapper.selectList(
                new LambdaQueryWrapper<Intention>()
                        .eq(Intention::getStudentId, studentId)
                        .eq(Intention::getStatus, IntentionStatus.拟录取));
        if (admits.size() == 1) {
            Intention target = admits.get(0);
            LocalDateTime now = now();
            target.setStatus(IntentionStatus.已确认);
            target.setUpdateTime(now);
            intentionMapper.updateById(target);

            AdmissionConfirmation conf = new AdmissionConfirmation();
            conf.setStudentId(studentId);
            conf.setIntentionId(target.getId());
            conf.setMode(ConfirmMode.auto);
            conf.setConfirmTime(now);
            confirmationMapper.insert(conf);
            return true;
        }
        return false;
    }

    // ================================================================
    // 辅助
    // ================================================================

    private void writeLog(String operatorRole, Long operatorId, String operatorName, String action, String detail) {
        OperationLog log = new OperationLog();
        log.setOperatorRole(operatorRole);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setAction(action);
        log.setDetail(detail);
        log.setCreateTime(now());
        operationLogMapper.insert(log);
    }

    /** AdjustmentRecord -> 前端期望的 Map（统一字段名，status 和 adjustments 共用） */
    private Map<String, Object> adjustmentRecordToMap(AdjustmentRecord a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("round", a.getRound());
        String typeName = a.getType() != null ? a.getType().name() : null;
        m.put("type", typeName);
        m.put("typeLabel", switch (typeName) {
            case "inner" -> "实训室内部调剂";
            case "global" -> "全院跨实训室调剂";
            case "supplement" -> "分轮补录";
            case "interview" -> "约谈";
            default -> typeName;
        });
        m.put("fromRoomName", a.getFromRoomSnapshot());
        m.put("toRoomName", a.getToRoomSnapshot());
        m.put("toTeacherName", a.getToTeacherSnapshot());
        m.put("result", a.getResultSnapshot());
        m.put("createdAt", toEpochMilli(a.getCreateTime()));
        return m;
    }
}
