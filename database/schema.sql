-- =============================================================================
--  爱教学 · 教学质量全链路数字化管理平台
--  实验二 Sprint 1「查课程」—— 数据库建表脚本 (US-05 统一数据建模)
-- -----------------------------------------------------------------------------
--  数据库：MySQL 8.0.43
--  字符集：utf8mb4 / utf8mb4_general_ci
--  引擎  ：InnoDB
--  责任人：杨继鑫（16组 DRI / 技术与架构）
--  说明  ：
--    1. 本脚本只负责建库建表，不插入业务数据；样例数据见同目录 data.sql。
--    2. 每个字段与每张表均带 COMMENT（DoD 第③条"有注释"在数据库侧的体现）。
--    3. 外键统一采用"普通索引 + 注释说明"的方式表达关联关系，不写 FOREIGN KEY
--       约束：一是避免批量导入时的写入顺序依赖，二是便于演示环境按需清空单表。
--       逻辑外键已在各表索引与注释中标注（如 idx_offering_course_id 对应
--       offering.course_id -> course.id）。
--    4. 执行顺序：先 schema.sql，再 data.sql。
-- =============================================================================

DROP DATABASE IF EXISTS aijiaoxue;
CREATE DATABASE aijiaoxue DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE aijiaoxue;

-- =============================================================================
-- 一、系统与权限（US-01 角色与权限配置）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. sys_user 系统用户表
--    登录账号与角色绑定。teacher_id 仅 TEACHER 角色使用，用于把账号绑定到
--    teacher 表的教师档案，实现"教师只能看到本人课程"的数据范围控制。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '用户主键ID，自增',
  `username`        VARCHAR(50)  NOT NULL                          COMMENT '登录账号，全局唯一，如 admin/director/teacher1',
  `password`        VARCHAR(100) NOT NULL                          COMMENT '登录密码，BCrypt 加密存储（$2a$10$ 前缀），明文不落库',
  `real_name`       VARCHAR(50)  NOT NULL                          COMMENT '用户真实姓名，用于页面展示与操作日志',
  `role_code`       VARCHAR(30)  NOT NULL                          COMMENT '角色编码，取值：ADMIN/DIRECTOR/TEACHER/SUPERVISOR/COURSE_ADMIN，关联 sys_role.role_code',
  `teacher_id`      BIGINT       DEFAULT NULL                      COMMENT '关联教师ID，仅 TEACHER 角色填写，关联 teacher.id；其他角色为 NULL',
  `department_code` VARCHAR(20)  DEFAULT NULL                      COMMENT '所属院系编码，用于 DIRECTOR 角色的本教研室数据范围，关联 teacher.department_code；ADMIN 可为 NULL',
  `enabled`         TINYINT(1)   NOT NULL DEFAULT 1                COMMENT '账号启用状态：1=启用可登录，0=停用禁止登录',
  `last_login_time` DATETIME     DEFAULT NULL                      COMMENT '最近一次登录成功时间，用于操作审计；从未登录为 NULL',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，插入时自动生成',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间，更新时自动刷新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_role_code` (`role_code`),
  KEY `idx_sys_user_teacher_id` (`teacher_id`),
  KEY `idx_sys_user_department_code` (`department_code`),
  KEY `idx_sys_user_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表：登录账号、密码与角色绑定（US-01）';

-- -----------------------------------------------------------------------------
-- 2. sys_role 角色表
--    定义 Sprint 1 的五类角色及其数据可见范围。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色主键ID，自增',
  `role_code`   VARCHAR(30)  NOT NULL                COMMENT '角色编码，全局唯一：ADMIN/DIRECTOR/TEACHER/SUPERVISOR/COURSE_ADMIN',
  `role_name`   VARCHAR(50)  NOT NULL                COMMENT '角色名称：系统管理员/教研室主任/教师/教学督导/课程管理员',
  `description` VARCHAR(255) DEFAULT NULL            COMMENT '角色职责说明，描述该角色的业务定位与典型操作',
  `data_scope`  VARCHAR(20)  NOT NULL                COMMENT '数据可见范围：ALL=全量，DEPARTMENT=本教研室，SELF=仅本人',
  `sort_no`     INT          NOT NULL DEFAULT 0      COMMENT '显示排序号，数值越小越靠前，用于前端角色清单排序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_role_code` (`role_code`),
  KEY `idx_sys_role_data_scope` (`data_scope`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表：五类角色的名称与数据可见范围（US-01）';

-- -----------------------------------------------------------------------------
-- 3. sys_permission 权限表
--    权限码清单（对应 API契约.md 第 1—8 节的"权限码"列），是权限矩阵的勾选项来源。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '权限主键ID，自增',
  `perm_code` VARCHAR(50) NOT NULL                COMMENT '权限码，全局唯一，格式"模块:动作"，如 course:read、supervision:write',
  `perm_name` VARCHAR(80) NOT NULL                COMMENT '权限名称（中文），如"课程查看""听评课登记"',
  `module`    VARCHAR(30) NOT NULL                COMMENT '所属功能模块，用于权限矩阵分组展示：用户/角色/审计/课程/开课/教师/资源/评价/督导/覆盖率/工作台',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_perm_code` (`perm_code`),
  KEY `idx_sys_permission_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表：权限码全量清单，供角色权限矩阵勾选（US-01）';

-- -----------------------------------------------------------------------------
-- 4. sys_role_permission 角色权限关联表
--    角色与权限的多对多关系，ADMIN 拥有全部权限。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '关联主键ID，自增',
  `role_code` VARCHAR(30) NOT NULL                COMMENT '角色编码，关联 sys_role.role_code',
  `perm_code` VARCHAR(50) NOT NULL                COMMENT '权限码，关联 sys_permission.perm_code',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_code`,`perm_code`),
  KEY `idx_srp_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表：角色拥有的权限码集合（US-01）';

-- -----------------------------------------------------------------------------
-- 5. sys_config 系统配置表
--    键值对形式存放系统级开关，如当前学期、是否允许跨院查询。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '配置主键ID，自增',
  `config_key`   VARCHAR(50)  NOT NULL                COMMENT '配置键，全局唯一，如 system.name、current.semester、cross_department',
  `config_value` VARCHAR(255) NOT NULL                COMMENT '配置值，字符串存储，布尔值用 true/false 表示',
  `remark`       VARCHAR(255) DEFAULT NULL            COMMENT '配置说明，解释该配置的业务含义与影响范围',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表：系统级键值配置，含当前学期与跨院查询开关';

-- -----------------------------------------------------------------------------
-- 6. sys_audit_log 操作日志表
--    记录关键写操作与AI辅助决策留痕，支撑"全过程可追溯"与越权排查。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_audit_log`;
CREATE TABLE `sys_audit_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志主键ID，自增',
  `operator`    VARCHAR(50)  NOT NULL                COMMENT '操作人账号（sys_user.username），系统自动动作用 system',
  `role_code`   VARCHAR(30)  DEFAULT NULL            COMMENT '操作人角色编码，取值同 sys_role.role_code，便于按角色审计',
  `action`      VARCHAR(30)  NOT NULL                COMMENT '操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/EXPORT/IMPORT/QUERY',
  `target_type` VARCHAR(40)  DEFAULT NULL            COMMENT '操作对象类型，如 COURSE/OFFERING/TEACHER/USER/ROLE/SUPERVISION',
  `target_id`   VARCHAR(40)  DEFAULT NULL            COMMENT '操作对象ID或业务编号，字符串存储以兼容编号型主键',
  `detail`      VARCHAR(500) DEFAULT NULL            COMMENT '操作详情摘要，记录关键参数、变更前后值或导出条件',
  `ip`          VARCHAR(50)  DEFAULT NULL            COMMENT '客户端IP地址，含IPv4/IPv6，用于安全审计',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '日志产生时间，插入时自动生成',
  PRIMARY KEY (`id`),
  KEY `idx_sys_audit_log_operator` (`operator`),
  KEY `idx_sys_audit_log_role_code` (`role_code`),
  KEY `idx_sys_audit_log_action` (`action`),
  KEY `idx_sys_audit_log_target_type` (`target_type`),
  KEY `idx_sys_audit_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表：关键操作留痕，支撑全过程可追溯与权限审计';

-- =============================================================================
-- 二、基础主数据（US-02 / US-05，史诗一"课程信息管理"）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 7. teacher 教师表
--    教师档案，是课程负责人、开课授课教师、资源归属与督导听评课的主体。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `teacher`;
CREATE TABLE `teacher` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '教师主键ID，自增',
  `teacher_no`      VARCHAR(30) NOT NULL                COMMENT '教师工号，全局唯一，如 TE001',
  `name`            VARCHAR(50) NOT NULL                COMMENT '教师姓名，如 张伟',
  `gender`          VARCHAR(4)  DEFAULT NULL            COMMENT '性别：男/女',
  `title`           VARCHAR(20) DEFAULT NULL            COMMENT '职称：教授/副教授/讲师/助教',
  `department_code` VARCHAR(20) NOT NULL                COMMENT '所属院系编码：CS=计算机学院，SE=软件学院，MATH=数学学院，EN=外国语学院，PHY=物理学院',
  `department_name` VARCHAR(60) NOT NULL                COMMENT '所属院系名称（冗余存储，避免查询时联表）',
  `email`           VARCHAR(80) DEFAULT NULL            COMMENT '电子邮箱，用于教学通知',
  `phone`           VARCHAR(30) DEFAULT NULL            COMMENT '联系电话',
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，插入时自动生成',
  `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间，更新时自动刷新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_teacher_no` (`teacher_no`),
  KEY `idx_teacher_name` (`name`),
  KEY `idx_teacher_department_code` (`department_code`),
  KEY `idx_teacher_title` (`title`),
  KEY `idx_teacher_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师表：教师档案，课程负责人与开课授课教师主体（US-02）';

-- -----------------------------------------------------------------------------
-- 8. course 课程表
--    课程库主表，course_code 唯一；已开课的课程不允许直接删除（接口返回 409）。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `course`;
CREATE TABLE `course` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '课程主键ID，自增',
  `course_code`      VARCHAR(30)  NOT NULL                COMMENT '课程编号，全局唯一，如 CS101；重复导入时接口返回409',
  `course_name`      VARCHAR(100) NOT NULL                COMMENT '课程名称，如 程序设计基础',
  `course_type`      VARCHAR(20)  NOT NULL                COMMENT '课程类型：必修/选修/通识',
  `credit`           DECIMAL(3,1) NOT NULL                COMMENT '学分，一位小数，如 3.0',
  `total_hours`      INT          NOT NULL                COMMENT '总学时，整数，如 48',
  `department_code`  VARCHAR(20)  NOT NULL                COMMENT '开课院系编码，取值同 teacher.department_code',
  `department_name`  VARCHAR(60)  NOT NULL                COMMENT '开课院系名称（冗余存储）',
  `owner_teacher_id` BIGINT       DEFAULT NULL            COMMENT '课程负责人教师ID，关联 teacher.id；为 NULL 表示暂未指定负责人',
  `intro`            TEXT                                 COMMENT '课程简介摘要（列表页展示用），完整简介见 course_intro.content',
  `status`           VARCHAR(20)  NOT NULL DEFAULT '已发布' COMMENT '课程状态：草稿/已发布/已归档；仅"已发布"参与常规开课与统计',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，插入时自动生成',
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间，更新时自动刷新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_course_code` (`course_code`),
  KEY `idx_course_department_code` (`department_code`),
  KEY `idx_course_course_type` (`course_type`),
  KEY `idx_course_status` (`status`),
  KEY `idx_course_owner_teacher_id` (`owner_teacher_id`),
  KEY `idx_course_course_name` (`course_name`),
  KEY `idx_course_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表：课程库主表，含类型学分学时与课程负责人（US-02/US-05）';

-- -----------------------------------------------------------------------------
-- 9. course_intro 课程简介表
--    与 course 一对一（course_id 唯一），承载故事原文"查看课程简介"的完整内容。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `course_intro`;
CREATE TABLE `course_intro` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '课程简介主键ID，自增',
  `course_id`         BIGINT       NOT NULL                COMMENT '关联课程ID，关联 course.id；一门课程仅一条简介记录（唯一）',
  `content`           TEXT                                 COMMENT '课程简介全文：课程定位、主要内容与能力培养目标',
  `objective`         TEXT                                 COMMENT '教学目标：学生学完后应掌握的知识与能力',
  `assessment_method` VARCHAR(255) DEFAULT NULL            COMMENT '考核方式，如"平时30%+实验30%+期末40%"',
  `textbook`          VARCHAR(255) DEFAULT NULL            COMMENT '使用教材，含教材名称、作者与出版社',
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间，更新时自动刷新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_intro_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程简介表：课程简介全文、教学目标、考核方式与教材（US-02）';

-- =============================================================================
-- 三、开课与学生人次（US-02 / US-04，史诗一）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 10. offering 开课记录表
--     教学班（教学班=课程×学期×班级×授课教师），是学生人次、资源、评价、
--     督导听评课的挂接中心。覆盖率统计的分母即本表中 offering_status='正常开课' 的记录。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `offering`;
CREATE TABLE `offering` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '开课主键ID，自增',
  `offering_code`    VARCHAR(40)  NOT NULL                COMMENT '教学班编号，全局唯一，如 CS101-2025-1-01',
  `course_id`        BIGINT       NOT NULL                COMMENT '关联课程ID，关联 course.id',
  `semester`         VARCHAR(20)  NOT NULL                COMMENT '开课学期，格式"学年-学期"，如 2025-2026-1、2024-2025-2',
  `class_name`       VARCHAR(60)  NOT NULL                COMMENT '教学班名称，如 计算机2301班',
  `teacher_id`       BIGINT       NOT NULL                COMMENT '主讲教师ID，关联 teacher.id；统计"教师开课情况"以此字段为准',
  `department_code`  VARCHAR(20)  NOT NULL                COMMENT '开课院系编码，取值同 teacher.department_code；用于按院系统计',
  `capacity`         INT          NOT NULL DEFAULT 0      COMMENT '课容量（可容纳人数上限）',
  `student_count`    INT          NOT NULL DEFAULT 0      COMMENT '教学班汇总人数（快照值），由 student_count 明细汇总而来，便于列表快速展示',
  `offering_status`  VARCHAR(20)  NOT NULL DEFAULT '正常开课' COMMENT '开课状态：正常开课/停开/已结课；覆盖率分母只统计"正常开课"',
  `schedule_info`    VARCHAR(255) DEFAULT NULL            COMMENT '排课信息，如"周一3-4节 教一101"',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，插入时自动生成',
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间，更新时自动刷新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_offering_offering_code` (`offering_code`),
  KEY `idx_offering_course_id` (`course_id`),
  KEY `idx_offering_semester` (`semester`),
  KEY `idx_offering_teacher_id` (`teacher_id`),
  KEY `idx_offering_department_code` (`department_code`),
  KEY `idx_offering_offering_status` (`offering_status`),
  KEY `idx_offering_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开课记录表：教学班信息，学生人次/资源/评价/听评课的挂接中心（US-02/US-04）';

-- -----------------------------------------------------------------------------
-- 11. offering_teacher 开课授课教师表
--     一个教学班可有多位教师（主讲/辅导/合上），与 offering.teacher_id 主讲教师互补。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `offering_teacher`;
CREATE TABLE `offering_teacher` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `offering_id` BIGINT     NOT NULL                COMMENT '关联开课记录ID，关联 offering.id',
  `teacher_id` BIGINT      NOT NULL                COMMENT '关联教师ID，关联 teacher.id',
  `role_type`  VARCHAR(20) NOT NULL DEFAULT '主讲' COMMENT '授课角色：主讲/辅导/合上',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_offering_teacher` (`offering_id`,`teacher_id`),
  KEY `idx_ot_teacher_id` (`teacher_id`),
  KEY `idx_ot_role_type` (`role_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开课授课教师表：教学班的多教师分工（主讲/辅导/合上）';

-- -----------------------------------------------------------------------------
-- 12. student_count 学生人次表
--     按教学班、按统计日期记录分专业学生人数，是"学生人次"指标的数据源。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `student_count`;
CREATE TABLE `student_count` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `offering_id` BIGINT       NOT NULL                COMMENT '关联开课记录ID，关联 offering.id',
  `stat_date`   DATE         NOT NULL                COMMENT '统计日期，口径为学期初选课结束后的人数快照',
  `class_name`  VARCHAR(60)  NOT NULL                COMMENT '行政班级名称，如 计算机2301班',
  `major`       VARCHAR(60)  DEFAULT NULL            COMMENT '专业名称，如 计算机科学与技术',
  `student_num` INT          NOT NULL DEFAULT 0      COMMENT '该教学班该专业的学生人次（人数），口径见数据字典"学生人次口径"',
  `remark`      VARCHAR(255) DEFAULT NULL            COMMENT '备注，如"含重修5人""校企合作班"等特殊说明',
  PRIMARY KEY (`id`),
  KEY `idx_sc_offering_id` (`offering_id`),
  KEY `idx_sc_stat_date` (`stat_date`),
  KEY `idx_sc_major` (`major`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生人次表：按教学班与专业记录学生人数，学生人次指标数据源（US-02/US-03）';

-- =============================================================================
-- 四、教学资源（US-03，史诗二）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 13. teaching_resource 教学资源表
--     课件/教案/视频/习题/参考文献等资源；教师角色只能看到本人（teacher_id）资源。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `teaching_resource`;
CREATE TABLE `teaching_resource` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '资源主键ID，自增',
  `resource_name` VARCHAR(120) NOT NULL                COMMENT '资源名称，如"第1章 程序设计概述.pptx"',
  `resource_type` VARCHAR(20)  NOT NULL                COMMENT '资源类型：课件/教案/视频/习题/参考文献',
  `file_url`      VARCHAR(300) NOT NULL                COMMENT '资源存放路径或访问URL，指向文件服务器或对象存储',
  `file_size`     BIGINT       DEFAULT 0               COMMENT '文件大小，单位字节(Byte)；未知时为0',
  `offering_id`   BIGINT       DEFAULT NULL            COMMENT '关联开课记录ID，关联 offering.id；为 NULL 表示院系级公共资源，未绑定具体教学班',
  `teacher_id`    BIGINT       NOT NULL                COMMENT '资源上传教师ID，关联 teacher.id；教师角色的数据范围过滤字段',
  `description`   VARCHAR(500) DEFAULT NULL            COMMENT '资源描述：内容摘要与适用场景',
  `status`        VARCHAR(20)  NOT NULL DEFAULT '有效' COMMENT '资源状态：有效/失效/待审核；备课查看默认只展示"有效"资源',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，插入时自动生成',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间，更新时自动刷新',
  PRIMARY KEY (`id`),
  KEY `idx_tr_teacher_id` (`teacher_id`),
  KEY `idx_tr_offering_id` (`offering_id`),
  KEY `idx_tr_resource_type` (`resource_type`),
  KEY `idx_tr_status` (`status`),
  KEY `idx_tr_resource_name` (`resource_name`),
  KEY `idx_tr_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学资源表：课件/教案/视频/习题/参考文献（US-03）';

-- -----------------------------------------------------------------------------
-- 14. course_resource 课程资源挂接表
--     课程与资源的多对多挂接；associated=0 表示已解除挂接（软解绑，保留历史）。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `course_resource`;
CREATE TABLE `course_resource` (
  `id`          BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `course_id`   BIGINT     NOT NULL                COMMENT '关联课程ID，关联 course.id',
  `resource_id` BIGINT     NOT NULL                COMMENT '关联资源ID，关联 teaching_resource.id',
  `associated`  TINYINT(1) NOT NULL DEFAULT 1      COMMENT '挂接状态：1=已挂接（有效关联），0=已解除挂接（软解绑，接口 DELETE 置0）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_resource` (`course_id`,`resource_id`),
  KEY `idx_cr_resource_id` (`resource_id`),
  KEY `idx_cr_associated` (`associated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程资源挂接表：课程与资源多对多关联，支持软解绑（US-03）';

-- =============================================================================
-- 五、教学评价（史诗三，只读展示）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 15. evaluation 教学评价表
--     学生评教/督导评教/同行评议三类评价记录，是评教平均分的数据源。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `evaluation`;
CREATE TABLE `evaluation` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价主键ID，自增',
  `offering_id`     BIGINT       NOT NULL                COMMENT '关联开课记录ID，关联 offering.id；评价按教学班归集',
  `evaluation_type` VARCHAR(20)  NOT NULL                COMMENT '评价类型：学生评教/督导评教/同行评议',
  `semester`        VARCHAR(20)  NOT NULL                COMMENT '评价所属学期，格式同 offering.semester',
  `score`           DECIMAL(4,1) NOT NULL                COMMENT '评价得分，百分制，保留一位小数，有效区间 0.0—100.0',
  `evaluator`       VARCHAR(50)  DEFAULT NULL            COMMENT '评价人标识：学生评教为匿名编号，督导/同行为真实姓名',
  `content`         VARCHAR(500) DEFAULT NULL            COMMENT '评价内容：评价意见与改进建议原文',
  `evaluated_at`    DATETIME     NOT NULL                COMMENT '评价提交时间，用于按学期与时间范围统计',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，插入时自动生成',
  PRIMARY KEY (`id`),
  KEY `idx_eval_offering_id` (`offering_id`),
  KEY `idx_eval_evaluation_type` (`evaluation_type`),
  KEY `idx_eval_semester` (`semester`),
  KEY `idx_eval_score` (`score`),
  KEY `idx_eval_evaluated_at` (`evaluated_at`),
  KEY `idx_eval_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学评价表：学生评教/督导评教/同行评议记录（史诗三）';

-- =============================================================================
-- 六、督导与听评课（US-04）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 16. supervision 督导听评课表
--     督导对教学班的听课安排与结果。覆盖率分子 = 本表中 status='已完成' 的
--     开课记录去重计数（同一教学班多次听课只计 1）。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `supervision`;
CREATE TABLE `supervision` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '听评课主键ID，自增',
  `offering_id`  BIGINT       NOT NULL                COMMENT '关联开课记录ID，关联 offering.id；覆盖率按此列去重计数',
  `supervisor_id` BIGINT      NOT NULL                COMMENT '督导教师ID，关联 teacher.id',
  `semester`     VARCHAR(20)  NOT NULL                COMMENT '听课所属学期，格式同 offering.semester',
  `plan_date`    DATE         DEFAULT NULL            COMMENT '计划听课日期',
  `finish_date`  DATE         DEFAULT NULL            COMMENT '实际完成听课日期；status=已完成 时应有值，否则为 NULL',
  `status`       VARCHAR(20)  NOT NULL DEFAULT '待安排' COMMENT '听课状态：待安排/已安排/已完成；覆盖率仅统计"已完成"',
  `score`        DECIMAL(4,1) DEFAULT NULL            COMMENT '听课评分，百分制一位小数；未完成听课时为 NULL',
  `comment`      VARCHAR(500) DEFAULT NULL            COMMENT '听课评语与改进建议',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间，插入时自动生成',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间，更新时自动刷新',
  PRIMARY KEY (`id`),
  KEY `idx_sup_offering_id` (`offering_id`),
  KEY `idx_sup_supervisor_id` (`supervisor_id`),
  KEY `idx_sup_semester` (`semester`),
  KEY `idx_sup_status` (`status`),
  KEY `idx_sup_plan_date` (`plan_date`),
  KEY `idx_sup_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='督导听评课表：听课安排与结果，督导覆盖率唯一数据源（US-04）';

-- =============================================================================
--  建表完成：共 16 张表
--  执行 data.sql 导入样例数据后，可用 database/README.md 中的校验语句核对数据量。
-- =============================================================================
