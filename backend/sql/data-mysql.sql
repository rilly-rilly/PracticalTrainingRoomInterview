/*
 * MySQL 8 种子数据
 * 使用方式：USE trainee_selection; SOURCE sql/data-mysql.sql;
 * 注意：必须在 schema-mysql.sql 之后执行，且表已清空。
 * 密码哈希已由 BCryptPasswordEncoder 预生成：
 *   admin123    -> $2a$10$C6QX8iXns7ieeQI.I6gZieSg4ZmICRzGj3Z2d.nxl0afGZfhjpnVy
 *   Teach@2026  -> $2a$10$PP/ZwYYeB7IKSniZfY6wUer1.Dg0psc.tqBtD2UimxA0NZTmnnuTe
 *   Stu@2026    -> $2a$10$ICIe3NxaHLZfiDlPAojkqe8lJGhs.3TeZuq./t/IqwYx5wO1r/TBe
 */
USE `trainee_selection`;
SET NAMES utf8mb4;

SET @now     = NOW();
SET @minus7d = DATE_SUB(@now, INTERVAL 7 DAY);
SET @minus2d = DATE_SUB(@now, INTERVAL 2 DAY);
SET @minus1d = DATE_SUB(@now, INTERVAL 1 DAY);
SET @plus2d  = DATE_ADD(@now, INTERVAL 2 DAY);
SET @plus3d  = DATE_ADD(@now, INTERVAL 3 DAY);
SET @plus4d  = DATE_ADD(@now, INTERVAL 4 DAY);
SET @plus6d  = DATE_ADD(@now, INTERVAL 6 DAY);
SET @plus7d  = DATE_ADD(@now, INTERVAL 7 DAY);

/* ============================================
   4 个实训室
   ============================================ */
INSERT INTO `training_room`
    (`id`, `room_code`, `name`, `direction`, `quota`, `training_plan`, `assessment_requirement`, `tags`, `description`, `create_time`, `update_time`)
VALUES
(1, 'RV-01', '智能视觉实训室', '计算机视觉 / 多模态感知', 4,
 '入组后前 6 周完成 Python、OpenCV 与 PyTorch 基础训练，随后进入课题组参与工业缺陷检测项目。',
 '考核出勤率、每周任务完成度、组会汇报质量与期末项目答辩。',
 '["机器视觉","深度学习","模型部署"]',
 '面向工业检测与智能感知场景，配备 GPU 服务器与工业相机。',
 @now, @now),
(2, 'RV-02', '嵌入式与物联网实训室', '嵌入式系统 / 传感器网络', 3,
 '从裸机开发到 RTOS 迁移，完成一套环境监测节点与网关的联调。',
 '考核硬件调试记录、代码规范、节点稳定性测试与最终联调演示。',
 '["STM32","RTOS","物联网协议"]',
 '拥有示波器、逻辑分析仪与焊接工位。',
 @now, @now),
(3, 'RV-03', 'Web 应用开发实训室', '全栈开发 / 工程化实践', 3,
 '以真实业务需求为驱动，完成前后端分离项目从需求评审到上线的完整流程。',
 '考核代码质量、接口设计、联调效率与项目上线后的运维记录。',
 '["Vue3","Spring Boot","MySQL"]',
 '承接学院内部信息化需求，强调工程规范。',
 @now, @now),
(4, 'RV-04', '数据工程与智能分析实训室', '数据管道 / 可视化分析', 2,
 '参与校园数据治理项目，完成数据采集、清洗、指标建模与看板搭建。',
 '考核数据质量、分析结论的可解释性与看板交付完整度。',
 '["数据清洗","指标建模","可视化"]',
 '面向真实校园数据，强调数据口径与业务价值。',
 @now, @now);

/* ============================================
   5 位导师
   ============================================ */
INSERT INTO `teacher`
    (`id`, `teacher_no`, `name`, `title`, `room_id`, `password_hash`, `create_time`, `update_time`)
VALUES
(1, 'T1001', '李伯谦', '副教授', 1, '$2a$10$PP/ZwYYeB7IKSniZfY6wUer1.Dg0psc.tqBtD2UimxA0NZTmnnuTe', @now, @now),
(2, 'T1002', '王书宁', '讲师',   1, '$2a$10$PP/ZwYYeB7IKSniZfY6wUer1.Dg0psc.tqBtD2UimxA0NZTmnnuTe', @now, @now),
(3, 'T1003', '陈嘉禾', '副教授', 2, '$2a$10$PP/ZwYYeB7IKSniZfY6wUer1.Dg0psc.tqBtD2UimxA0NZTmnnuTe', @now, @now),
(4, 'T1004', '赵明远', '讲师',   3, '$2a$10$PP/ZwYYeB7IKSniZfY6wUer1.Dg0psc.tqBtD2UimxA0NZTmnnuTe', @now, @now),
(5, 'T1005', '孙倩',   '高级工程师', 4, '$2a$10$PP/ZwYYeB7IKSniZfY6wUer1.Dg0psc.tqBtD2UimxA0NZTmnnuTe', @now, @now);

