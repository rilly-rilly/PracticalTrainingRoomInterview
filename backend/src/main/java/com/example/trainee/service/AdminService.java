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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员端业务：导入 / 时间窗口 / 公告 / 录取总览 / 名额调整 / 逾期放弃与自动补录
 * / 调剂总览与执行 / 公示发布与结束 / 操作日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final PasswordEncoder encoder;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final TrainingRoomMapper trainingRoomMapper;
    private final IntentionMapper intentionMapper;
    private final InterviewMapper interviewMapper;
    private final AdmissionConfirmationMapper confirmationMapper;
    private final AdjustmentRecordMapper adjustmentRecordMapper;
    private final PublicityResultMapper publicityResultMapper;
    private final ScheduleItemMapper scheduleItemMapper;
    private final NoticeMapper noticeMapper;
    private final OperationLogMapper operationLogMapper;
    private final ImportBatchMapper importBatchMapper;
    private final IntentionDecorator decorator;

    // ---------- 时间工具 ----------

    private static long toEpochMilli(LocalDateTime ldt) {
        return ldt == null ? 0 : ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static String randomHex(int len) {
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < len; i++) sb.append(Integer.toHexString(rnd.nextInt(16)));
        return sb.toString();
    }

    // ================================================================
    // 通用工具（private）
    // ================================================================

    private void writeLog(String operatorRole, Long operatorId, String operatorName, String action, String detail) {
        OperationLog logEntry = new OperationLog();
        logEntry.setOperatorRole(operatorRole);
        logEntry.setOperatorId(operatorId);
        logEntry.setOperatorName(operatorName);
        logEntry.setAction(action);
        logEntry.setDetail(detail);
        logEntry.setCreateTime(now());
        operationLogMapper.insert(logEntry);
    }

    private Map<String, Object> windowOf(String key) {
        ScheduleItem item = scheduleItemMapper.selectOne(
                new LambdaQueryWrapper<ScheduleItem>().eq(ScheduleItem::getKeyValue, key));
        if (item == null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("step", 0);
            m.put("title", null);
            m.put("startAt", 0L);
            m.put("endAt", 0L);
            m.put("isOpen", false);
            return m;
        }
        LocalDateTime n = now();
        boolean isOpen = item.getStartTime() != null && item.getEndTime() != null
                && !n.isBefore(item.getStartTime()) && !n.isAfter(item.getEndTime());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("step", item.getStepNo());
        m.put("title", item.getTitle());
        m.put("startAt", toEpochMilli(item.getStartTime()));
        m.put("endAt", toEpochMilli(item.getEndTime()));
        m.put("isOpen", isOpen);
        return m;
    }

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
    // 1. importData
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importData(String type, List<Map<String, String>> rows, boolean strict) {
        if (!"student".equals(type) && !"teacher".equals(type)) {
            throw new BizException(400, "type 只能是 student 或 teacher");
        }

        List<Map<String, Object>> failures = new ArrayList<>();
        List<Map<String, Object>> accounts = new ArrayList<>();

        int imported = 0;
        int skipped = 0;

        // 同批次去重
        Set<String> seenNos = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int line = i + 2; // 1-based，跳过表头
            List<String> reasons = new ArrayList<>();

            String name = row.getOrDefault("姓名", "").trim();
            String no = row.getOrDefault("学号", row.getOrDefault("工号", "")).trim();

            // 必填
            if (name.isEmpty()) reasons.add("姓名必填");
            if (no.isEmpty()) reasons.add("工号/学号必填");

            // 批次内重复
            if (!no.isEmpty() && !seenNos.add(no)) {
                reasons.add("同文件内工号/学号重复");
            }

            switch (type) {
                case "student" -> {
                    String major = row.getOrDefault("专业", "").trim();
                    String grade = row.getOrDefault("年级", "").trim();
                    if (major.isEmpty()) reasons.add("专业必填");
                    if (grade.isEmpty()) reasons.add("年级必填");
                    if (!no.isEmpty() && studentMapper.findByNo(no) != null) {
                        reasons.add("学号已存在");
                    }
                }
                case "teacher" -> {
                    String title = row.getOrDefault("职称", "").trim();
                    String roomCode = row.getOrDefault("实训室编号", "").trim();
                    if (!no.isEmpty() && teacherMapper.findByNo(no) != null) {
                        reasons.add("工号已存在");
                    }
                    if (roomCode.isEmpty()) {
                        reasons.add("实训室编号必填");
                    } else {
                        TrainingRoom room = trainingRoomMapper.findByCode(roomCode);
                        if (room == null) reasons.add("实训室编号不存在: " + roomCode);
                    }
                }
            }

            if (!reasons.isEmpty()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("line", line);
                f.put("no", no);
                f.put("name", name);
                f.put("reasons", reasons);
                failures.add(f);

                if (strict) {
                    throw new BizException(422, "导入校验失败",
                            Map.of("imported", imported, "skipped", skipped,
                                    "failures", failures, "strict", true));
                }
                skipped++;
                continue;
            }

            // 入库
            String password;
            switch (type) {
                case "student" -> {
                    Student s = new Student();
                    s.setStudentNo(no);
                    s.setName(name);
                    s.setMajor(row.getOrDefault("专业", "").trim());
                    s.setGrade(row.getOrDefault("年级", "").trim());
                    password = "Stu@" + randomHex(6);
                    s.setPasswordHash(encoder.encode(password));
                    s.setCreateTime(now());
                    studentMapper.insert(s);

                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("no", no);
                    a.put("name", name);
                    a.put("password", password);
                    accounts.add(a);
                }
                case "teacher" -> {
                    Teacher t = new Teacher();
                    t.setTeacherNo(no);
                    t.setName(name);
                    t.setTitle(row.getOrDefault("职称", "").trim());
                    String roomCode = row.getOrDefault("实训室编号", "").trim();
                    TrainingRoom room = trainingRoomMapper.findByCode(roomCode);
                    t.setRoomId(room != null ? room.getId() : null);
                    password = "Teach@" + randomHex(6);
                    t.setPasswordHash(encoder.encode(password));
                    t.setCreateTime(now());
                    t.setUpdateTime(now());
                    teacherMapper.insert(t);

                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("no", no);
                    a.put("name", name);
                    a.put("password", password);
                    a.put("roomName", room != null ? room.getName() : null);
                    accounts.add(a);
                }
                default -> throw new BizException(400, "未知 type");
            }
            imported++;
        }

        // ImportBatch 记录
        ImportBatch batch = new ImportBatch();
        batch.setType(type);
        batch.setBatchNo(UUID.randomUUID().toString());
        batch.setTotalRows(rows.size());
        batch.setImported(imported);
        batch.setSkipped(skipped);
        batch.setStrictMode(strict ? 1 : 0);
        batch.setCreateTime(now());
        importBatchMapper.insert(batch);

        // OperationLog
        LoginUser loginUser = TokenInterceptor.AuthContext.getOrNull();
        writeLog(
                loginUser != null ? loginUser.getRole() : "admin",
                loginUser != null ? loginUser.getUserId() : null,
                "admin",
                "导入" + type,
                "total=" + rows.size() + ", imported=" + imported + ", skipped=" + skipped
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("failures", failures);
        result.put("accounts", accounts);
        result.put("strict", strict);
        result.put("batchId", batch.getId());
        return result;
    }

    // ================================================================
    // 2. listSchedules
    // ================================================================

    public List<Map<String, Object>> listSchedules() {
        List<ScheduleItem> list = scheduleItemMapper.selectList(null);
        list.sort(Comparator.comparingInt(ScheduleItem::getStepNo));
        return list.stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId());
            m.put("stepNo", item.getStepNo());
            m.put("keyValue", item.getKeyValue());
            m.put("title", item.getTitle());
            m.put("description", item.getDescription());
            m.put("startAt", toEpochMilli(item.getStartTime()));
            m.put("endAt", toEpochMilli(item.getEndTime()));
            m.put("updateTime", toEpochMilli(item.getUpdateTime()));
            return m;
        }).collect(Collectors.toList());
    }

    // ================================================================
    // 3. updateSchedule
    // ================================================================

    public Map<String, Object> updateSchedule(Long id, String title, String description,
                                              Long startAtMs, Long endAtMs) {
        ScheduleItem item = scheduleItemMapper.selectById(id);
        if (item == null) throw new BizException(404, "时间节点不存在");

        if (startAtMs != null && endAtMs != null && startAtMs >= endAtMs) {
            throw new BizException(422, "开始时间必须早于结束时间");
        }

        LocalDateTime start = startAtMs != null
                ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(startAtMs), ZoneId.systemDefault())
                : item.getStartTime();
        LocalDateTime end = endAtMs != null
                ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endAtMs), ZoneId.systemDefault())
                : item.getEndTime();

        if (start == null || end == null || start.isAfter(end)) {
            throw new BizException(422, "时间不合法");
        }

        LambdaUpdateWrapper<ScheduleItem> uw = new LambdaUpdateWrapper<>();
        uw.eq(ScheduleItem::getId, id)
                .set(ScheduleItem::getTitle, title != null ? title : item.getTitle())
                .set(ScheduleItem::getDescription, description != null ? description : item.getDescription())
                .set(ScheduleItem::getStartTime, start)
                .set(ScheduleItem::getEndTime, end)
                .set(ScheduleItem::getUpdateTime, now());
        scheduleItemMapper.update(null, uw);

        ScheduleItem updated = scheduleItemMapper.selectById(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", updated.getId());
        m.put("stepNo", updated.getStepNo());
        m.put("keyValue", updated.getKeyValue());
        m.put("title", updated.getTitle());
        m.put("description", updated.getDescription());
        m.put("startAt", toEpochMilli(updated.getStartTime()));
        m.put("endAt", toEpochMilli(updated.getEndTime()));
        m.put("updateTime", toEpochMilli(updated.getUpdateTime()));

        LoginUser loginUser = TokenInterceptor.AuthContext.getOrNull();
        writeLog(
                loginUser != null ? loginUser.getRole() : "admin",
                loginUser != null ? loginUser.getUserId() : null,
                "admin",
                "更新时间节点",
                "id=" + id + ", title=" + updated.getTitle()
        );

        return m;
    }

    // ================================================================
    // 4. listNotices
    // ================================================================

    public List<Map<String, Object>> listNotices() {
        List<Notice> list = noticeMapper.selectList(null);
        list.sort((a, b) -> {
            if (a.getPublishTime() == null && b.getPublishTime() == null) return 0;
            if (a.getPublishTime() == null) return 1;
            if (b.getPublishTime() == null) return -1;
            return b.getPublishTime().compareTo(a.getPublishTime());
        });
        return list.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("level", n.getLevel());
            m.put("publishedAt", toEpochMilli(n.getPublishTime()));
            return m;
        }).collect(Collectors.toList());
    }

    // ================================================================
    // 5. createNotice
    // ================================================================

    public Map<String, Object> createNotice(String title, String content, String level) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new BizException(422, "标题和内容不能为空");
        }

        Notice n = new Notice();
        n.setTitle(title.trim());
        n.setContent(content.trim());
        n.setLevel(level != null && !level.isBlank() ? level.trim() : "普通");
        n.setPublishTime(now());
        noticeMapper.insert(n);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("content", n.getContent());
        m.put("level", n.getLevel());
        m.put("publishedAt", toEpochMilli(n.getPublishTime()));

        LoginUser loginUser = TokenInterceptor.AuthContext.getOrNull();
        writeLog(
                loginUser != null ? loginUser.getRole() : "admin",
                loginUser != null ? loginUser.getUserId() : null,
                "admin",
                "发布公告",
                "id=" + n.getId() + ", title=" + n.getTitle()
        );

        return m;
    }

    // ================================================================
    // 6. deleteNotice
    // ================================================================

    public void deleteNotice(Long id) {
        Notice n = noticeMapper.selectById(id);
        if (n == null) throw new BizException(404, "公告不存在");

        noticeMapper.deleteById(id);

        LoginUser loginUser = TokenInterceptor.AuthContext.getOrNull();
        writeLog(
                loginUser != null ? loginUser.getRole() : "admin",
                loginUser != null ? loginUser.getUserId() : null,
                "admin",
                "删除公告",
                "id=" + id + ", title=" + n.getTitle()
        );
    }

    // ================================================================
    // 7. admissionsOverview
    // ================================================================

    public Map<String, Object> admissionsOverview() {
        // 一次查出全部数据，后续在内存里聚合
        List<TrainingRoom> rooms = trainingRoomMapper.selectList(null);
        List<Teacher> teachers = teacherMapper.selectList(null);
        // 构建 roomId -> name 映射，供 teachers 联查
        Map<Long, String> roomNameMap = rooms.stream()
                .collect(Collectors.toMap(TrainingRoom::getId, TrainingRoom::getName, (a, b) -> a));

        List<Intention> allIntentions = intentionMapper.selectList(null);
        // 按 roomId 分组
        Map<Long, List<Intention>> byRoom = allIntentions.stream()
                .collect(Collectors.groupingBy(Intention::getRoomId));
        // 按 teacherId 分组
        Map<Long, List<Intention>> byTeacher = allIntentions.stream()
                .collect(Collectors.groupingBy(Intention::getTeacherId));

        // -------- rooms --------
        List<Map<String, Object>> roomsView = rooms.stream().map(room -> {
            List<Intention> list = byRoom.getOrDefault(room.getId(), Collections.emptyList());
            long admitted = list.stream().filter(i -> i.getStatus() == IntentionStatus.拟录取).count();
            long confirmed = list.stream().filter(i -> i.getStatus() == IntentionStatus.已确认).count();
            long waitlist = list.stream().filter(i -> i.getStatus() == IntentionStatus.递补).count();
            long enrolled = list.stream().filter(i -> i.getStatus() == IntentionStatus.已录取).count();
            int used = trainingRoomMapper.countOccupied(room.getId());

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", room.getId());
            m.put("name", room.getName());
            m.put("code", room.getRoomCode());           // mock 用 code
            m.put("roomCode", room.getRoomCode());       // 兼容已有的 roomCode 引用
            m.put("direction", room.getDirection());
            m.put("quota", room.getQuota());
            m.put("used", used);                         // mock 用 used
            m.put("countOccupied", used);                // 兼容
            m.put("remaining", Math.max(0, room.getQuota() - used)); // mock 有 remaining
            m.put("admitted", admitted);
            m.put("confirmed", confirmed);
            m.put("waitlist", waitlist);
            m.put("enrolled", enrolled);
            return m;
        }).collect(Collectors.toList());

        // -------- teachers --------
        List<Map<String, Object>> teachersView = teachers.stream().map(t -> {
            List<Intention> list = byTeacher.getOrDefault(t.getId(), Collections.emptyList());
            long applied = list.size();
            long admitted = list.stream().filter(i ->
                    i.getStatus() == IntentionStatus.拟录取
                            || i.getStatus() == IntentionStatus.已确认
                            || i.getStatus() == IntentionStatus.已录取).count();
            long waitlist = list.stream().filter(i -> i.getStatus() == IntentionStatus.递补).count();

            String roomName = t.getRoomId() != null ? roomNameMap.getOrDefault(t.getRoomId(), "") : "";

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("teacherNo", t.getTeacherNo());
            m.put("name", t.getName());
            m.put("title", t.getTitle());
            m.put("roomId", t.getRoomId());
            m.put("roomName", roomName);                  // mock 有 roomName
            m.put("applied", applied);
            m.put("admitted", admitted);
            m.put("waitlist", waitlist);
            return m;
        }).collect(Collectors.toList());

        // -------- intentions 装饰 --------
        List<Map<String, Object>> intentionsView = allIntentions.stream()
                .map(decorator::decorate)
                .collect(Collectors.toList());

        // -------- stats：扁平字段 + 保留 statusCounts --------
        long studentsTotal = studentMapper.selectCount(null);
        long teachersTotal = teacherMapper.selectCount(null);
        long intentionsTotal = allIntentions.size();

        long pending = allIntentions.stream().filter(i -> i.getStatus() == IntentionStatus.待面试).count();
        long scored  = allIntentions.stream().filter(i -> i.getStatus() == IntentionStatus.面试完成).count();
        long admittedFlat = allIntentions.stream().filter(i -> i.getStatus() == IntentionStatus.拟录取).count();
        long waitlistFlat = allIntentions.stream().filter(i -> i.getStatus() == IntentionStatus.递补).count();
        long confirmedFlat = allIntentions.stream().filter(i -> i.getStatus() == IntentionStatus.已确认).count();
        long enrolledFlat = allIntentions.stream().filter(i -> i.getStatus() == IntentionStatus.已录取).count();
        long rejected = allIntentions.stream().filter(i ->
                i.getStatus() == IntentionStatus.未录取 || i.getStatus() == IntentionStatus.已放弃).count();

        // 保留 statusCounts（中文 key）给可能的其他消费者
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (IntentionStatus s : IntentionStatus.values()) {
            long cnt = allIntentions.stream().filter(i -> i.getStatus() == s).count();
            statusCounts.put(s.name(), cnt);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("students", studentsTotal);
        stats.put("teachers", teachersTotal);
        stats.put("intentions", intentionsTotal);
        // 前端 Admissions.vue statItems 直接取这些扁平字段
        stats.put("pending", pending);
        stats.put("scored", scored);
        stats.put("admitted", admittedFlat);
        stats.put("waitlist", waitlistFlat);
        stats.put("confirmed", confirmedFlat);
        stats.put("enrolled", enrolledFlat);
        stats.put("rejected", rejected);
        stats.put("statusCounts", statusCounts);  // 兼容保留

        // -------- confirm window --------
        Map<String, Object> confirmWindow = windowOf("confirm");

        // -------- logs 最新 30 --------
        List<OperationLog> logs = operationLogMapper.selectList(null);
        logs.sort((a, b) -> {
            if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });
        List<Map<String, Object>> logsView = logs.stream().limit(30).map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("operatorRole", l.getOperatorRole());
            m.put("operatorId", l.getOperatorId());
            m.put("operatorName", l.getOperatorName());
            m.put("action", l.getAction());
            m.put("detail", l.getDetail());
            m.put("createTime", toEpochMilli(l.getCreateTime()));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rooms", roomsView);
        result.put("teachers", teachersView);
        result.put("intentions", intentionsView);
        result.put("stats", stats);
        result.put("confirmWindow", confirmWindow);
        result.put("logs", logsView);
        return result;
    }

    // ================================================================
    // 8. updateRoomQuota
    // ================================================================

    public Map<String, Object> updateRoomQuota(Long id, Integer quota) {
        TrainingRoom room = trainingRoomMapper.selectById(id);
        if (room == null) throw new BizException(404, "实训室不存在");

        if (quota == null || quota < 1 || quota > 20) {
            throw new BizException(422, "名额必须在 1-20 之间");
        }

        int occupied = trainingRoomMapper.countOccupied(id);
        if (quota < occupied) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("quota", quota);
            data.put("occupied", occupied);
            throw new BizException(409, "新名额不能小于当前已占用名额", data);
        }

        LambdaUpdateWrapper<TrainingRoom> uw = new LambdaUpdateWrapper<>();
        uw.eq(TrainingRoom::getId, id)
                .set(TrainingRoom::getQuota, quota)
                .set(TrainingRoom::getUpdateTime, now());
        trainingRoomMapper.update(null, uw);

        LoginUser loginUser = TokenInterceptor.AuthContext.getOrNull();
        writeLog(
                loginUser != null ? loginUser.getRole() : "admin",
                loginUser != null ? loginUser.getUserId() : null,
                "admin",
                "调整实训室名额",
                "roomId=" + id + ", quota=" + quota
        );

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("quota", quota);
        return m;
    }

    // ================================================================
    // 9. expireConfirmations
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> expireConfirmations(boolean force) {
        List<Intention> pending = intentionMapper.selectList(
                new LambdaQueryWrapper<Intention>()
                        .eq(Intention::getStatus, IntentionStatus.拟录取));

        if (!force) {
            Map<String, Object> win = windowOf("confirm");
            if (win == null || Boolean.TRUE.equals(win.get("isOpen"))) {
                throw new BizException(410, "确认时间窗口尚未关闭，请使用 force=true 强制逾期");
            }
        }

        if (pending.isEmpty()) {
            throw new BizException(422, "没有待逾期的拟录取意向");
        }

        LocalDateTime nt = now();
        // 把所有 pending.status 改为 已放弃
        for (Intention p : pending) {
            p.setStatus(IntentionStatus.已放弃);
            p.setUpdateTime(nt);
            intentionMapper.updateById(p);

            AdmissionConfirmation conf = new AdmissionConfirmation();
            conf.setStudentId(p.getStudentId());
            conf.setIntentionId(p.getId());
            conf.setMode(ConfirmMode.expired);
            conf.setConfirmTime(nt);
            confirmationMapper.insert(conf);
        }

        // affectedRooms
        Set<Long> affectedRooms = pending.stream()
                .map(Intention::getRoomId).filter(Objects::nonNull).collect(Collectors.toSet());

        int promoted = 0;
        Set<Long> affectedStudents = new HashSet<>();

        for (Long roomId : affectedRooms) {
            TrainingRoom room = trainingRoomMapper.selectById(roomId);
            if (room == null) continue;
            while (trainingRoomMapper.countOccupied(roomId) < room.getQuota()) {
                Intention wait = intentionMapper.findNextWaitlist(roomId);
                if (wait == null) break;
                wait.setStatus(IntentionStatus.拟录取);
                wait.setWaitlistRank(null);
                wait.setUpdateTime(nt);
                intentionMapper.updateById(wait);

                boolean auto = autoConfirmIfSingle(wait.getStudentId());
                affectedStudents.add(wait.getStudentId());

                TrainingRoom currentRoom = trainingRoomMapper.selectById(wait.getRoomId());
                AdjustmentRecord rec = new AdjustmentRecord();
                rec.setStudentId(wait.getStudentId());
                rec.setRound(2);
                rec.setType(AdjustmentType.supplement);
                rec.setFromRoomSnapshot("");
                rec.setToRoomSnapshot(currentRoom != null ? currentRoom.getName() : wait.getRoomNameSnapshot());
                rec.setToTeacherSnapshot(wait.getTeacherNameSnapshot());
                rec.setResultSnapshot(auto ? "递补录取并自动确认" : "递补拟录取，待学生确认");
                rec.setOperatorRole("admin");
                rec.setOperatorId(TokenInterceptor.AuthContext.getOrNull() != null
                        ? TokenInterceptor.AuthContext.getOrNull().getUserId() : null);
                rec.setCreateTime(nt);
                adjustmentRecordMapper.insert(rec);

                promoted++;
            }
        }

        LoginUser loginUser = TokenInterceptor.AuthContext.getOrNull();
        writeLog(
                loginUser != null ? loginUser.getRole() : "admin",
                loginUser != null ? loginUser.getUserId() : null,
                "admin",
                "逾期放弃并自动补录",
                "expired=" + pending.size() + ", promoted=" + promoted
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expired", pending.size());
        result.put("promoted", promoted);
        result.put("students", affectedStudents.size());
        return result;
    }

    // ================================================================
    // 10. adjustmentsOverview
    // ================================================================

    public Map<String, Object> adjustmentsOverview() {
        // 所有学生
        List<Student> allStudents = studentMapper.selectList(null);

        // 查所有意向
        List<Intention> allIntentions = intentionMapper.selectList(null);
        // 查所有调剂记录
        List<AdjustmentRecord> allRecords = adjustmentRecordMapper.selectList(null);

        List<Map<String, Object>> studentsView = allStudents.stream().map(s -> {
            List<Intention> myIntentions = allIntentions.stream()
                    .filter(i -> s.getId().equals(i.getStudentId()))
                    .collect(Collectors.toList());

            String statusStr = myIntentions.stream()
                    .map(i -> i.getStatus() != null ? i.getStatus().name() : "null")
                    .collect(Collectors.joining(", "));

            boolean admitted = myIntentions.stream().anyMatch(i -> i.getStatus() == IntentionStatus.拟录取);
            boolean waitlist = myIntentions.stream().anyMatch(i -> i.getStatus() == IntentionStatus.递补);
            boolean rejectedAll = !myIntentions.isEmpty() && myIntentions.stream()
                    .allMatch(i -> i.getStatus() == IntentionStatus.未录取 || i.getStatus() == IntentionStatus.已放弃);
            boolean acceptInnerAdjust = myIntentions.stream()
                    .anyMatch(i -> i.getAcceptInnerAdjust() != null && i.getAcceptInnerAdjust() == 1);
            boolean acceptGlobalAdjust = myIntentions.stream()
                    .anyMatch(i -> i.getAcceptGlobalAdjust() != null && i.getAcceptGlobalAdjust() == 1);
            Set<String> originalRooms = myIntentions.stream()
                    .map(Intention::getRoomNameSnapshot).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            boolean adjusted = allRecords.stream()
                    .anyMatch(r -> s.getId().equals(r.getStudentId()));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", s.getId());
            m.put("studentNo", s.getStudentNo());
            m.put("name", s.getName());
            m.put("major", s.getMajor());
            m.put("grade", s.getGrade());
            m.put("intentionsCount", myIntentions.size());
            m.put("status", statusStr);
            m.put("admitted", admitted);
            m.put("waitlist", waitlist);
            m.put("rejectedAll", rejectedAll);
            m.put("acceptInnerAdjust", acceptInnerAdjust);
            m.put("acceptGlobalAdjust", acceptGlobalAdjust);
            m.put("originalRooms", originalRooms);
            m.put("adjusted", adjusted);
            return m;
        }).collect(Collectors.toList());

        // records 全部按 createTime desc
        List<AdjustmentRecord> sortedRecords = new ArrayList<>(allRecords);
        sortedRecords.sort((a, b) -> {
            if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });
        List<Map<String, Object>> recordsView = sortedRecords.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("studentId", r.getStudentId());
            m.put("round", r.getRound());
            m.put("type", r.getType() != null ? r.getType().name() : null);
            m.put("fromRoomSnapshot", r.getFromRoomSnapshot());
            m.put("toRoomSnapshot", r.getToRoomSnapshot());
            m.put("toTeacherSnapshot", r.getToTeacherSnapshot());
            m.put("resultSnapshot", r.getResultSnapshot());
            m.put("operatorRole", r.getOperatorRole());
            m.put("operatorId", r.getOperatorId());
            m.put("createTime", toEpochMilli(r.getCreateTime()));
            return m;
        }).collect(Collectors.toList());

        // rooms
        List<TrainingRoom> rooms = trainingRoomMapper.selectList(null);
        List<Map<String, Object>> roomsView = rooms.stream().map(room -> {
            int used = trainingRoomMapper.countOccupied(room.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", room.getId());
            m.put("name", room.getName());
            m.put("quota", room.getQuota());
            m.put("used", used);
            m.put("remaining", Math.max(0, room.getQuota() - used));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("students", studentsView);
        result.put("records", recordsView);
        result.put("rooms", roomsView);
        return result;
    }

    // ================================================================
    // 11. runAdjustment
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runAdjustment(String type) {
        if (type == null || Arrays.stream(AdjustmentType.values()).noneMatch(t -> t.name().equals(type))) {
            throw new BizException(400, "type 必须是 inner/global/interview/supplement");
        }

        AdjustmentType adjType = AdjustmentType.valueOf(type);
        LoginUser loginUser = TokenInterceptor.AuthContext.getOrNull();
        LocalDateTime nt = now();
        int affected = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();

        switch (adjType) {

            case supplement -> {
                List<TrainingRoom> rooms = trainingRoomMapper.selectList(null);
                for (TrainingRoom room : rooms) {
                    while (trainingRoomMapper.countOccupied(room.getId()) < room.getQuota()) {
                        Intention wait = intentionMapper.findNextWaitlist(room.getId());
                        if (wait == null) break;
                        wait.setStatus(IntentionStatus.拟录取);
                        wait.setWaitlistRank(null);
                        wait.setUpdateTime(nt);
                        intentionMapper.updateById(wait);

                        boolean auto = autoConfirmIfSingle(wait.getStudentId());

                        AdjustmentRecord rec = new AdjustmentRecord();
                        rec.setStudentId(wait.getStudentId());
                        rec.setRound(2);
                        rec.setType(AdjustmentType.supplement);
                        rec.setFromRoomSnapshot("");
                        rec.setToRoomSnapshot(room.getName());
                        rec.setToTeacherSnapshot(wait.getTeacherNameSnapshot());
                        rec.setResultSnapshot(auto ? "递补录取并自动确认" : "递补拟录取，待学生确认");
                        rec.setOperatorRole("admin");
                        rec.setOperatorId(loginUser != null ? loginUser.getUserId() : null);
                        rec.setCreateTime(nt);
                        adjustmentRecordMapper.insert(rec);

                        affected++;
                    }
                }
            }

            case interview -> {
                // 候选：allRejected AND 每个意向 acceptInnerAdjust=0 AND acceptGlobalAdjust=0 AND 未做过 interview 类型
                List<Long> allRejectedIds = intentionMapper.findAllRejectedStudentIds();
                List<AdjustmentRecord> existingInterview = adjustmentRecordMapper.selectList(
                        new LambdaQueryWrapper<AdjustmentRecord>()
                                .eq(AdjustmentRecord::getType, AdjustmentType.interview));
                Set<Long> interviewDone = existingInterview.stream()
                        .map(AdjustmentRecord::getStudentId).collect(Collectors.toSet());

                for (Long sid : allRejectedIds) {
                    if (interviewDone.contains(sid)) continue;
                    List<Intention> mine = intentionMapper.listByStudent(sid);
                    if (mine.isEmpty()) continue;
                    boolean acceptAny = mine.stream().anyMatch(i ->
                            (i.getAcceptInnerAdjust() != null && i.getAcceptInnerAdjust() == 1)
                            || (i.getAcceptGlobalAdjust() != null && i.getAcceptGlobalAdjust() == 1));
                    if (acceptAny) continue;

                    AdjustmentRecord rec = new AdjustmentRecord();
                    rec.setStudentId(sid);
                    rec.setRound(2);
                    rec.setType(AdjustmentType.interview);
                    rec.setFromRoomSnapshot("");
                    rec.setToRoomSnapshot("");
                    rec.setToTeacherSnapshot("");
                    rec.setResultSnapshot("待约谈，由学院统一安排");
                    rec.setOperatorRole("admin");
                    rec.setOperatorId(loginUser != null ? loginUser.getUserId() : null);
                    rec.setCreateTime(nt);
                    adjustmentRecordMapper.insert(rec);
                    affected++;
                }
            }

            case inner -> {
                List<Long> allRejectedIds = intentionMapper.findAllRejectedStudentIds();
                List<AdjustmentRecord> existingInner = adjustmentRecordMapper.selectList(
                        new LambdaQueryWrapper<AdjustmentRecord>()
                                .eq(AdjustmentRecord::getType, AdjustmentType.inner));
                Set<Long> innerDone = existingInner.stream()
                        .map(AdjustmentRecord::getStudentId).collect(Collectors.toSet());

                List<Student> allStudents = studentMapper.selectList(null);
                Map<Long, Student> studentMap = allStudents.stream()
                        .collect(Collectors.toMap(Student::getId, s -> s));

                for (Long sid : allRejectedIds) {
                    if (innerDone.contains(sid)) continue;
                    List<Intention> mine = intentionMapper.listByStudent(sid);
                    boolean acceptInner = mine.stream().anyMatch(i ->
                            i.getAcceptInnerAdjust() != null && i.getAcceptInnerAdjust() == 1);
                    if (!acceptInner) continue;

                    Student s = studentMap.get(sid);
                    if (s == null) continue;

                    // originalRoomIds：原填报的 roomIds 集合（按 priority 升序）
                    List<Long> originalRoomIds = mine.stream()
                            .map(Intention::getRoomId).filter(Objects::nonNull)
                            .distinct().collect(Collectors.toList());

                    Long foundRoomId = null;
                    Teacher foundTeacher = null;
                    for (Long rid : originalRoomIds) {
                        TrainingRoom curRoom = trainingRoomMapper.selectById(rid);
                        if (curRoom == null) continue;
                        if (trainingRoomMapper.countOccupied(rid) < curRoom.getQuota()) {
                            // 找这个 room 下可用的导师（没有 studentId=sid 的意向）
                            List<Teacher> roomTeachers = teacherMapper.selectList(
                                    new LambdaQueryWrapper<Teacher>().eq(Teacher::getRoomId, rid));
                            for (Teacher t : roomTeachers) {
                                boolean alreadyChosen = mine.stream()
                                        .anyMatch(i -> t.getId().equals(i.getTeacherId()));
                                if (!alreadyChosen) {
                                    foundRoomId = rid;
                                    foundTeacher = t;
                                    break;
                                }
                            }
                            if (foundTeacher != null) break;
                        }
                    }

                    if (foundRoomId == null || foundTeacher == null) {
                        Map<String, Object> sk = new LinkedHashMap<>();
                        sk.put("studentId", sid);
                        sk.put("name", s.getName());
                        sk.put("reason", "对应实训室无空闲导师");
                        skipped.add(sk);
                        continue;
                    }

                    TrainingRoom room = trainingRoomMapper.selectById(foundRoomId);

                    // 新建意向 round=2, priority=0
                    Intention ni = new Intention();
                    ni.setStudentId(sid);
                    ni.setTeacherId(foundTeacher.getId());
                    ni.setRoomId(foundRoomId);
                    ni.setTeacherNameSnapshot(foundTeacher.getName());
                    ni.setTeacherNoSnapshot(foundTeacher.getTeacherNo());
                    ni.setRoomNameSnapshot(room != null ? room.getName() : null);
                    ni.setPriority(0);
                    ni.setIntro(mine.stream().map(Intention::getIntro).filter(Objects::nonNull)
                            .findFirst().orElse(""));
                    ni.setAcceptInnerAdjust(1);
                    ni.setAcceptGlobalAdjust(mine.stream().anyMatch(i ->
                            i.getAcceptGlobalAdjust() != null && i.getAcceptGlobalAdjust() == 1) ? 1 : 0);
                    ni.setRound(2);
                    ni.setStatus(IntentionStatus.拟录取);
                    ni.setSubmitTime(nt);
                    ni.setWaitlistRank(null);
                    ni.setUpdateTime(nt);
                    intentionMapper.insert(ni);

                    boolean auto = autoConfirmIfSingle(sid);

                    AdjustmentRecord rec = new AdjustmentRecord();
                    rec.setStudentId(sid);
                    rec.setRound(2);
                    rec.setType(AdjustmentType.inner);
                    rec.setFromRoomSnapshot(String.join(", ", mine.stream()
                            .map(Intention::getRoomNameSnapshot).filter(Objects::nonNull).toList()));
                    rec.setToRoomSnapshot(room != null ? room.getName() : null);
                    rec.setToTeacherSnapshot(foundTeacher.getName());
                    rec.setResultSnapshot(auto ? "组内调剂成功并自动确认" : "组内调剂拟录取，待学生确认");
                    rec.setOperatorRole("admin");
                    rec.setOperatorId(loginUser != null ? loginUser.getUserId() : null);
                    rec.setCreateTime(nt);
                    adjustmentRecordMapper.insert(rec);
                    affected++;
                }
            }

            case global -> {
                List<Long> allRejectedIds = intentionMapper.findAllRejectedStudentIds();
                List<AdjustmentRecord> existingGlobal = adjustmentRecordMapper.selectList(
                        new LambdaQueryWrapper<AdjustmentRecord>()
                                .eq(AdjustmentRecord::getType, AdjustmentType.global));
                Set<Long> globalDone = existingGlobal.stream()
                        .map(AdjustmentRecord::getStudentId).collect(Collectors.toSet());

                List<Student> allStudents = studentMapper.selectList(null);
                Map<Long, Student> studentMap = allStudents.stream()
                        .collect(Collectors.toMap(Student::getId, s -> s));

                List<Teacher> allTeachers = teacherMapper.selectList(null);
                List<TrainingRoom> allRooms = trainingRoomMapper.selectList(null);

                for (Long sid : allRejectedIds) {
                    if (globalDone.contains(sid)) continue;
                    List<Intention> mine = intentionMapper.listByStudent(sid);
                    boolean acceptGlobal = mine.stream().anyMatch(i ->
                            i.getAcceptGlobalAdjust() != null && i.getAcceptGlobalAdjust() == 1);
                    if (!acceptGlobal) continue;

                    Student s = studentMap.get(sid);
                    if (s == null) continue;

                    // originalRoomIds：跳过
                    Set<Long> originalRoomIds = mine.stream()
                            .map(Intention::getRoomId).filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    Long foundRoomId = null;
                    Teacher foundTeacher = null;
                    for (TrainingRoom room : allRooms) {
                        if (originalRoomIds.contains(room.getId())) continue;
                        if (trainingRoomMapper.countOccupied(room.getId()) >= room.getQuota()) continue;
                        // 在这个 room 里找一个导师，student 没有选过
                        List<Teacher> roomTeachers = allTeachers.stream()
                                .filter(t -> room.getId().equals(t.getRoomId())).toList();
                        for (Teacher t : roomTeachers) {
                            boolean alreadyChosen = mine.stream()
                                    .anyMatch(i -> t.getId().equals(i.getTeacherId()));
                            if (!alreadyChosen) {
                                foundRoomId = room.getId();
                                foundTeacher = t;
                                break;
                            }
                        }
                        if (foundTeacher != null) break;
                    }

                    if (foundRoomId == null || foundTeacher == null) {
                        Map<String, Object> sk = new LinkedHashMap<>();
                        sk.put("studentId", sid);
                        sk.put("name", s.getName());
                        sk.put("reason", "全院无空闲调剂名额");
                        skipped.add(sk);
                        continue;
                    }

                    TrainingRoom room = trainingRoomMapper.selectById(foundRoomId);

                    Intention ni = new Intention();
                    ni.setStudentId(sid);
                    ni.setTeacherId(foundTeacher.getId());
                    ni.setRoomId(foundRoomId);
                    ni.setTeacherNameSnapshot(foundTeacher.getName());
                    ni.setTeacherNoSnapshot(foundTeacher.getTeacherNo());
                    ni.setRoomNameSnapshot(room != null ? room.getName() : null);
                    ni.setPriority(0);
                    ni.setIntro(mine.stream().map(Intention::getIntro).filter(Objects::nonNull)
                            .findFirst().orElse(""));
                    ni.setAcceptInnerAdjust(mine.stream().anyMatch(i ->
                            i.getAcceptInnerAdjust() != null && i.getAcceptInnerAdjust() == 1) ? 1 : 0);
                    ni.setAcceptGlobalAdjust(1);
                    ni.setRound(2);
                    ni.setStatus(IntentionStatus.拟录取);
                    ni.setSubmitTime(nt);
                    ni.setWaitlistRank(null);
                    ni.setUpdateTime(nt);
                    intentionMapper.insert(ni);

                    boolean auto = autoConfirmIfSingle(sid);

                    AdjustmentRecord rec = new AdjustmentRecord();
                    rec.setStudentId(sid);
                    rec.setRound(2);
                    rec.setType(AdjustmentType.global);
                    rec.setFromRoomSnapshot(String.join(", ", mine.stream()
                            .map(Intention::getRoomNameSnapshot).filter(Objects::nonNull).toList()));
                    rec.setToRoomSnapshot(room != null ? room.getName() : null);
                    rec.setToTeacherSnapshot(foundTeacher.getName());
                    rec.setResultSnapshot(auto ? "跨室调剂成功并自动确认" : "跨室调剂拟录取，待学生确认");
                    rec.setOperatorRole("admin");
                    rec.setOperatorId(loginUser != null ? loginUser.getUserId() : null);
                    rec.setCreateTime(nt);
                    adjustmentRecordMapper.insert(rec);
                    affected++;
                }
            }
        }

        writeLog(
                loginUser != null ? loginUser.getRole() : "admin",
                loginUser != null ? loginUser.getUserId() : null,
                "admin",
                "执行调剂",
                "type=" + type + ", affected=" + affected + ", skipped=" + skipped.size()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("affected", affected);
        if (!skipped.isEmpty()) {
            result.put("skipped", skipped);
        }
        return result;
    }

    // ================================================================
    // 12. publicityOverview
    // ================================================================

    public Map<String, Object> publicityOverview() {
        List<Intention> confirmedIntentions = intentionMapper.selectList(
                new LambdaQueryWrapper<Intention>()
                        .eq(Intention::getStatus, IntentionStatus.已确认));
        List<Student> allStudents = studentMapper.selectList(null);
        Map<Long, Student> studentMap = allStudents.stream()
                .collect(Collectors.toMap(Student::getId, s -> s));

        List<Map<String, Object>> eligible = confirmedIntentions.stream().map(i -> {
            Student s = studentMap.get(i.getStudentId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", i.getStudentId());
            m.put("intentionId", i.getId());
            m.put("studentNo", s != null ? s.getStudentNo() : null);
            m.put("name", s != null ? s.getName() : null);
            m.put("major", s != null ? s.getMajor() : null);
            m.put("grade", s != null ? s.getGrade() : null);
            m.put("roomName", i.getRoomNameSnapshot());
            m.put("teacherName", i.getTeacherNameSnapshot());
            m.put("status", i.getStatus() != null ? i.getStatus().name() : null);
            return m;
        }).collect(Collectors.toList());

        // records
        List<PublicityResult> results = publicityResultMapper.selectList(null);
        results.sort((a, b) -> {
            if (a.getStartTime() == null && b.getStartTime() == null) return 0;
            if (a.getStartTime() == null) return 1;
            if (b.getStartTime() == null) return -1;
            return b.getStartTime().compareTo(a.getStartTime());
        });
        List<Map<String, Object>> records = results.stream().map(r -> {
            Student s = studentMap.get(r.getStudentId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("studentId", r.getStudentId());
            m.put("intentionId", r.getIntentionId());
            m.put("studentNo", s != null ? s.getStudentNo() : null);
            m.put("studentName", s != null ? s.getName() : null);
            m.put("roomSnapshot", r.getRoomSnapshot());
            m.put("teacherSnapshot", r.getTeacherSnapshot());
            m.put("startTime", toEpochMilli(r.getStartTime()));
            m.put("endTime", toEpochMilli(r.getEndTime()));
            m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> window = windowOf("publicity");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eligible", eligible);
        result.put("records", records);
        result.put("window", window);
        return result;
    }

    // ================================================================
    // 13. publishPublicity
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishPublicity() {
        List<Intention> confirmedIntentions = intentionMapper.selectList(
                new LambdaQueryWrapper<Intention>()
                        .eq(Intention::getStatus, IntentionStatus.已确认));

        // 跳过已有 PUBLISHING 状态的
        List<PublicityResult> existing = publicityResultMapper.selectList(
                new LambdaQueryWrapper<PublicityResult>()
                        .eq(PublicityResult::getStatus, PublicityStatus.PUBLISHING));
        Set<Long> excludedStudentIds = existing.stream()
                .map(PublicityResult::getStudentId).collect(Collectors.toSet());

        LocalDateTime start = now();
        LocalDateTime end = start.plusDays(1);

        int created = 0;
        for (Intention i : confirmedIntentions) {
            if (excludedStudentIds.contains(i.getStudentId())) continue;
            PublicityResult pr = new PublicityResult();
            pr.setStudentId(i.getStudentId());
            pr.setIntentionId(i.getId());
            pr.setRoomSnapshot(i.getRoomNameSnapshot());
            pr.setTeacherSnapshot(i.getTeacherNameSnapshot());
            pr.setStartTime(start);
            pr.setEndTime(end);
            pr.setStatus(PublicityStatus.PUBLISHING);
            publicityResultMapper.insert(pr);
            created++;
        }

        LoginUser loginUser = TokenInterceptor.AuthContext.getOrNull();
        writeLog(
                loginUser != null ? loginUser.getRole() : "admin",
                loginUser != null ? loginUser.getUserId() : null,
                "admin",
                "发布公示",
                "created=" + created
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        return result;
    }

    // ================================================================
    // 14. finalizePublicity
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> finalizePublicity(boolean force) {
        LocalDateTime nt = now();
        List<PublicityResult> publishing = publicityResultMapper.selectList(
                new LambdaQueryWrapper<PublicityResult>()
                        .eq(PublicityResult::getStatus, PublicityStatus.PUBLISHING));

        List<PublicityResult> toFinalize;
        if (force) {
            toFinalize = publishing;
        } else {
            toFinalize = publishing.stream()
                    .filter(r -> r.getEndTime() != null && !nt.isBefore(r.getEndTime()))
                    .collect(Collectors.toList());
        }

        if (toFinalize.isEmpty()) {
            throw new BizException(422, "没有可结束的公示");
        }

        int enrolled = 0;
        for (PublicityResult r : toFinalize) {
            r.setStatus(PublicityStatus.FINISHED);
            publicityResultMapper.updateById(r);

            Intention it = r.getIntentionId() != null ? intentionMapper.selectById(r.getIntentionId())
                    : null;
            if (it != null && it.getStatus() == IntentionStatus.已确认) {
                it.setStatus(IntentionStatus.已录取);
                it.setUpdateTime(nt);
                intentionMapper.updateById(it);
                enrolled++;
            }
        }

        LoginUser loginUser = TokenInterceptor.AuthContext.getOrNull();
        writeLog(
                loginUser != null ? loginUser.getRole() : "admin",
                loginUser != null ? loginUser.getUserId() : null,
                "admin",
                "结束公示",
                "finalized=" + toFinalize.size() + ", enrolled=" + enrolled
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enrolled", enrolled);
        result.put("finalized", toFinalize.size());
        return result;
    }

    // ================================================================
    // 15. listLogs
    // ================================================================

    public List<Map<String, Object>> listLogs() {
        List<OperationLog> logs = operationLogMapper.selectList(null);
        logs.sort((a, b) -> {
            if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });
        return logs.stream().limit(40).map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("operatorRole", l.getOperatorRole());
            m.put("operatorId", l.getOperatorId());
            m.put("operatorName", l.getOperatorName());
            m.put("action", l.getAction());
            m.put("detail", l.getDetail());
            m.put("createTime", toEpochMilli(l.getCreateTime()));
            return m;
        }).collect(Collectors.toList());
    }
}
