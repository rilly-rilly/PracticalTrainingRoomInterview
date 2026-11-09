# 02 数据库表结构与初始化数据设计

> 本文档依据 `frontend/src/mock/seed.js`、`frontend/src/mock/server.js`、`frontend/src/api/index.js` 中的实际数据模型与业务规则编写，目标是把演示用 localStorage 数据模型落地为 MySQL 8 正式库。
>
> 约定：数据库 `trainee_selection`；存储引擎 `InnoDB`；字符集 `utf8mb4`；排序规则 `utf8mb4_0900_ai_ci`；字段统一 `snake_case`；主键统一 `BIGINT UNSIGNED AUTO_INCREMENT`；时间统一 `DATETIME`。DDL 基于 MySQL 8.0.16 及以上版本，`CHECK` 约束在该版本起真正生效。

## 1 总览

### 1.1 核心 8 张业务表

| 序号 | 表名 | 中文名 | 用途 | 主要关联 |
|---|---|---|---|---|
| 1 | `student` | 学生 | 保存学生登录账号、学号、姓名、专业、年级 | 一对多关联 `intention`、`admission_confirmation`、`adjustment_record`、`publicity_result` |
| 2 | `teacher` | 导师 | 保存导师登录账号、工号、职称、所属实训室 | 多对一关联 `training_room`；一对多关联 `intention` |
| 3 | `training_room` | 实训室 | 保存实训室编码、方向、招生名额、培养与考核要求 | 一对多关联 `teacher`、`intention` |
| 4 | `intention` | 意向填报记录 | 保存学生每一轮、每一个平行志愿，以及提交时的导师/实训室快照和流程状态 | 多对一关联 `student`、`teacher`、`training_room`；一对一关联 `interview` |
| 5 | `interview` | 面试考核记录 | 保存四项评分、总分、评语 | 一对一关联 `intention` |
| 6 | `admission_confirmation` | 拟录取确认记录 | 保存自动确认、手动确认、放弃、逾期四种终态记录 | 多对一关联 `student`、`intention` |
| 7 | `adjustment_record` | 调剂与补录记录 | 保存分轮补录、实训室内部调剂、全院跨实训室调剂、第三优先级约谈的全过程 | 多对一关联 `student`；可关联来源/目标实训室与目标导师 |
| 8 | `publicity_result` | 公示结果 | 保存最终公示名单、公示起止时间与公示/定稿状态 | 多对一关联 `student`、`intention`、`training_room`、`teacher` |

### 1.2 ER 关系说明

| 关系 | 基数 | 外键 | 业务含义 |
|---|---|---|---|
| 实训室 ↔ 导师 | **1 : N** | `teacher.room_id → training_room.id` | 一个实训室可有多名导师；每名导师只归属一个实训室 |
| 导师 ↔ 意向 | **1 : N** | `intention.teacher_id → teacher.id` | 一名导师可收到多名学生填报；每条意向只报考一名导师 |
| 学生 ↔ 意向 | **1 : N** | `intention.student_id → student.id` | 一名学生每轮最多 3 个平行意向，并可在第二轮产生调剂意向 |
| 实训室 ↔ 意向 | 1 : N | `intention.room_id → training_room.id` | 意向提交时按导师所属实训室冗余记录，用于名额统计与历史展示 |
| 意向 ↔ 面试 | **1 : 1** | `interview.intention_id → intention.id`，且唯一 | 一条意向最多录入一次面试；同一学生不同意向分别面试 |
| 学生 ↔ 确认记录 | **1 : N** | `admission_confirmation.student_id → student.id` | 学生可能有多个拟录取，确认唯一去向时其余拟录取生成放弃记录 |
| 意向 ↔ 确认记录 | 1 : 1（终态） | `admission_confirmation.intention_id → intention.id`，且唯一 | 一条意向最终只会有一条确认/放弃/逾期记录 |
| 学生 ↔ 调剂记录 | 1 : N | `adjustment_record.student_id → student.id` | 同一学生可先后经历补录、内部调剂、全院调剂、约谈 |
| 学生 ↔ 公示结果 | 1 : N（实际 1 条定稿） | `publicity_result.student_id → student.id` | 公示与定稿记录保留历史，最终名单按状态筛选 |

```text
training_room 1 ──< teacher / intention >── 1 student
intention     1 ── 1 interview
student       1 ──< admission_confirmation / adjustment_record / publicity_result
```

### 1.3 mock 字段与数据库字段对应

| mock 字段 | 数据库字段 | 说明 |
|---|---|---|
| `studentNo` / `teacherNo` | `student.student_no` / `teacher.teacher_no` | 登录账号，数据库唯一 |
| `pwdHash` | `password_hash` | mock 为演示哈希，正式库为 BCrypt |
| `roomId` + 三个 `*Snap` | `room_id` + `teacher_name_snapshot` / `teacher_no_snapshot` / `room_name_snapshot` | 关联 ID 用于当前业务，快照用于历史展示 |
| `submittedAt` / `createdAt` | `submit_time` / `create_time` | mock 使用毫秒时间戳，数据库使用 `DATETIME` |
| `acceptInnerAdjust` / `acceptGlobalAdjust` | `accept_inner_adjust` / `accept_global_adjust` | 是否服从内部/全院调剂 |
| `rank` | `waitlist_rank` | 递补顺位，`0` 表示未进入递补 |
| `adjustments[].fromRoomName` / `toRoomName` / `toTeacherName` | `adjustment_record.from_room_name_snapshot` / `to_room_name_snapshot` / `to_teacher_name_snapshot` | 调剂过程快照 |