/* ============================================
   9 位学生
   ============================================ */
INSERT INTO `student`
    (`id`, `student_no`, `name`, `major`, `grade`, `password_hash`, `create_time`)
VALUES
(1, 'S2026001', '张一鸣', '计算机科学与技术', '2024 级', '$2a$10$ICIe3NxaHLZfiDlPAojkqe8lJGhs.3TeZuq./t/IqwYx5wO1r/TBe', @now),
(2, 'S2026002', '李思远', '软件工程',       '2024 级', '$2a$10$ICIe3NxaHLZfiDlPAojkqe8lJGhs.3TeZuq./t/IqwYx5wO1r/TBe', @now),
(3, 'S2026003', '王雨桐', '人工智能',       '2023 级', '$2a$10$ICIe3NxaHLZfiDlPAojkqe8lJGhs.3TeZuq./t/IqwYx5wO1r/TBe', @now),
(4, 'S2026004', '陈子豪', '物联网工程',     '2024 级', '$2a$10$ICIe3NxaHLZfiDlPAojkqe8lJGhs.3TeZuq./t/IqwYx5wO1r/TBe', @now),
(5, 'S2026005', '刘嘉琪', '数据科学与大数据技术', '2023 级', '$2a$10$ICIe3NxaHLZfiDlPAojkqe8lJGhs.3TeZuq./t/IqwYx5wO1r/TBe', @now),
(6, 'S2026006', '周文轩', '计算机科学与技术', '2024 级', '$2a$10$ICIe3NxaHLZfiDlPAojkqe8lJGhs.3TeZuq./t/IqwYx5wO1r/TBe', @now),
(7, 'S2026007', '吴涵',   '电子信息工程',     '2024 级', '$2a$10$ICIe3NxaHLZfiDlPAojkqe8lJGhs.3TeZuq./t/IqwYx5wO1r/TBe', @now),
(8, 'S2026008', '郑欣然', '软件工程',         '2024 级', '$2a$10$ICIe3NxaHLZfiDlPAojkqe8lJGhs.3TeZuq./t/IqwYx5wO1r/TBe', @now),
(9, 'S2026009', '韩雨泽', '网络工程',         '2023 级', '$2a$10$ICIe3NxaHLZfiDlPAojkqe8lJGhs.3TeZuq./t/IqwYx5wO1r/TBe', @now);

/* ============================================
   管理员
   ============================================ */
INSERT INTO `admin_user` (`id`, `username`, `name`, `password_hash`, `create_time`)
VALUES (1, 'admin', '学院管理员', '$2a$10$C6QX8iXns7ieeQI.I6gZieSg4ZmICRzGj3Z2d.nxl0afGZfhjpnVy', @now);

/* ============================================
   7 步日程
   ============================================ */
INSERT INTO `schedule_item`
    (`id`, `step_no`, `key_value`, `title`, `start_time`, `end_time`, `description`, `update_time`)
VALUES
(1, 1, 'open',       '实训室开放日与介绍', @minus7d, @minus1d, '各实训室开放参观，可在线查看项目方向、名额与培养安排。', @minus1d),
(2, 2, 'intent',     '学生意向填报',       @minus1d, @plus2d,  '最多填报 3 个平行意向，必须提交个人简介并选择调剂意愿。', @plus2d),
(3, 3, 'interview',  '导师面试考核',       @minus2d, @plus1d,  '导师查看报考学生简介，录入面试评分并决定拟录取或递补。', @plus1d),
(4, 4, 'confirm',    '拟录取确认',         @minus1d, @plus3d,  '收到 1 个拟录取自动确认；2 个及以上须自主选择唯一去向，逾期视为放弃。', @plus3d),
(5, 5, 'supplement', '分轮补录',           @plus2d,  @plus4d,  '按递补顺位补充空出的名额，逐轮公布补录结果。', @plus4d),
(6, 6, 'adjust',     '统筹调剂',           @plus4d,  @plus6d,  '先后执行实训室内部调剂、全院跨实训室调剂，最后约谈未服从调剂学生。', @plus6d),
(7, 7, 'publicity',  '结果公示与入组',     @plus6d,  @plus7d,  '汇总最终录取名单公示 1 天，无异议后正式入组。', @plus7d);

