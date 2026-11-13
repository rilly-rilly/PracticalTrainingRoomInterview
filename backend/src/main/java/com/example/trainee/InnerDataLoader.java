package com.example.trainee;

import com.example.trainee.entity.*;
import com.example.trainee.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

/**
 * H2 开发环境启动时的种子数据初始化，严格对齐 frontend/src/mock/seed.js 的初始状态。
 * 生产 MySQL 部署时请用 sql/data-mysql.sql，不要依赖此加载器。
 *
 * 密码由 BCryptPasswordEncoder 在运行时生成哈希存入数据库，**不会**出现明文密码。
 */
@Slf4j
@Component
@Order(1)
@Transactional
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!mysql")  // 只在 H2 默认 profile 下执行，MySQL 用 sql/data-mysql.sql 初始化
public class InnerDataLoader implements CommandLineRunner {

    private final TrainingRoomMapper roomMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final AdminUserMapper adminMapper;
    private final ScheduleItemMapper scheduleMapper;
    private final NoticeMapper noticeMapper;
    private final InterviewMapper interviewMapper;
    private final AdmissionConfirmationMapper confirmMapper;
    private final IntentionMapper intentionMapper;
    private final OperationLogMapper logMapper;
    private final PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        // 清空（schema-h2.sql 每次启动重建，这里保险起见）
        intentionMapper.delete(null);
        interviewMapper.delete(null);
        confirmMapper.delete(null);
        roomMapper.delete(null);
        teacherMapper.delete(null);
        studentMapper.delete(null);
        adminMapper.delete(null);
        scheduleMapper.delete(null);
        noticeMapper.delete(null);
        logMapper.delete(null);

        // --- 4 个实训室 ---
        LocalDateTime s1 = now.minusDays(7), e1 = now.minusDays(1);
        LocalDateTime s2 = now.minusDays(1), e2 = now.plusDays(2);
        LocalDateTime s3 = now.minusDays(2), e3 = now.plusDays(1);
        LocalDateTime s4 = now.minusDays(1), e4 = now.plusDays(3);
        LocalDateTime s5 = now.plusDays(2), e5 = now.plusDays(4);
        LocalDateTime s6 = now.plusDays(4), e6 = now.plusDays(6);
        LocalDateTime s7 = now.plusDays(6), e7 = now.plusDays(7);

        TrainingRoom[] rooms = {
                room("RV-01", "智能视觉实训室", "计算机视觉 / 多模态感知", 4,
                        "入组后前 6 周完成 Python、OpenCV 与 PyTorch 基础训练，随后进入课题组参与工业缺陷检测项目。",
                        "考核出勤率、每周任务完成度、组会汇报质量与期末项目答辩。",
                        "[\"机器视觉\",\"深度学习\",\"模型部署\"]", "面向工业检测与智能感知场景，配备 GPU 服务器与工业相机。"),
                room("RV-02", "嵌入式与物联网实训室", "嵌入式系统 / 传感器网络", 3,
                        "从裸机开发到 RTOS 迁移，完成一套环境监测节点与网关的联调。",
                        "考核硬件调试记录、代码规范、节点稳定性测试与最终联调演示。",
                        "[\"STM32\",\"RTOS\",\"物联网协议\"]", "拥有示波器、逻辑分析仪与焊接工位。"),
                room("RV-03", "Web 应用开发实训室", "全栈开发 / 工程化实践", 3,
                        "以真实业务需求为驱动，完成前后端分离项目从需求评审到上线的完整流程。",
                        "考核代码质量、接口设计、联调效率与项目上线后的运维记录。",
                        "[\"Vue3\",\"Spring Boot\",\"MySQL\"]", "承接学院内部信息化需求，强调工程规范。"),
                room("RV-04", "数据工程与智能分析实训室", "数据管道 / 可视化分析", 2,
                        "参与校园数据治理项目，完成数据采集、清洗、指标建模与看板搭建。",
                        "考核数据质量、分析结论的可解释性与看板交付完整度。",
                        "[\"数据清洗\",\"指标建模\",\"可视化\"]", "面向真实校园数据，强调数据口径与业务价值。")
        };
        Arrays.stream(rooms).forEach(roomMapper::insert);
        long r1 = rooms[0].getId(), r2 = rooms[1].getId(), r3 = rooms[2].getId(), r4 = rooms[3].getId();

        // --- 5 位导师 ---
        Teacher t1 = teacher("T1001", "李伯谦", "副教授", r1, "Teach@2026");
        Teacher t2 = teacher("T1002", "王书宁", "讲师", r1, "Teach@2026");
        Teacher t3 = teacher("T1003", "陈嘉禾", "副教授", r2, "Teach@2026");
        Teacher t4 = teacher("T1004", "赵明远", "讲师", r3, "Teach@2026");
        Teacher t5 = teacher("T1005", "孙倩", "高级工程师", r4, "Teach@2026");
        teacherMapper.insert(t1); teacherMapper.insert(t2); teacherMapper.insert(t3);
        teacherMapper.insert(t4); teacherMapper.insert(t5);

