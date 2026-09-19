-- ============================================================================
-- 测试用建表脚本（H2 内存库，MySQL 兼容模式）
-- 用途：保证 mvn test 不依赖本机 MySQL 是否启动，可在任意机器上复现测试结果。
-- 与 database/schema.sql（MySQL 生产库）表结构一一对应，仅去掉 ENGINE/COMMENT 语法。
-- ============================================================================

DROP TABLE IF EXISTS supervision;
DROP TABLE IF EXISTS evaluation;
DROP TABLE IF EXISTS course_resource;
DROP TABLE IF EXISTS teaching_resource;
DROP TABLE IF EXISTS student_count;
DROP TABLE IF EXISTS offering_teacher;
DROP TABLE IF EXISTS offering;
DROP TABLE IF EXISTS course_intro;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS teacher;
DROP TABLE IF EXISTS sys_audit_log;
DROP TABLE IF EXISTS sys_config;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    password        VARCHAR(100) NOT NULL,
    real_name       VARCHAR(50),
    role_code       VARCHAR(30)  NOT NULL,
    teacher_id      BIGINT,
    department_code VARCHAR(20),
    enabled         TINYINT      DEFAULT 1,
    last_login_time TIMESTAMP,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);
CREATE INDEX idx_sys_user_role ON sys_user (role_code);

CREATE TABLE sys_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code   VARCHAR(30) NOT NULL,
    role_name   VARCHAR(50),
    description VARCHAR(255),
    data_scope  VARCHAR(20),
    sort_no     INT,
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

CREATE TABLE sys_permission (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    perm_code VARCHAR(50) NOT NULL,
    perm_name VARCHAR(80),
    module    VARCHAR(30),
    CONSTRAINT uk_sys_perm_code UNIQUE (perm_code)
);

CREATE TABLE sys_role_permission (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(30) NOT NULL,
    perm_code VARCHAR(50) NOT NULL,
    CONSTRAINT uk_role_perm UNIQUE (role_code, perm_code)
);

CREATE TABLE sys_config (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key   VARCHAR(50) NOT NULL,
    config_value VARCHAR(255),
    remark       VARCHAR(255),
    CONSTRAINT uk_sys_config_key UNIQUE (config_key)
);

CREATE TABLE sys_audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator    VARCHAR(50),
    role_code   VARCHAR(30),
    action      VARCHAR(30),
    target_type VARCHAR(40),
    target_id   VARCHAR(40),
    detail      VARCHAR(500),
    ip          VARCHAR(50),
    created_at  TIMESTAMP
);
CREATE INDEX idx_audit_created ON sys_audit_log (created_at);
CREATE INDEX idx_audit_operator ON sys_audit_log (operator);

CREATE TABLE teacher (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_no      VARCHAR(30) NOT NULL,
    name            VARCHAR(50),
    gender          VARCHAR(4),
    title           VARCHAR(20),
    department_code VARCHAR(20),
    department_name VARCHAR(60),
    email           VARCHAR(80),
    phone           VARCHAR(30),
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT uk_teacher_no UNIQUE (teacher_no)
);
CREATE INDEX idx_teacher_dept ON teacher (department_code);

CREATE TABLE course (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_code      VARCHAR(30) NOT NULL,
    course_name      VARCHAR(100),
    course_type      VARCHAR(20),
    credit           DECIMAL(3,1),
    total_hours      INT,
    department_code  VARCHAR(20),
    department_name  VARCHAR(60),
    owner_teacher_id BIGINT,
    intro            VARCHAR(1000),
    status           VARCHAR(20),
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    CONSTRAINT uk_course_code UNIQUE (course_code)
);
CREATE INDEX idx_course_dept ON course (department_code);
CREATE INDEX idx_course_owner ON course (owner_teacher_id);

CREATE TABLE course_intro (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id         BIGINT NOT NULL,
    content           VARCHAR(4000),
    objective         VARCHAR(2000),
    assessment_method VARCHAR(255),
    textbook          VARCHAR(255),
    updated_at        TIMESTAMP,
    CONSTRAINT uk_course_intro UNIQUE (course_id)
);

CREATE TABLE offering (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    offering_code   VARCHAR(40) NOT NULL,
    course_id       BIGINT,
    semester        VARCHAR(20),
    class_name      VARCHAR(60),
    teacher_id      BIGINT,
    department_code VARCHAR(20),
    capacity        INT,
    student_count   INT,
    offering_status VARCHAR(20),
    schedule_info   VARCHAR(255),
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT uk_offering_code UNIQUE (offering_code)
);
CREATE INDEX idx_offering_course ON offering (course_id);
CREATE INDEX idx_offering_semester ON offering (semester);
CREATE INDEX idx_offering_teacher ON offering (teacher_id);
CREATE INDEX idx_offering_dept ON offering (department_code);

CREATE TABLE offering_teacher (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    offering_id BIGINT NOT NULL,
    teacher_id  BIGINT NOT NULL,
    role_type   VARCHAR(20),
    CONSTRAINT uk_offering_teacher UNIQUE (offering_id, teacher_id)
);

CREATE TABLE student_count (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    offering_id BIGINT,
    stat_date   DATE,
    class_name  VARCHAR(60),
    major       VARCHAR(60),
    student_num INT,
    remark      VARCHAR(255)
);
CREATE INDEX idx_student_count_offering ON student_count (offering_id);

CREATE TABLE teaching_resource (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_name VARCHAR(120),
    resource_type VARCHAR(20),
    file_url      VARCHAR(300),
    file_size     BIGINT,
    offering_id   BIGINT,
    teacher_id    BIGINT,
    description   VARCHAR(500),
    status        VARCHAR(20),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);
CREATE INDEX idx_resource_offering ON teaching_resource (offering_id);
CREATE INDEX idx_resource_teacher ON teaching_resource (teacher_id);

CREATE TABLE course_resource (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id   BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    associated  TINYINT DEFAULT 1,
    CONSTRAINT uk_course_resource UNIQUE (course_id, resource_id)
);

CREATE TABLE evaluation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    offering_id     BIGINT,
    evaluation_type VARCHAR(20),
    semester        VARCHAR(20),
    score           DECIMAL(4,1),
    evaluator       VARCHAR(50),
    content         VARCHAR(500),
    evaluated_at    TIMESTAMP,
    created_at      TIMESTAMP
);
CREATE INDEX idx_eval_offering ON evaluation (offering_id);
CREATE INDEX idx_eval_type ON evaluation (evaluation_type);

CREATE TABLE supervision (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    offering_id   BIGINT,
    supervisor_id BIGINT,
    semester      VARCHAR(20),
    plan_date     DATE,
    finish_date   DATE,
    status        VARCHAR(20),
    score         DECIMAL(4,1),
    comment       VARCHAR(500),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);
CREATE INDEX idx_supervision_offering ON supervision (offering_id);
CREATE INDEX idx_supervision_status ON supervision (status);