/* ============================================
   4 条公告
   ============================================ */
INSERT INTO `notice` (`id`, `title`, `content`, `level`, `publish_time`)
VALUES
(1, '2026 年秋季实训室进组选拔正式启动', '本次共有 4 个实训室开放选拔，请同学们在意向填报窗口内完成填报，逾期不再受理。', '重要', @now),
(2, '意向填报截止时间提醒', '意向填报窗口将于窗口结束后关闭，最多填报 3 个平行意向，务必填写真实个人简介。', '提醒', @now),
(3, '面试安排说明', '导师将通过站内列表查看你的简介并组织面试，请保持通讯畅通，按导师通知的时间参加。', '普通', @now),
(4, '关于调剂意愿的说明', '勾选服从调剂可以增加录取机会：先进行实训室内部调剂，再进行全院跨实训室调剂，均不接受时将进入约谈环节。', '普通', @now);

/* ============================================
   初始意向（15 条）
   ============================================ */
INSERT INTO `intention`
    (`id`, `student_id`, `teacher_id`, `room_id`, `teacher_name_snapshot`, `teacher_no_snapshot`, `room_name_snapshot`,
     `priority`, `intro`, `accept_inner_adjust`, `accept_global_adjust`, `round`, `status`,
     `submit_time`, `waitlist_rank`, `update_time`)
VALUES
/* stu1: 2 拟录取 + 1 未录取 */
(1,  1, 1, 1, '李伯谦', 'T1001', '智能视觉实训室',
  1, '我是计算机科学与技术专业 2024 级学生，熟悉 Python 与 C++，做过图像分类课程项目，希望在实训室系统学习视觉算法与工程落地。',
  1, 1, 1, '拟录取', DATE_SUB(@now, INTERVAL 24 HOUR), 0, @now),
(2,  1, 4, 3, '赵明远', 'T1004', 'Web 应用开发实训室',
  2, '我是计算机科学与技术专业 2024 级学生，熟悉 Python 与 C++，做过图像分类课程项目，希望在实训室系统学习视觉算法与工程落地。',
  1, 1, 1, '拟录取', DATE_SUB(@now, INTERVAL 22 HOUR), 0, @now),
(3,  1, 3, 2, '陈嘉禾', 'T1003', '嵌入式与物联网实训室',
  3, '我是计算机科学与技术专业 2024 级学生，熟悉 Python 与 C++，做过图像分类课程项目，希望在实训室系统学习视觉算法与工程落地。',
  1, 1, 1, '未录取', DATE_SUB(@now, INTERVAL 20 HOUR), 0, @now),

/* stu2: 已自动确认 */
(4,  2, 5, 4, '孙倩',   'T1005', '数据工程与智能分析实训室',
  1, '软件工程专业，前后端都写过，独立完成过校园二手交易平台，希望加入实训室参与真实项目。',
  1, 1, 1, '已确认', DATE_SUB(@now, INTERVAL 26 HOUR), 0, @now),

/* stu3: 递补 rank=1 + 2 未录取 */
(5,  3, 1, 1, '李伯谦', 'T1001', '智能视觉实训室',
  1, '人工智能专业，数学基础较好，跟进过目标检测开源项目。',
  1, 0, 1, '递补', DATE_SUB(@now, INTERVAL 18 HOUR), 1, @now),
(6,  3, 2, 1, '王书宁', 'T1002', '智能视觉实训室',
  2, '人工智能专业，数学基础较好，跟进过目标检测开源项目。',
  1, 0, 1, '未录取', DATE_SUB(@now, INTERVAL 16 HOUR), 0, @now),
(7,  3, 5, 4, '孙倩',   'T1005', '数据工程与智能分析实训室',
  3, '人工智能专业，数学基础较好，跟进过目标检测开源项目。',
  1, 0, 1, '未录取', DATE_SUB(@now, INTERVAL 14 HOUR), 0, @now),

/* stu4: 2 条待面试 */
(8,  4, 3, 2, '陈嘉禾', 'T1003', '嵌入式与物联网实训室',
  1, '物联网工程专业，做过 STM32 小车与温湿度采集，希望深入嵌入式系统与传感器网络。',
  1, 1, 1, '待面试', DATE_SUB(@now, INTERVAL 10 HOUR), 0, @now),
(9,  4, 2, 1, '王书宁', 'T1002', '智能视觉实训室',
  2, '物联网工程专业，做过 STM32 小车与温湿度采集，希望深入嵌入式系统与传感器网络。',
  1, 1, 1, '待面试', DATE_SUB(@now, INTERVAL 10 HOUR), 0, @now),