        // --- 9 位学生 ---
        Student[] students = {
                stu("S2026001", "张一鸣", "计算机科学与技术", "2024 级"),
                stu("S2026002", "李思远", "软件工程", "2024 级"),
                stu("S2026003", "王雨桐", "人工智能", "2023 级"),
                stu("S2026004", "陈子豪", "物联网工程", "2024 级"),
                stu("S2026005", "刘嘉琪", "数据科学与大数据技术", "2023 级"),
                stu("S2026006", "周文轩", "计算机科学与技术", "2024 级"),
                stu("S2026007", "吴涵", "电子信息工程", "2024 级"),
                stu("S2026008", "郑欣然", "软件工程", "2024 级"),
                stu("S2026009", "韩雨泽", "网络工程", "2023 级")
        };
        Arrays.stream(students).forEach(studentMapper::insert);
        long stu1 = students[0].getId(), stu2 = students[1].getId(), stu3 = students[2].getId(),
                stu4 = students[3].getId(), stu5 = students[4].getId(), stu6 = students[5].getId(),
                stu7 = students[6].getId(), stu9 = students[8].getId();

        // --- 管理员 ---
        AdminUser admin = new AdminUser();
        admin.setUsername("admin");
        admin.setName("学院管理员");
        admin.setPasswordHash(encoder.encode("admin123"));
        adminMapper.insert(admin);

        // --- 7 步日程 ---
        schedule(1, "open", "实训室开放日与介绍", s1, e1, "各实训室开放参观，可在线查看项目方向、名额与培养安排。");
        schedule(2, "intent", "学生意向填报", s2, e2, "最多填报 3 个平行意向，必须提交个人简介并选择调剂意愿。");
        schedule(3, "interview", "导师面试考核", s3, e3, "导师查看报考学生简介，录入面试评分并决定拟录取或递补。");
        schedule(4, "confirm", "拟录取确认", s4, e4, "收到 1 个拟录取自动确认；2 个及以上须自主选择唯一去向，逾期视为放弃。");
        schedule(5, "supplement", "分轮补录", s5, e5, "按递补顺位补充空出的名额，逐轮公布补录结果。");
        schedule(6, "adjust", "统筹调剂", s6, e6, "先后执行实训室内部调剂、全院跨实训室调剂，最后约谈未服从调剂学生。");
        schedule(7, "publicity", "结果公示与入组", s7, e7, "汇总最终录取名单公示 1 天，无异议后正式入组。");

        // --- 4 条公告 ---
        notice("2026 年秋季实训室进组选拔正式启动",
                "本次共有 4 个实训室开放选拔，请同学们在意向填报窗口内完成填报，逾期不再受理。", "重要");
        notice("意向填报截止时间提醒",
                "意向填报窗口将于窗口结束后关闭，最多填报 3 个平行意向，务必填写真实个人简介。", "提醒");
        notice("面试安排说明",
                "导师将通过站内列表查看你的简介并组织面试，请保持通讯畅通，按导师通知的时间参加。", "普通");
        notice("关于调剂意愿的说明",
                "勾选服从调剂可以增加录取机会：先进行实训室内部调剂，再进行全院跨实训室调剂，均不接受时将进入约谈环节。", "普通");

        // --- 初始意向 ---
        String intro = "我是计算机科学与技术专业 2024 级学生，熟悉 Python 与 C++，做过图像分类课程项目，希望在实训室系统学习视觉算法与工程落地。";
        String intro2 = "软件工程专业，前后端都写过，独立完成过校园二手交易平台，希望加入实训室参与真实项目。";
        String intro3 = "人工智能专业，数学基础较好，跟进过目标检测开源项目。";
        String intro4 = "物联网工程专业，做过 STM32 小车与温湿度采集，希望深入嵌入式系统与传感器网络。";
        String intro5 = "数据科学与大数据技术专业，熟悉 SQL 与 Pandas，做过电商用户行为分析。";
        String intro6 = "计算机专业，喜欢后端开发，了解 Spring Boot 与 MySQL。";
        String intro7 = "电子信息工程专业，做过 FPGA 与信号采集，希望跨方向学习。";
        String intro9 = "网络工程专业，熟悉 Linux 与网络协议，希望参与实训室的系统运维。";