## 2 核心 8 表 DDL

> 执行顺序必须为：`training_room` → `teacher` → `student` → `intention` → `interview` → `admission_confirmation` → `adjustment_record` → `publicity_result`。管理员表在 4.1 节单独给出，避免混入作业要求的核心 8 表。

### 2.1 建库与实训室表

```sql
CREATE DATABASE IF NOT EXISTS `trainee_selection`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `trainee_selection`;
SET NAMES utf8mb4;

CREATE TABLE `training_room` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `room_code` VARCHAR(16) NOT NULL COMMENT '实训室编码，如 RV-01，导入导师时按编码匹配',
  `name` VARCHAR(64) NOT NULL COMMENT '实训室名称',
  `direction` VARCHAR(128) NOT NULL COMMENT '研究方向',
  `quota` TINYINT UNSIGNED NOT NULL COMMENT '招生名额上限（1~20）',
  `training_plan` TEXT NOT NULL COMMENT '培养安排',
  `assessment_requirement` TEXT NOT NULL COMMENT '考核要求',
  `tags` JSON NULL COMMENT '方向标签 JSON 数组，对应 mock 的 tags',
  `description` VARCHAR(500) NULL COMMENT '实训室简介，对应 mock 的 description',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_code` (`room_code`),
  CONSTRAINT `chk_room_quota` CHECK (`quota` BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实训室';
```

### 2.2 导师表

```sql
CREATE TABLE `teacher` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `teacher_no` VARCHAR(16) NOT NULL COMMENT '工号，同时作为登录账号',
  `name` VARCHAR(32) NOT NULL COMMENT '姓名',
  `title` VARCHAR(32) NOT NULL DEFAULT '讲师' COMMENT '职称',
  `room_id` BIGINT UNSIGNED NOT NULL COMMENT '所属实训室',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希，实际 60 位，预留 100',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_no` (`teacher_no`),
  KEY `idx_teacher_room` (`room_id`),
  CONSTRAINT `fk_teacher_room` FOREIGN KEY (`room_id`)
    REFERENCES `training_room` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导师';
```

### 2.3 学生表

```sql
CREATE TABLE `student` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_no` VARCHAR(16) NOT NULL COMMENT '学号，同时作为登录账号',
  `name` VARCHAR(32) NOT NULL COMMENT '姓名',
  `major` VARCHAR(64) NOT NULL COMMENT '专业',
  `grade` VARCHAR(16) NOT NULL COMMENT '年级，如 2024 级',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希，实际 60 位，预留 100',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_no` (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生';
```

### 2.4 意向填报表

```sql
CREATE TABLE `intention` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，对应 mock 的 int-*',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 ID',
  `teacher_id` BIGINT UNSIGNED NOT NULL COMMENT '导师 ID',
  `room_id` BIGINT UNSIGNED NOT NULL COMMENT '提交时导师所属实训室 ID',
  `teacher_name_snapshot` VARCHAR(32) NOT NULL COMMENT '提交时导师姓名快照',
  `teacher_no_snapshot` VARCHAR(16) NOT NULL COMMENT '提交时导师工号快照',
  `room_name_snapshot` VARCHAR(64) NOT NULL COMMENT '提交时实训室名称快照',
  `priority` TINYINT UNSIGNED NOT NULL COMMENT '平行志愿顺序：1~3；第二轮调剂固定为 0',
  `intro` TEXT NOT NULL COMMENT '个人简介，服务端校验不少于 20 字',
  `accept_inner_adjust` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否服从实训室内调剂：0 否 1 是',
  `accept_global_adjust` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否服从全院跨实训室调剂：0 否 1 是',
  `round` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '轮次：1 第一轮填报；2 第二轮调剂/补录',
  `status` ENUM('待面试','面试完成','拟录取','递补','已确认','已放弃','已录取','未录取')
    NOT NULL DEFAULT '待面试' COMMENT '流程状态，8 个值固定',
  `submit_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `waitlist_rank` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '同实训室递补顺位，0 表示未进入递补',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_round_priority` (`student_id`, `round`, `priority`),
  UNIQUE KEY `uk_student_round_teacher` (`student_id`, `round`, `teacher_id`),
  KEY `idx_teacher_status` (`teacher_id`, `status`),
  KEY `idx_room_status` (`room_id`, `status`),
  KEY `idx_room_waitlist` (`room_id`, `status`, `waitlist_rank`),
  CONSTRAINT `fk_intention_student` FOREIGN KEY (`student_id`)
    REFERENCES `student` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `fk_intention_teacher` FOREIGN KEY (`teacher_id`)
    REFERENCES `teacher` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `fk_intention_room` FOREIGN KEY (`room_id`)
    REFERENCES `training_room` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `chk_intention_priority` CHECK (
    (`round` = 1 AND `priority` BETWEEN 1 AND 3)
    OR (`round` = 2 AND `priority` = 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='意向填报记录';
```

### 2.5 面试考核表

```sql
CREATE TABLE `interview` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，对应 mock 的 iv-*',
  `intention_id` BIGINT UNSIGNED NOT NULL COMMENT '意向 ID，一条意向只允许一次面试',
  `attitude_score` TINYINT UNSIGNED NOT NULL COMMENT '态度评分 1~10，对应 mock 的 attitude',
  `logic_score` TINYINT UNSIGNED NOT NULL COMMENT '逻辑评分 1~10，对应 mock 的 logic',
  `coding_score` TINYINT UNSIGNED NOT NULL COMMENT '编码评分 1~10，对应 mock 的 coding',
  `commitment_score` TINYINT UNSIGNED NOT NULL COMMENT '投入度评分 1~10，对应 mock 的 commitment',
  `total` TINYINT UNSIGNED NOT NULL COMMENT '总分，四项之和，范围 4~40',
  `comment` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '面试评语',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_intention` (`intention_id`),
  CONSTRAINT `fk_interview_intention` FOREIGN KEY (`intention_id`)
    REFERENCES `intention` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `chk_interview_scores` CHECK (
    `attitude_score` BETWEEN 1 AND 10
    AND `logic_score` BETWEEN 1 AND 10
    AND `coding_score` BETWEEN 1 AND 10
    AND `commitment_score` BETWEEN 1 AND 10
  ),
  CONSTRAINT `chk_interview_total` CHECK (
    `total` = `attitude_score` + `logic_score` + `coding_score` + `commitment_score`
    AND `total` BETWEEN 4 AND 40
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试考核记录';
```

### 2.6 拟录取确认表

```sql
CREATE TABLE `admission_confirmation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，对应 mock 的 cf-*',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 ID',
  `intention_id` BIGINT UNSIGNED NOT NULL COMMENT '意向 ID，一条意向只有一条终态记录',
  `mode` ENUM('auto','manual','give_up','expired') NOT NULL
    COMMENT 'auto 唯一拟录取自动确认；manual 学生自主确认；give_up 放弃其他拟录取；expired 逾期未确认',
  `confirm_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '确认/放弃/逾期处理时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_confirmation_intention` (`intention_id`),
  KEY `idx_confirmation_student` (`student_id`, `confirm_time`),
  CONSTRAINT `fk_confirmation_student` FOREIGN KEY (`student_id`)
    REFERENCES `student` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `fk_confirmation_intention` FOREIGN KEY (`intention_id`)
    REFERENCES `intention` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拟录取确认记录';
```

### 2.7 调剂与补录记录表

```sql
CREATE TABLE `adjustment_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，对应 mock 的 adj-*',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 ID',
  `round` TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '轮次：1 首轮；2 补录/调剂轮',
  `type` ENUM('inner','global','interview','supplement') NOT NULL
    COMMENT 'inner 实训室内部调剂；global 全院跨实训室调剂；interview 第三优先级约谈；supplement 分轮补录',
  `from_room_id` BIGINT UNSIGNED NULL COMMENT '来源实训室，补录/约谈可为空',
  `to_room_id` BIGINT UNSIGNED NULL COMMENT '目标实训室，约谈可为空',
  `to_teacher_id` BIGINT UNSIGNED NULL COMMENT '目标导师，约谈/补录未指定时可为空',
  `from_room_name_snapshot` VARCHAR(64) NULL COMMENT '来源实训室名称快照',
  `to_room_name_snapshot` VARCHAR(64) NULL COMMENT '目标实训室名称快照',
  `to_teacher_name_snapshot` VARCHAR(32) NULL COMMENT '目标导师姓名快照',
  `result` VARCHAR(200) NOT NULL COMMENT '结果说明，如“调剂成功并自动确认”“待约谈，由学院统一安排”',
  `operator_id` BIGINT UNSIGNED NULL COMMENT '操作人 ID；系统任务为空',
  `operator_role` ENUM('admin','teacher','system') NOT NULL DEFAULT 'admin' COMMENT '操作人角色',
  `operator_name_snapshot` VARCHAR(32) NOT NULL COMMENT '操作人姓名快照',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_adjustment_student` (`student_id`, `type`),
  KEY `idx_adjustment_round` (`round`, `type`, `create_time`),
  CONSTRAINT `fk_adjustment_student` FOREIGN KEY (`student_id`)
    REFERENCES `student` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `fk_adjustment_from_room` FOREIGN KEY (`from_room_id`)
    REFERENCES `training_room` (`id`) ON UPDATE RESTRICT ON DELETE SET NULL,
  CONSTRAINT `fk_adjustment_to_room` FOREIGN KEY (`to_room_id`)
    REFERENCES `training_room` (`id`) ON UPDATE RESTRICT ON DELETE SET NULL,
  CONSTRAINT `fk_adjustment_to_teacher` FOREIGN KEY (`to_teacher_id`)
    REFERENCES `teacher` (`id`) ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='调剂与补录记录';
```

各类 `type` 的字段表达：

- `supplement`：`round=2`，`from_room_id` 为空，`to_room_id`/`to_teacher_id` 为补录去向，`result` 记“递补录取”。
- `inner`：`from_room_id` 与 `to_room_id` 相同，`to_teacher_id` 为同室新导师。
- `global`：`from_room_id` 与 `to_room_id` 不同，`to_teacher_id` 为其他实训室导师。
- `interview`：`to_room_id`/`to_teacher_id` 为空，`result` 记“待约谈，由学院统一安排”。

### 2.8 公示结果表

```sql
CREATE TABLE `publicity_result` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，对应 mock 的 pub-*',
  `student_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 ID',
  `intention_id` BIGINT UNSIGNED NOT NULL COMMENT '被公示的已确认意向 ID',
  `room_id` BIGINT UNSIGNED NOT NULL COMMENT '录取实训室 ID',
  `teacher_id` BIGINT UNSIGNED NOT NULL COMMENT '录取导师 ID',
  `room_name_snapshot` VARCHAR(64) NOT NULL COMMENT '公示时实训室名称快照',
  `teacher_name_snapshot` VARCHAR(32) NOT NULL COMMENT '公示时导师姓名快照',
  `start_time` DATETIME NOT NULL COMMENT '公示开始时间',
  `end_time` DATETIME NOT NULL COMMENT '公示结束时间，默认开始时间加 1 天',
  `status` ENUM('public','final') NOT NULL DEFAULT 'public'
    COMMENT 'public 公示中；final 公示结束并定稿',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_publicity_intention` (`intention_id`),
  KEY `idx_publicity_status_end` (`status`, `end_time`),
  KEY `idx_publicity_student` (`student_id`),
  CONSTRAINT `fk_publicity_student` FOREIGN KEY (`student_id`)
    REFERENCES `student` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `fk_publicity_intention` FOREIGN KEY (`intention_id`)
    REFERENCES `intention` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `fk_publicity_room` FOREIGN KEY (`room_id`)
    REFERENCES `training_room` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `fk_publicity_teacher` FOREIGN KEY (`teacher_id`)
    REFERENCES `teacher` (`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `chk_publicity_time` CHECK (`end_time` > `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公示结果';
```

### 2.9 关键设计说明

#### 2.9.1 意向唯一约束与“最多 3 个平行意向”

| 约束 | 作用 | 说明 |
|---|---|---|
| `uk_student_round_priority (student_id, round, priority)` | 同一学生同一轮同一志愿位只能有一条记录 | 第一轮 `priority` 为 1~3，配合 `CHECK` 天然限制最多 3 条，且不会出现两个“第一志愿” |
| `uk_student_round_teacher (student_id, round, teacher_id)` | 同一学生同一轮不能重复填报同一导师 | 对应 mock 的“同一导师不能重复填报”校验 |
| `chk_intention_priority` | 限制第一轮只能 1~3，第二轮只能 0 | 第二轮自动调剂只保留最终去向一条意向 |

不建议只使用 `(student_id, priority)`：第二轮会与第一轮历史记录冲突，且历史不能被覆盖。
不建议只使用 `(student_id, teacher_id)`：同一导师可能在不同轮次再次出现，会误伤合法的调剂记录。
第二轮的所有调剂尝试（内部调剂、全院调剂、补录、约谈）记录在 `adjustment_record`，`intention` 只保存最终形成的第二条意向（`round=2`, `priority=0`）。若希望 `priority` 严格限定 1~3，可把第二轮调剂意向记为 `priority=1`，用 `round=2` 区分；当前保留 mock 的 0 作为“自动调剂生成”的语义。若未来产品要求第二轮也保留多个平行调剂志愿，只需把 `chk_intention_priority` 中 `round=2` 的限制放开为 1~3；两个唯一键已经具备，不需要改表结构。

#### 2.9.2 快照字段与历史不可变

`teacher_name_snapshot`、`teacher_no_snapshot`、`room_name_snapshot` 在提交意向或生成调剂意向时，由服务端从当前导师与实训室数据复制写入，前端只传 `teacher_id`，不传名称。

保留快照的原因：

| 场景 | 若只关联实时表 | 使用快照后 |
|---|---|---|
| 导师更换实训室 | 历史意向的实训室会被“改写”，名额统计与公示口径漂移 | 历史意向仍指向提交时的实训室，记录稳定 |
| 实训室更名 | 历史名单、面试记录显示新名称，无法还原当时场景 | 始终显示提交时名称 |
| 导师姓名/工号调整 | 历史公示与操作记录失去可追溯性 | 快照保留当时身份信息 |
| 面试、拟录取、公示查询 | 需要反复 JOIN 且结果随主数据变化 | 可直接读取快照，查询稳定且减少关联 |

`teacher_id`、`room_id` 仍保留外键，用于当前业务操作（如导师查看报考学生、按实训室统计名额）；展示与审计一律优先使用快照。导师转室、实训室改名后，不得批量回刷历史快照。

#### 2.9.3 8 个状态及流转

| 状态 | 含义 | 进入条件 | 允许流转到 | 是否占用名额 |
|---|---|---|---|---|
| 待面试 | 已提交意向，等待导师考核 | 学生提交意向 | 面试完成 | 否 |
| 面试完成 | 导师已录入四项评分 | 导师提交面试评分 | 拟录取、递补、未录取 | 否 |
| 拟录取 | 导师决定录取，等待学生确认 | 导师选择 admit 且名额未满 | 已确认、已放弃 | 是 |
| 递补 | 进入同实训室递补队列 | 导师选择 waitlist | 拟录取（空出名额时晋升） | 否 |
| 已确认 | 学生已锁定唯一去向 | 单条拟录取自动确认，或学生手动确认 | 已录取 | 是 |
| 已放弃 | 学生放弃其他拟录取，或逾期未确认 | 学生确认唯一去向时其余拟录取；管理员执行逾期处理 | 终态 | 否 |
| 已录取 | 公示结束、正式入组 | 公示期满且无异议 | 终态 | 是 |
| 未录取 | 导师拒绝或未入选 | 导师选择 reject | 终态；学生进入调剂流程，成功后生成第二轮意向 | 否 |

名额占用口径固定为 `拟录取`、`已确认`、`已录取` 三种状态，与 mock 的 `OCCUPIED` 常量一致。递补顺位由服务端在同一实训室内按 `MAX(waitlist_rank)+1` 生成，仅在 `status='递补'` 时有意义；取下一人时按 `(waitlist_rank, id)` 排序以避免并列。

#### 2.9.4 ENUM 与 VARCHAR + CHECK 的取舍

| 方案 | 优点 | 缺点 | 适用判断 |
|---|---|---|---|
| `ENUM` | 存储紧凑；数据库强校验；索引友好；状态值集中可读 | 新增状态需 `ALTER TABLE`；跨数据库迁移差；排序按枚举定义顺序 | 本作业状态固定为 8 个，选 `ENUM` |
| `VARCHAR(10) + CHECK` | 扩展方便；跨库兼容；SQL 可读性更好 | 占用略大；依赖 MySQL 8.0.16+ 的 `CHECK` 真正生效；仍有拼写风险 | 后续状态经常变化时改用此方案 |

本设计采用 `ENUM('待面试','面试完成','拟录取','递补','已确认','已放弃','已录取','未录取')`。若后续增加“候补确认中”等状态，迁移为 `VARCHAR(16) + CHECK`，并在服务层统一维护状态常量，避免散落字符串。

#### 2.9.5 名额控制必须在 Service 层事务内完成

`training_room.quota` 是招收上限，但 MySQL 8 的 `CHECK` 约束不能跨表、不能对关联表做聚合统计，因此无法用 DDL 直接表达“同一实训室拟录取/已确认/已录取人数之和不超过 quota”。也不建议用触发器：高并发下难定位冲突、错误信息不友好、调试成本高。

服务端在“导师决定拟录取”或“补录晋升”时执行：

```text
BEGIN;
1. SELECT quota FROM training_room WHERE id = :roomId FOR UPDATE;   -- 锁定该实训室，串行化同室录取
2. SELECT COUNT(*) FROM intention
   WHERE room_id = :roomId
     AND status IN ('拟录取','已确认','已录取')
     AND id <> :currentIntentionId;                                  -- 排除当前记录自身
3. 若 used + 1 > quota，抛 409（名额已满），ROLLBACK；
4. UPDATE intention SET status = '拟录取' WHERE id = :currentIntentionId;
5. COMMIT;
```

`idx_room_status` 支撑第 2 步统计。管理员调低 `quota` 时同样要先统计已占用人数，若 `quota < used` 则拒绝（对应 mock 的 409）。若后续采用“同一学生多条拟录取不重复计数”的口径，把 `COUNT(*)` 改为 `COUNT(DISTINCT student_id)`，需在需求中明确口径。

## 3 密码存储与初始密码

### 3.1 BCrypt 存储规则

| 项目 | 设计 |
|---|---|
| 算法 | BCrypt（Spring Security `BCryptPasswordEncoder` 或同等级实现） |
| 列类型 | `VARCHAR(100)`，BCrypt 实际输出 60 位，预留算法前缀与未来升级空间 |
| 成本因子 | 建议 `10`~`12`，课程环境取 `10` 兼顾性能 |
| 盐 | BCrypt 自带随机盐，无需单独 `salt` 列 |
| 登录校验 | `encoder.matches(rawPassword, password_hash)`，禁止字符串直接比较 |
| 接口返回 | 任何查询/详情接口都不返回 `password_hash`，DTO 中不包含该字段 |
| 日志 | 操作日志、异常日志、导入结果日志均不得出现明文密码或哈希 |

### 3.2 初始化账号导入与随机初始密码

mock 中 `adminImport` 的真实流程是：管理员上传学生/导师表格 → 校验必填与账号唯一 → 为每条有效记录生成随机初始密码 → 写入账号 → 在响应中一次性返回账号密码清单。正式库沿用该流程，但**只把哈希入库**。

| 步骤 | 处理 | 落库内容 |
|---|---|---|
| 1 | 管理员上传 Excel，字段：学号/工号、姓名、专业/年级或职称/实训室编号 | 不落库 |
| 2 | 校验必填、文件内重复、数据库已存在、实训室编码存在；严格模式失败整体回滚 | `import_batch` 记录统计与失败原因 |
| 3 | 生成随机初始密码：学生 `Stu@` + 6 位随机；导师 `Teach@` + 6 位随机 | 不落库 |
| 4 | 对初始密码执行 BCrypt，得到 `password_hash` | `student`/`teacher` 仅存哈希 |
| 5 | 事务内批量插入，唯一键兜底并发重复 | 账号数据 |
| 6 | 接口响应返回 `accounts: [{ no, name, password, roomName? }]`，仅供本次下载/复制 | 不落库、不写日志、不写 `import_batch` |

初始密码生成策略示例（伪代码）：

```text
ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"  // 去掉 0/O/1/I/l 等易混淆字符
random6  = 用 SecureRandom 从 ALPHABET 取 6 位
studentPwd = "Stu@"   + random6     // 例：Stu@K7mQ2x
teacherPwd = "Teach@" + random6     // 例：Teach@9fR3pW
hash = BCrypt(studentPwd 或 teacherPwd, cost = 10)
```

要点：随机串使用 `SecureRandom` 而非 `Math.random`；6 位、字符集约 56 个字符时空间约 56^6，足够课程场景使用；同一批次内如出现重复初始密码，重新生成即可。初始密码**只在导入响应中出现一次**，页面应提示管理员立即下载或分发；生产环境不要把 `accounts` 数组持久化到数据库、浏览器 localStorage 或磁盘日志。mock 为了演示把 `accounts` 放在 `db.imports`，正式库的 `import_batch` 明确不设密码列。

### 3.3 管理员账号密码

管理员只有 1 名，初始化时 `password_hash` 使用 BCrypt 占位符，现场部署必须替换为真实哈希：

```sql
-- 管理员表在 4.1 创建；部署时替换为真实哈希后执行：
-- UPDATE `admin` SET `password_hash` = '<60 位 BCrypt 哈希>' WHERE `username` = 'admin';
```

管理员初始密码建议由学院线下交付，不写入初始化 SQL、不提交到代码仓库；首次登录后可增加“强制修改密码”流程（本期不展开）。

## 4 初始化数据 SQL

### 4.1 管理员表（系统支撑表，非核心 8 表）

管理员登录不属于核心 8 张业务表，但为了让初始化 SQL 可执行、系统可登录，单独建 `admin` 表。它不计入作业要求的核心 8 表。

```sql
CREATE TABLE `admin` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(32) NOT NULL COMMENT '登录账号，固定为 admin',
  `name` VARCHAR(32) NOT NULL COMMENT '显示名称',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员账号（支撑表）';
```

### 4.2 4 个实训室

| 编码 | 名称 | 方向 | 名额 |
|---|---|---|---|
| RV-01 | 智能视觉实训室 | 计算机视觉 / 多模态感知 | 4 |
| RV-02 | 嵌入式与物联网实训室 | 嵌入式系统 / 传感器网络 | 3 |
| RV-03 | Web 应用开发实训室 | 全栈开发 / 工程化实践 | 3 |
| RV-04 | 数据工程与智能分析实训室 | 数据管道 / 可视化分析 | 2 |

```sql
INSERT INTO `training_room`
  (`id`, `room_code`, `name`, `direction`, `quota`,
   `training_plan`, `assessment_requirement`, `tags`, `description`)
VALUES
(1, 'RV-01', '智能视觉实训室', '计算机视觉 / 多模态感知', 4,
 '入组后前 6 周完成 Python、OpenCV 与 PyTorch 基础训练，随后进入工业缺陷检测项目，每周一次组会汇报。',
 '考核出勤率、每周任务完成度、组会汇报质量与期末项目答辩，连续两周未完成任务将退出实训室。',
 '["机器视觉","深度学习","模型部署"]',
 '面向工业检测与智能感知场景，配备 GPU 服务器与工业相机，与两家本地企业共建项目库。'),
(2, 'RV-02', '嵌入式与物联网实训室', '嵌入式系统 / 传感器网络', 3,
 '从裸机开发到 RTOS 迁移，完成一套环境监测节点与网关的联调，参与学院实验室设备改造。',
 '考核硬件调试记录、代码规范、节点稳定性测试与最终联调演示。',
 '["STM32","RTOS","物联网协议"]',
 '拥有示波器、逻辑分析仪与焊接工位，长期承接校园物联网设备维护任务。'),
(3, 'RV-03', 'Web 应用开发实训室', '全栈开发 / 工程化实践', 3,
 '以真实业务需求为驱动，完成前后端分离项目从需求评审到上线的完整流程，参与代码评审。',
 '考核代码质量、接口设计、联调效率与项目上线后的运维记录。',
 '["Vue 3","Spring Boot","MySQL"]',
 '承接学院内部信息化需求，强调工程规范、版本管理与接口文档。'),
(4, 'RV-04', '数据工程与智能分析实训室', '数据管道 / 可视化分析', 2,
 '参与校园数据治理项目，完成数据采集、清洗、指标建模与看板搭建，每两周产出一次分析报告。',
 '考核数据质量、分析结论的可解释性与看板交付完整度。',
 '["数据清洗","指标建模","可视化"]',
 '面向真实校园数据，强调数据口径、可复现分析与业务价值。');
```

### 4.3 管理员初始化记录

```sql
INSERT INTO `admin` (`id`, `username`, `name`, `password_hash`)
VALUES (1, 'admin', '学院管理员',
        '$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH_000000000000000000000');
-- 占位符不是可验证哈希；部署前用 BCryptPasswordEncoder.encode(管理员初始密码) 生成 60 位真实哈希替换，否则 admin 登录失败且不得保存明文。
```

### 4.4 为什么导师与学生数据由管理员导入，而不是预置

| 原因 | 说明 |
|---|---|
| 账号量动态变化 | 每学期学生名单、导师分工都会变化，预置数据无法覆盖后续学期 |
| 避免硬编码 | 把真实姓名、学号、工号写进 SQL 会固化隐私数据，也不利于课程作业复用 |
| 导入即校验唯一性 | 导入时统一校验学号/工号必填、文件内重复、数据库已存在、导师的实训室编码存在，数据库唯一键再做并发兜底 |
| 批量生成初始密码 | 每位学生/导师生成独立随机初始密码并只返回一次，避免统一默认密码 |
| 支持严格模式 | 任一行失败可整体回滚，保证名单一致；失败原因写入 `import_batch.failure_detail`，不写密码 |

因此初始化数据只包含 4 个实训室和 1 个管理员。导师与学生由管理员通过 Excel 导入；导入完成后才能登录，导师的 `room_id` 由“实训室编号”列解析得到。

## 5 支撑表说明

> 以下 5 张表不属于作业要求的核心 8 张业务表，但为登录态失效、流程时间窗口、公告、操作审计和导入批次追踪所必需。加上 4.1 节的 `admin`，系统共 6 张支撑/系统表。

| 表名 | 用途 | 为什么必需 |
|---|---|---|
| `auth_token` | 登录令牌会话 | mock 用内存 `sessions` 维持登录态；正式库需要服务端可失效、可过期的 token |
| `schedule` | 7 个流程阶段的时间窗口 | 意向填报、确认、补录、调剂、公示都依赖服务端时间校验，不能信任前端 |
| `notice` | 公告 | 首页公告、填报提醒、调剂说明 |
| `operation_log` | 操作日志 | 记录管理员导入、导师评分/决定、学生确认、调剂执行等关键动作 |
| `import_batch` | 账号导入批次 | 记录导入类型、成功/跳过数量、严格模式与失败原因，便于追溯 |

### 5.1 登录会话表
```sql
CREATE TABLE `auth_token` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `token_hash` CHAR(64) NOT NULL COMMENT 'SHA-256(原始 token)，库中不存明文',
  `role` ENUM('admin','teacher','student') NOT NULL COMMENT '登录角色',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '对应 admin/teacher/student 的主键',
  `expires_at` DATETIME NOT NULL COMMENT '失效时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_hash` (`token_hash`),
  KEY `idx_token_user` (`role`, `user_id`),
  KEY `idx_token_expire` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录令牌（支撑表）';
```

说明：`user_id` 是多态关联（可能是管理员、导师或学生），因此不设外键；原始 token 只在登录响应返回一次，库中只存 `SHA-256` 摘要，退出登录按摘要删除记录使旧 token 立即失效；定时任务清理 `expires_at < NOW()` 的记录。

### 5.2 流程日程表
```sql
CREATE TABLE `schedule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `schedule_key` VARCHAR(32) NOT NULL COMMENT '阶段标识：open/intent/interview/confirm/supplement/adjust/publicity，对应 mock 的 key',
  `step` TINYINT UNSIGNED NOT NULL COMMENT '展示顺序 1~7',
  `title` VARCHAR(64) NOT NULL COMMENT '阶段名称',
  `start_at` DATETIME NOT NULL COMMENT '开始时间',
  `end_at` DATETIME NOT NULL COMMENT '结束时间',
  `description` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '阶段说明',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_key` (`schedule_key`),
  UNIQUE KEY `uk_schedule_step` (`step`),
  CONSTRAINT `chk_schedule_time` CHECK (`end_at` > `start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程日程与时间窗口（支撑表）';
```

说明：服务端所有“窗口是否开放”的判断都以 `start_at <= NOW() <= end_at` 为准；mock 的毫秒时间戳在 API 层转换为 `DATETIME`。初始化时至少写入 `open/intent/interview/confirm/supplement/adjust/publicity` 7 条记录，否则学生无法提交意向。

### 5.3 公告表

```sql
CREATE TABLE `notice` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` VARCHAR(128) NOT NULL COMMENT '公告标题',
  `content` TEXT NOT NULL COMMENT '公告正文',
  `level` ENUM('普通','提醒','重要') NOT NULL DEFAULT '普通' COMMENT '公告级别',
  `publisher_id` BIGINT UNSIGNED NULL COMMENT '发布管理员 ID',
  `publish_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_notice_publish` (`publish_time`),
  KEY `idx_notice_level` (`level`),
  CONSTRAINT `fk_notice_publisher` FOREIGN KEY (`publisher_id`)
    REFERENCES `admin` (`id`) ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告（支撑表）';
```

### 5.4 操作日志表

```sql
CREATE TABLE `operation_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operator_role` ENUM('admin','teacher','student','system') NOT NULL COMMENT '操作人角色',
  `operator_id` BIGINT UNSIGNED NULL COMMENT '操作人 ID，系统任务为空',
  `operator_name_snapshot` VARCHAR(32) NOT NULL COMMENT '操作人姓名快照',
  `action` VARCHAR(64) NOT NULL COMMENT '动作，如“导入账号”“录入面试评分”“执行调剂”',
  `detail` VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '详情，禁止写入密码或密码哈希',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_log_time` (`create_time`),
  KEY `idx_log_operator` (`operator_role`, `operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志（支撑表）';
```

说明：`operator_id` 跨多种角色，故不设外键；`detail` 只记录业务摘要，绝不记录初始密码、密码哈希或完整 Excel 内容。

### 5.5 导入批次表

```sql
CREATE TABLE `import_batch` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `import_type` ENUM('student','teacher') NOT NULL COMMENT '导入类型',
  `operator_id` BIGINT UNSIGNED NULL COMMENT '操作管理员 ID',
  `operator_name_snapshot` VARCHAR(32) NOT NULL COMMENT '操作人姓名快照',
  `total_rows` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总行数',
  `imported_rows` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功导入行数',
  `skipped_rows` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '跳过/失败行数',
  `strict_mode` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否严格模式：0 否 1 是',
  `failure_detail` JSON NULL COMMENT '失败行与原因，不含密码',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
  PRIMARY KEY (`id`),
  KEY `idx_import_type_time` (`import_type`, `create_time`),
  CONSTRAINT `fk_import_operator` FOREIGN KEY (`operator_id`)
    REFERENCES `admin` (`id`) ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号导入批次（支撑表）';
```

说明：该表**不设账号、密码、密码哈希列**。导入响应中的 `accounts` 明文清单只在本次 HTTP 响应中返回一次；`failure_detail` 只记录行号、账号、失败原因，不能记录初始密码。

## 6 关键 SQL 示例

### 6.1 查询某导师的报考学生
```sql
SELECT i.id AS intention_id,
       s.student_no, s.name AS student_name, s.major, s.grade,
       i.priority, i.status, i.intro, i.submit_time,
       iv.attitude_score, iv.logic_score, iv.coding_score, iv.commitment_score,
       iv.total, iv.comment
FROM `intention` i
JOIN `student` s ON s.id = i.student_id
LEFT JOIN `interview` iv ON iv.intention_id = i.id
WHERE i.teacher_id = ?
ORDER BY iv.total DESC, s.student_no ASC;
```

### 6.2 统计某实训室已占用名额

```sql
SELECT r.id, r.room_code, r.name, r.quota,
       COUNT(i.id) AS used,
       r.quota - COUNT(i.id) AS remaining
FROM `training_room` r
LEFT JOIN `intention` i
       ON i.room_id = r.id
      AND i.status IN ('拟录取','已确认','已录取')
WHERE r.id = ?
GROUP BY r.id, r.room_code, r.name, r.quota;

-- 若业务口径要求“同一学生多条记录只算 1 人”，将 COUNT(i.id) 改为 COUNT(DISTINCT i.student_id)。
```

### 6.3 查询学生全部意向及提交时快照

```sql
SELECT i.id AS intention_id, i.round, i.priority, i.status,
       i.teacher_name_snapshot, i.teacher_no_snapshot, i.room_name_snapshot,
       i.waitlist_rank, i.accept_inner_adjust, i.accept_global_adjust,
       i.intro, i.submit_time
FROM `intention` i
WHERE i.student_id = ?
ORDER BY i.round ASC, i.priority ASC, i.id ASC;
```

### 6.4 按递补顺位取下一人

```sql
-- 在“有非递补名额释放”的事务内执行；FOR UPDATE 锁住候选行，避免并发补录同一人
SELECT i.id AS intention_id, i.student_id, s.student_no, s.name, i.waitlist_rank
FROM `intention` i
JOIN `student` s ON s.id = i.student_id
WHERE i.room_id = ?
  AND i.status = '递补'
  AND i.waitlist_rank > 0
ORDER BY i.waitlist_rank ASC, i.id ASC
LIMIT 1
FOR UPDATE;

-- 取出后在同一事务内将状态置为“拟录取”，并按 2.9.5 的名额校验逻辑处理；
-- 若该生仅此一条拟录取，再自动生成 admission_confirmation(mode='auto') 并置为“已确认”。
```

### 6.5 汇总公示名单并公示、定稿

```sql
-- 5a. 汇总可公示名单：从“已确认”意向生成，使用快照展示历史口径
SELECT s.student_no, s.name, s.major,
       i.id AS intention_id,
       i.room_name_snapshot AS room_name,
       i.teacher_name_snapshot AS teacher_name
FROM `intention` i
JOIN `student` s ON s.id = i.student_id
WHERE i.status = '已确认'
ORDER BY i.room_id, i.teacher_id, s.student_no;

-- 5b. 生成公示记录，公示 1 天；NOT EXISTS 保证同一意向不重复公示
INSERT INTO `publicity_result`
  (`student_id`, `intention_id`, `room_id`, `teacher_id`,
   `room_name_snapshot`, `teacher_name_snapshot`,
   `start_time`, `end_time`, `status`)
SELECT s.id, i.id, i.room_id, i.teacher_id,
       i.room_name_snapshot, i.teacher_name_snapshot,
       NOW(), NOW() + INTERVAL 1 DAY, 'public'
FROM `intention` i
JOIN `student` s ON s.id = i.student_id
LEFT JOIN `publicity_result` p ON p.intention_id = i.id
WHERE i.status = '已确认'
  AND p.id IS NULL;

-- 5c. 公示期满后定稿，并把对应意向置为“已录取”（同一事务）
START TRANSACTION;
UPDATE `publicity_result`
SET `status` = 'final'
WHERE `status` = 'public' AND `end_time` <= NOW();

UPDATE `intention`
SET `status` = '已录取'
WHERE `status` = '已确认'
  AND `id` IN (
    SELECT `intention_id` FROM `publicity_result` WHERE `status` = 'final'
  );
COMMIT;
```

说明：mock 为了课堂演示提供 `force` 提前结束公示；正式环境应只允许 `end_time <= NOW()` 时定稿，或由管理员显式确认“公示无异议”。定稿后 `publicity_result.status='final'`，最终录取名单可直接按 `status='final'` 查询，学生端看到 `已录取`。
