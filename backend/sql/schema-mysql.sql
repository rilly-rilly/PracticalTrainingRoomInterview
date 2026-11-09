/*
 * MySQL 8 DDL（正式库，严格对应 docs/02-database-design.md）
 * 本机安装 MySQL 8 后：USE trainee_selection; SOURCE sql/schema-mysql.sql;
 * 开发阶段请用 H2（schema-h2.sql）+ profile=default
 */
CREATE DATABASE IF NOT EXISTS `trainee_selection`
    DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `trainee_selection`;
SET NAMES utf8mb4;

CREATE TABLE `training_room` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `room_code` VARCHAR(16) NOT NULL COMMENT '实训室编码，如 RV-01',
    `name` VARCHAR(64) NOT NULL COMMENT '实训室名称',
    `direction` VARCHAR(128) NOT NULL COMMENT '研究方向',
    `quota` TINYINT UNSIGNED NOT NULL COMMENT '招生名额上限（1~20）',
    `training_plan` TEXT NOT NULL COMMENT '培养安排',
    `assessment_requirement` TEXT NOT NULL COMMENT '考核要求',
    `tags` JSON NULL COMMENT '方向标签 JSON 数组',
    `description` VARCHAR(500) NULL COMMENT '实训室简介',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_room_code` (`room_code`),
    CONSTRAINT `chk_room_quota` CHECK (`quota` BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实训室';

CREATE TABLE `teacher` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `teacher_no` VARCHAR(16) NOT NULL COMMENT '工号/登录账号',
    `name` VARCHAR(32) NOT NULL,
    `title` VARCHAR(32) NOT NULL DEFAULT '讲师',
    `room_id` BIGINT UNSIGNED NOT NULL,
    `password_hash` VARCHAR(100) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_teacher_no` (`teacher_no`),
    KEY `idx_teacher_room` (`room_id`),
    CONSTRAINT `fk_teacher_room` FOREIGN KEY (`room_id`) REFERENCES `training_room` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导师';

CREATE TABLE `student` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `student_no` VARCHAR(16) NOT NULL,
    `name` VARCHAR(32) NOT NULL,
    `major` VARCHAR(64) NOT NULL,
    `grade` VARCHAR(16) NOT NULL,
    `password_hash` VARCHAR(100) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_student_no` (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生';

CREATE TABLE `intention` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT UNSIGNED NOT NULL,
    `teacher_id` BIGINT UNSIGNED NOT NULL,
    `room_id` BIGINT UNSIGNED NOT NULL,
    `teacher_name_snapshot` VARCHAR(32) NOT NULL,
    `teacher_no_snapshot` VARCHAR(16) NOT NULL,
    `room_name_snapshot` VARCHAR(64) NOT NULL,
    `priority` TINYINT UNSIGNED NOT NULL,
    `intro` TEXT NOT NULL,
    `accept_inner_adjust` TINYINT(1) NOT NULL DEFAULT 0,
    `accept_global_adjust` TINYINT(1) NOT NULL DEFAULT 0,
    `round` TINYINT UNSIGNED NOT NULL DEFAULT 1,
    `status` ENUM('待面试','面试完成','拟录取','递补','已确认','已放弃','已录取','未录取') NOT NULL DEFAULT '待面试',
    `submit_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `waitlist_rank` INT UNSIGNED NOT NULL DEFAULT 0,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_student_round_priority` (`student_id`, `round`, `priority`),
    UNIQUE KEY `uk_student_round_teacher` (`student_id`, `round`, `teacher_id`),
    KEY `idx_teacher_status` (`teacher_id`, `status`),
    KEY `idx_room_status` (`room_id`, `status`),
    CONSTRAINT `fk_intention_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
    CONSTRAINT `fk_intention_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`),
    CONSTRAINT `fk_intention_room` FOREIGN KEY (`room_id`) REFERENCES `training_room` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='意向填报记录';