        // int-1: stu1 -> t1 拟录取
        intention(stu1, t1.getId(), r1, t1.getName(), t1.getTeacherNo(), "智能视觉实训室",
                1, intro, 1, 1, 1, IntentionStatus.拟录取, now.minusHours(24));
        // int-2: stu1 -> t4 拟录取
        intention(stu1, t4.getId(), r3, t4.getName(), t4.getTeacherNo(), "Web 应用开发实训室",
                2, intro, 1, 1, 1, IntentionStatus.拟录取, now.minusHours(22));
        // int-3: stu1 -> t3 未录取
        intention(stu1, t3.getId(), r2, t3.getName(), t3.getTeacherNo(), "嵌入式与物联网实训室",
                3, intro, 1, 1, 1, IntentionStatus.未录取, now.minusHours(20));

        // int-4: stu2 -> t5 拟录取（随后自动确认）
        Intention int4 = intention(stu2, t5.getId(), r4, t5.getName(), t5.getTeacherNo(), "数据工程与智能分析实训室",
                1, intro2, 1, 1, 1, IntentionStatus.已确认, now.minusHours(26));

        // int-5: stu3 -> t1 递补 rank=1
        intention(stu3, t1.getId(), r1, t1.getName(), t1.getTeacherNo(), "智能视觉实训室",
                1, intro3, 1, 0, 1, IntentionStatus.递补, now.minusHours(18), 1);
        intention(stu3, t2.getId(), r1, t2.getName(), t2.getTeacherNo(), "智能视觉实训室",
                2, intro3, 1, 0, 1, IntentionStatus.未录取, now.minusHours(16));
        intention(stu3, t5.getId(), r4, t5.getName(), t5.getTeacherNo(), "数据工程与智能分析实训室",
                3, intro3, 1, 0, 1, IntentionStatus.未录取, now.minusHours(14));

        // stu4 -> t3 待面试
        intention(stu4, t3.getId(), r2, t3.getName(), t3.getTeacherNo(), "嵌入式与物联网实训室",
                1, intro4, 1, 1, 1, IntentionStatus.待面试, now.minusHours(10));
        intention(stu4, t2.getId(), r1, t2.getName(), t2.getTeacherNo(), "智能视觉实训室",
                2, intro4, 1, 1, 1, IntentionStatus.待面试, now.minusHours(10));

        // stu5: 3 条面试完成
        intention(stu5, t4.getId(), r3, t4.getName(), t4.getTeacherNo(), "Web 应用开发实训室",
                1, intro5, 1, 1, 1, IntentionStatus.面试完成, now.minusHours(16));
        intention(stu5, t5.getId(), r4, t5.getName(), t5.getTeacherNo(), "数据工程与智能分析实训室",
                2, intro5, 1, 1, 1, IntentionStatus.面试完成, now.minusHours(15));
        intention(stu5, t2.getId(), r1, t2.getName(), t2.getTeacherNo(), "智能视觉实训室",
                3, intro5, 1, 1, 1, IntentionStatus.面试完成, now.minusHours(14));

        // stu6: 未录取，服从内部+全院
        intention(stu6, t2.getId(), r1, t2.getName(), t2.getTeacherNo(), "智能视觉实训室",
                1, intro6, 1, 1, 1, IntentionStatus.未录取, now.minusHours(20));
        // stu7: 未录取，仅服从全院
        intention(stu7, t3.getId(), r2, t3.getName(), t3.getTeacherNo(), "嵌入式与物联网实训室",
                1, intro7, 0, 1, 1, IntentionStatus.未录取, now.minusHours(19));
        // stu9: 未录取，不服从
        intention(stu9, t4.getId(), r3, t4.getName(), t4.getTeacherNo(), "Web 应用开发实训室",
                1, intro9, 0, 0, 1, IntentionStatus.未录取, now.minusHours(18));

        // --- 面试记录 ---
        interview(findIntention(stu1, t1.getId()), 9, 8, 9, 8, "基础扎实，项目描述清楚。", now.minusHours(6));
        interview(findIntention(stu2, t5.getId()), 9, 9, 8, 9, "数据基础与业务理解都好，直接进组。", now.minusHours(10));
        interview(findIntention(stu3, t1.getId()), 8, 8, 8, 8, "综合稳定，暂列递补第一顺位。", now.minusHours(8));

        // --- 确认记录 ---
        AdmissionConfirmation cf2 = new AdmissionConfirmation();
        cf2.setStudentId(stu2); cf2.setIntentionId(int4.getId());
        cf2.setMode(ConfirmMode.auto); cf2.setConfirmTime(now.minusHours(9));
        confirmMapper.insert(cf2);

        // --- 操作日志 ---
        opLog("system", null, "系统", "初始化演示数据", "生成 4 个实训室、5 名导师、9 名学生及演示意向", now.minusDays(2));
        opLog("teacher", t1.getId(), t1.getName(), "录入面试评分", "张一鸣 / 智能视觉实训室 / 总分 34", now.minusHours(6));
        opLog("teacher", t5.getId(), t5.getName(), "作出录取决定", "李思远 / 拟录取（自动确认）", now.minusHours(9));

