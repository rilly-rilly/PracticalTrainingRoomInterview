/*
 * H2 兼容模式 DDL（MySQL 模式）
 * 本机开发使用，Spring Boot 默认 profile 指向 H2。
 * MySQL 专用字段（JSON、ENUM、TINYINT UNSIGNED 严格 CHECK）请改用 sql/schema-mysql.sql
 */
DROP ALL OBJECTS;

CREATE TABLE training_room (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code              VARCHAR(16)  NOT NULL UNIQUE,
    name                   VARCHAR(64)  NOT NULL,
    direction              VARCHAR(128) NOT NULL,
    quota                  INTEGER      NOT NULL,
    training_plan          VARCHAR(1000) NOT NULL,
    assessment_requirement VARCHAR(1000) NOT NULL,
    tags                   VARCHAR(255),
    description            VARCHAR(500),
    create_time            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE teacher (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_no       VARCHAR(16)  NOT NULL UNIQUE,
    name             VARCHAR(32)  NOT NULL,
    title            VARCHAR(32)  NOT NULL DEFAULT '讲师',
    room_id          BIGINT       NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_teacher_room FOREIGN KEY (room_id) REFERENCES training_room (id)
);

CREATE TABLE student (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_no       VARCHAR(16)  NOT NULL UNIQUE,
    name             VARCHAR(32)  NOT NULL,
    major            VARCHAR(64)  NOT NULL,
    grade            VARCHAR(16)  NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE intention (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id            BIGINT       NOT NULL,
    teacher_id            BIGINT       NOT NULL,
    room_id               BIGINT       NOT NULL,
    teacher_name_snapshot VARCHAR(32)  NOT NULL,
    teacher_no_snapshot   VARCHAR(16)  NOT NULL,
    room_name_snapshot    VARCHAR(64)  NOT NULL,
    priority              INTEGER      NOT NULL,
    intro                 VARCHAR(2000) NOT NULL,
    accept_inner_adjust   INTEGER      NOT NULL DEFAULT 0,
    accept_global_adjust  INTEGER      NOT NULL DEFAULT 0,
    round                 INTEGER      NOT NULL DEFAULT 1,
    status                VARCHAR(32)  NOT NULL,
    submit_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    waitlist_rank         INTEGER      NOT NULL DEFAULT 0,
    update_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_int_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_int_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id),
    CONSTRAINT fk_int_room FOREIGN KEY (room_id) REFERENCES training_room (id)
);

CREATE TABLE interview (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    intention_id      BIGINT       NOT NULL UNIQUE,
    attitude_score    INTEGER      NOT NULL,
    logic_score       INTEGER      NOT NULL,
    coding_score      INTEGER      NOT NULL,
    commitment_score  INTEGER      NOT NULL,
    total             INTEGER      NOT NULL,
    comment           VARCHAR(500) NOT NULL DEFAULT '',
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iv_intention FOREIGN KEY (intention_id) REFERENCES intention (id)
);

CREATE TABLE admission_confirmation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id      BIGINT       NOT NULL,
    intention_id    BIGINT       NOT NULL,
    mode            VARCHAR(16)  NOT NULL,
    confirm_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cf_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_cf_intention FOREIGN KEY (intention_id) REFERENCES intention (id)
);

CREATE TABLE adjustment_record (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id            BIGINT       NOT NULL,
    round                 INTEGER      NOT NULL,
    type                  VARCHAR(16)  NOT NULL,
    from_room_snapshot    VARCHAR(64),
    to_room_snapshot      VARCHAR(64),
    to_teacher_snapshot   VARCHAR(32),
    result_snapshot       VARCHAR(200),
    operator_role         VARCHAR(10)  NOT NULL DEFAULT 'system',
    operator_id           BIGINT,
    create_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_adj_student FOREIGN KEY (student_id) REFERENCES student (id)
);

CREATE TABLE publicity_result (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id          BIGINT       NOT NULL,
    intention_id        BIGINT       NOT NULL,
    room_snapshot       VARCHAR(64)  NOT NULL,
    teacher_snapshot    VARCHAR(32)  NOT NULL,
    start_time          TIMESTAMP    NOT NULL,
    end_time            TIMESTAMP    NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    CONSTRAINT fk_pb_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_pb_intention FOREIGN KEY (intention_id) REFERENCES intention (id)
);

/* ---- 支撑表 ---- */
CREATE TABLE admin_user (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(32)  NOT NULL UNIQUE,
    name             VARCHAR(32)  NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_session (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    token        VARCHAR(100) NOT NULL UNIQUE,
    role         VARCHAR(10)  NOT NULL,
    user_id      BIGINT       NOT NULL,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE schedule_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    step_no     INTEGER     NOT NULL,
    key_value   VARCHAR(32) NOT NULL UNIQUE,
    title       VARCHAR(64) NOT NULL,
    start_time  TIMESTAMP   NOT NULL,
    end_time    TIMESTAMP   NOT NULL,
    description VARCHAR(500),
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notice (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(100)  NOT NULL,
    content      VARCHAR(1000) NOT NULL,
    level        VARCHAR(8)    NOT NULL DEFAULT '普通',
    publish_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE operation_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_role  VARCHAR(10)  NOT NULL,
    operator_id    BIGINT,
    operator_name  VARCHAR(32),
    action         VARCHAR(40)  NOT NULL,
    detail         VARCHAR(1000),
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE import_batch (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    type           VARCHAR(10) NOT NULL,
    batch_no       VARCHAR(40) NOT NULL UNIQUE,
    total_rows     INTEGER     NOT NULL,
    imported       INTEGER     NOT NULL,
    skipped        INTEGER     NOT NULL,
    strict_mode    INTEGER     NOT NULL DEFAULT 1,
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