CREATE TABLE `interview` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `intention_id` BIGINT UNSIGNED NOT NULL UNIQUE,
    `attitude_score` TINYINT UNSIGNED NOT NULL,
    `logic_score` TINYINT UNSIGNED NOT NULL,
    `coding_score` TINYINT UNSIGNED NOT NULL,
    `commitment_score` TINYINT UNSIGNED NOT NULL,
    `total` TINYINT UNSIGNED NOT NULL,
    `comment` VARCHAR(500) NOT NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_interview_intention` FOREIGN KEY (`intention_id`) REFERENCES `intention` (`id`),
    CONSTRAINT `chk_interview_scores` CHECK (
        `attitude_score` BETWEEN 1 AND 10 AND `logic_score` BETWEEN 1 AND 10
        AND `coding_score` BETWEEN 1 AND 10 AND `commitment_score` BETWEEN 1 AND 10
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试考核记录';

CREATE TABLE `admission_confirmation` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT UNSIGNED NOT NULL,
    `intention_id` BIGINT UNSIGNED NOT NULL,
    `mode` ENUM('auto','manual','give_up','expired') NOT NULL,
    `confirm_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_cf_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
    CONSTRAINT `fk_cf_intention` FOREIGN KEY (`intention_id`) REFERENCES `intention` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拟录取确认记录';

CREATE TABLE `adjustment_record` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT UNSIGNED NOT NULL,
    `round` TINYINT UNSIGNED NOT NULL,
    `type` ENUM('inner','global','supplement','interview') NOT NULL,
    `from_room_snapshot` VARCHAR(64),
    `to_room_snapshot` VARCHAR(64),
    `to_teacher_snapshot` VARCHAR(32),
    `result_snapshot` VARCHAR(200),
    `operator_role` VARCHAR(10) NOT NULL DEFAULT 'system',
    `operator_id` BIGINT UNSIGNED,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_adj_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='调剂与补录记录';

CREATE TABLE `publicity_result` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT UNSIGNED NOT NULL,
    `intention_id` BIGINT UNSIGNED NOT NULL,
    `room_snapshot` VARCHAR(64) NOT NULL,
    `teacher_snapshot` VARCHAR(32) NOT NULL,
    `start_time` DATETIME NOT NULL,
    `end_time` DATETIME NOT NULL,
    `status` ENUM('PUBLISHING','FINISHED') NOT NULL,
    CONSTRAINT `fk_pb_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
    CONSTRAINT `fk_pb_intention` FOREIGN KEY (`intention_id`) REFERENCES `intention` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公示结果';

/* 支撑表 */
CREATE TABLE `admin_user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(32) NOT NULL UNIQUE,
    `name` VARCHAR(32) NOT NULL,
    `password_hash` VARCHAR(100) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员账户';

CREATE TABLE `user_session` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `token` VARCHAR(100) NOT NULL UNIQUE,
    `role` VARCHAR(10) NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录会话';

CREATE TABLE `schedule_item` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `step_no` TINYINT UNSIGNED NOT NULL,
    `key_value` VARCHAR(32) NOT NULL UNIQUE,
    `title` VARCHAR(64) NOT NULL,
    `start_time` DATETIME NOT NULL,
    `end_time` DATETIME NOT NULL,
    `description` VARCHAR(500),
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='选拔日程';

CREATE TABLE `notice` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(100) NOT NULL,
    `content` VARCHAR(1000) NOT NULL,
    `level` VARCHAR(8) NOT NULL DEFAULT '普通',
    `publish_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告';

CREATE TABLE `operation_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `operator_role` VARCHAR(10) NOT NULL,
    `operator_id` BIGINT UNSIGNED,
    `operator_name` VARCHAR(32),
    `action` VARCHAR(40) NOT NULL,
    `detail` VARCHAR(1000),
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';

CREATE TABLE `import_batch` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `type` VARCHAR(10) NOT NULL,
    `batch_no` VARCHAR(40) NOT NULL UNIQUE,
    `total_rows` INT NOT NULL,
    `imported` INT NOT NULL,
    `skipped` INT NOT NULL,
    `strict_mode` TINYINT(1) NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导入批次';