        log.info("InnerDataLoader 初始化完成：{} 实训室, {} 导师, {} 学生, {} 意向",
                rooms.length, 5, students.length, 15);
    }

    /* -------- 私有工具方法 -------- */

    private TrainingRoom room(String code, String name, String dir, int quota, String plan, String req, String tags, String desc) {
        TrainingRoom r = new TrainingRoom();
        r.setRoomCode(code); r.setName(name); r.setDirection(dir); r.setQuota(quota);
        r.setTrainingPlan(plan); r.setAssessmentRequirement(req); r.setTags(tags); r.setDescription(desc);
        LocalDateTime now = LocalDateTime.now();
        r.setCreateTime(now); r.setUpdateTime(now);
        return r;
    }

    private Teacher teacher(String no, String name, String title, Long roomId, String rawPwd) {
        Teacher t = new Teacher();
        t.setTeacherNo(no); t.setName(name); t.setTitle(title);
        t.setRoomId(roomId); t.setPasswordHash(encoder.encode(rawPwd));
        LocalDateTime now = LocalDateTime.now();
        t.setCreateTime(now); t.setUpdateTime(now);
        return t;
    }

    private Student stu(String no, String name, String major, String grade) {
        Student s = new Student();
        s.setStudentNo(no); s.setName(name); s.setMajor(major);
        s.setGrade(grade); s.setPasswordHash(encoder.encode("Stu@2026"));
        s.setCreateTime(LocalDateTime.now());
        return s;
    }

    private void schedule(int step, String key, String title, LocalDateTime start, LocalDateTime end, String desc) {
        ScheduleItem si = new ScheduleItem();
        si.setStepNo(step); si.setKeyValue(key); si.setTitle(title);
        si.setStartTime(start); si.setEndTime(end); si.setDescription(desc);
        si.setUpdateTime(end);
        scheduleMapper.insert(si);
    }

    private void notice(String title, String content, String level) {
        Notice n = new Notice();
        n.setTitle(title); n.setContent(content); n.setLevel(level);
        n.setPublishTime(LocalDateTime.now());
        noticeMapper.insert(n);
    }

    private Intention intention(Long studentId, Long teacherId, Long roomId, String teacherNameSnap, String teacherNoSnap,
                                String roomNameSnap, int priority, String intro, int acceptInner, int acceptGlobal,
                                int round, IntentionStatus status, LocalDateTime submitTime) {
        return intention(studentId, teacherId, roomId, teacherNameSnap, teacherNoSnap, roomNameSnap, priority, intro,
                acceptInner, acceptGlobal, round, status, submitTime, 0);
    }

    private Intention intention(Long studentId, Long teacherId, Long roomId, String teacherNameSnap, String teacherNoSnap,
                                String roomNameSnap, int priority, String intro, int acceptInner, int acceptGlobal,
                                int round, IntentionStatus status, LocalDateTime submitTime, int waitlistRank) {
        Intention i = new Intention();
        i.setStudentId(studentId); i.setTeacherId(teacherId); i.setRoomId(roomId);
        i.setTeacherNameSnapshot(teacherNameSnap); i.setTeacherNoSnapshot(teacherNoSnap);
        i.setRoomNameSnapshot(roomNameSnap); i.setPriority(priority); i.setIntro(intro);
        i.setAcceptInnerAdjust(acceptInner); i.setAcceptGlobalAdjust(acceptGlobal);
        i.setRound(round); i.setStatus(status); i.setSubmitTime(submitTime);
        i.setWaitlistRank(waitlistRank); i.setUpdateTime(LocalDateTime.now());
        intentionMapper.insert(i);
        return i;
    }

    /** 按 studentId + teacherId 找到刚才插入的意向 */
    private Intention findIntention(Long studentId, Long teacherId) {
        return intentionMapper.listByStudent(studentId).stream()
                .filter(i -> i.getTeacherId().equals(teacherId))
                .findFirst().orElseThrow(() -> new IllegalStateException("种子数据定位失败：student=" + studentId + " teacher=" + teacherId));
    }

    private void interview(Intention it, int att, int log, int cod, int com, String comment, LocalDateTime t) {
        Interview iv = new Interview();
        iv.setIntentionId(it.getId());
        iv.setAttitudeScore(att); iv.setLogicScore(log); iv.setCodingScore(cod); iv.setCommitmentScore(com);
        iv.setTotal(att + log + cod + com); iv.setComment(comment); iv.setCreateTime(t);
        interviewMapper.insert(iv);
    }

    private void opLog(String role, Long userId, String userName, String action, String detail, LocalDateTime t) {
        OperationLog log = new OperationLog();
        log.setOperatorRole(role); log.setOperatorId(userId); log.setOperatorName(userName);
        log.setAction(action); log.setDetail(detail); log.setCreateTime(t);
        logMapper.insert(log);
    }
}