/* stu5: 3 条面试完成 */
(10, 5, 4, 3, '赵明远', 'T1004', 'Web 应用开发实训室',
  1, '数据科学与大数据技术专业，熟悉 SQL 与 Pandas，做过电商用户行为分析。',
  1, 1, 1, '面试完成', DATE_SUB(@now, INTERVAL 16 HOUR), 0, @now),
(11, 5, 5, 4, '孙倩',   'T1005', '数据工程与智能分析实训室',
  2, '数据科学与大数据技术专业，熟悉 SQL 与 Pandas，做过电商用户行为分析。',
  1, 1, 1, '面试完成', DATE_SUB(@now, INTERVAL 15 HOUR), 0, @now),
(12, 5, 2, 1, '王书宁', 'T1002', '智能视觉实训室',
  3, '数据科学与大数据技术专业，熟悉 SQL 与 Pandas，做过电商用户行为分析。',
  1, 1, 1, '面试完成', DATE_SUB(@now, INTERVAL 14 HOUR), 0, @now),

/* stu6~7~9: 未录取（后续调剂候选） */
(13, 6, 2, 1, '王书宁', 'T1002', '智能视觉实训室',
  1, '计算机专业，喜欢后端开发，了解 Spring Boot 与 MySQL。',
  1, 1, 1, '未录取', DATE_SUB(@now, INTERVAL 20 HOUR), 0, @now),
(14, 7, 3, 2, '陈嘉禾', 'T1003', '嵌入式与物联网实训室',
  1, '电子信息工程专业，做过 FPGA 与信号采集，希望跨方向学习。',
  0, 1, 1, '未录取', DATE_SUB(@now, INTERVAL 19 HOUR), 0, @now),
(15, 9, 4, 3, '赵明远', 'T1004', 'Web 应用开发实训室',
  1, '网络工程专业，熟悉 Linux 与网络协议，希望参与实训室的系统运维。',
  0, 0, 1, '未录取', DATE_SUB(@now, INTERVAL 18 HOUR), 0, @now);

/* ============================================
   面试记录（3 条）
   ============================================ */
INSERT INTO `interview`
    (`id`, `intention_id`, `attitude_score`, `logic_score`, `coding_score`, `commitment_score`, `total`, `comment`, `create_time`)
VALUES
(1, 1, 9, 8, 9, 8, 34, '基础扎实，项目描述清楚。', DATE_SUB(@now, INTERVAL 6 HOUR)),
(2, 4, 9, 9, 8, 9, 35, '数据基础与业务理解都好，直接进组。', DATE_SUB(@now, INTERVAL 10 HOUR)),
(3, 5, 8, 8, 8, 8, 32, '综合稳定，暂列递补第一顺位。', DATE_SUB(@now, INTERVAL 8 HOUR));

/* ============================================
   确认记录（1 条自动确认）
   ============================================ */
INSERT INTO `admission_confirmation`
    (`id`, `student_id`, `intention_id`, `mode`, `confirm_time`)
VALUES
(1, 2, 4, 'auto', DATE_SUB(@now, INTERVAL 9 HOUR));

/* ============================================
   操作日志
   ============================================ */
INSERT INTO `operation_log`
    (`id`, `operator_role`, `operator_id`, `operator_name`, `action`, `detail`, `create_time`)
VALUES
(1, 'system',  NULL, '系统',     '初始化演示数据', '生成 4 个实训室、5 名导师、9 名学生及演示意向', DATE_SUB(@now, INTERVAL 2 DAY)),
(2, 'teacher', 1,    '李伯谦',   '录入面试评分',   '张一鸣 / 智能视觉实训室 / 总分 34',            DATE_SUB(@now, INTERVAL 6 HOUR)),
(3, 'teacher', 5,    '孙倩',     '作出录取决定',   '李思远 / 拟录取（自动确认）',                   DATE_SUB(@now, INTERVAL 9 HOUR));

/* 可选：先 TRUNCATE 再执行本文件可完整重置演示环境。
   TRUNCATE TABLE operation_log;
   TRUNCATE TABLE admission_confirmation;
   TRUNCATE TABLE interview;
   TRUNCATE TABLE intention;
   TRUNCATE TABLE student;
   TRUNCATE TABLE teacher;
   TRUNCATE TABLE admin_user;
   TRUNCATE TABLE schedule_item;
   TRUNCATE TABLE notice;
   TRUNCATE TABLE training_room;
   TRUNCATE TABLE adjustment_record;
   TRUNCATE TABLE publicity_result;
   TRUNCATE TABLE user_session;
   TRUNCATE TABLE import_batch;
*/
