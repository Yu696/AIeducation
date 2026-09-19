-- =============================================================================
--  爱教学 · 教学质量全链路数字化管理平台
--  实验二 Sprint 1「查课程」—— 样例数据导入脚本 (US-05 数据导入)
-- -----------------------------------------------------------------------------
--  【样例数据来源与性质声明】
--  本文件数据均为教学演示用虚构样例数据，不代表任何真实教学数据；
--  学生人次与评教分数为模拟生成，用于验证查询与统计功能。
-- -----------------------------------------------------------------------------
--  执行前提：先执行 schema.sql 建库建表。
--  执行方式：mysql -uroot -p123456 < data.sql
--  数据量  ：院系5 / 教师12 / 课程24 / 开课30(正常24) / 学生人次40
--            资源25 / 课程资源挂接20 / 评价60 / 听评课20 / 演示账号6
--  覆盖率  ：16(已完成听评课的开课记录) / 24(正常开课记录) = 66.7%
-- =============================================================================

USE aijiaoxue;

SET FOREIGN_KEY_CHECKS = 0;
SET NAMES utf8mb4;

-- ============================ 一、系统与权限（US-01） ============================

-- 5 类角色：data_scope: ALL=全量 / DEPARTMENT=本教研室 / SELF=仅本人
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `data_scope`, `sort_no`) VALUES (1, 'ADMIN', '系统管理员', '维护账号、角色与权限矩阵，查看操作日志，拥有系统全部功能权限', 'ALL', 1);
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `data_scope`, `sort_no`) VALUES (2, 'DIRECTOR', '教研室主任', '统筹本院系课程、开课与教师信息，查看本院系资源与评教概览；是否可跨院由 sys_config.cross_department 控制', 'DEPARTMENT', 2);
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `data_scope`, `sort_no`) VALUES (3, 'TEACHER', '教师', '查看本人课程、学生人次与备课资源，查看本人评教结果；数据范围仅限本人', 'SELF', 3);
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `data_scope`, `sort_no`) VALUES (4, 'SUPERVISOR', '教学督导', '查看全校开课情况与督导听评课数据，登记听课结果，统计督导覆盖率', 'ALL', 4);
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `data_scope`, `sort_no`) VALUES (5, 'COURSE_ADMIN', '课程管理员', '维护课程库与开课基础数据，维护教学资源；查询范围为全校数据', 'ALL', 5);

INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (1, 'user:read', '用户查看', '用户');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (2, 'user:write', '用户维护', '用户');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (3, 'role:read', '角色查看', '角色');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (4, 'role:write', '角色权限配置', '角色');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (5, 'audit:read', '操作日志查看', '审计');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (6, 'course:read', '课程查看', '课程');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (7, 'course:write', '课程维护', '课程');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (8, 'offering:read', '开课查看', '开课');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (9, 'offering:write', '开课维护', '开课');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (10, 'teacher:read', '教师查看', '教师');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (11, 'teacher:write', '教师维护', '教师');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (12, 'resource:read', '资源查看', '资源');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (13, 'resource:write', '资源维护', '资源');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (14, 'resource:prep', '备课查看', '资源');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (15, 'evaluation:read', '评价查看', '评价');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (16, 'supervision:read', '听评课查看', '督导');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (17, 'supervision:write', '听评课登记', '督导');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (18, 'coverage:read', '覆盖率查看', '覆盖率');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (19, 'dashboard:read', '工作台通用', '工作台');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (20, 'dashboard:director', '主任工作台', '工作台');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (21, 'dashboard:teacher', '教师工作台', '工作台');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (22, 'dashboard:supervisor', '督导工作台', '工作台');
INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `module`) VALUES (23, 'dashboard:admin', '管理员工作台', '工作台');

INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (1, 'ADMIN', 'user:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (2, 'ADMIN', 'user:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (3, 'ADMIN', 'role:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (4, 'ADMIN', 'role:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (5, 'ADMIN', 'audit:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (6, 'ADMIN', 'course:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (7, 'ADMIN', 'course:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (8, 'ADMIN', 'offering:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (9, 'ADMIN', 'offering:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (10, 'ADMIN', 'teacher:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (11, 'ADMIN', 'teacher:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (12, 'ADMIN', 'resource:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (13, 'ADMIN', 'resource:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (14, 'ADMIN', 'resource:prep');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (15, 'ADMIN', 'evaluation:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (16, 'ADMIN', 'supervision:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (17, 'ADMIN', 'supervision:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (18, 'ADMIN', 'coverage:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (19, 'ADMIN', 'dashboard:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (20, 'ADMIN', 'dashboard:director');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (21, 'ADMIN', 'dashboard:teacher');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (22, 'ADMIN', 'dashboard:supervisor');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (23, 'ADMIN', 'dashboard:admin');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (24, 'DIRECTOR', 'course:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (25, 'DIRECTOR', 'offering:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (26, 'DIRECTOR', 'teacher:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (27, 'DIRECTOR', 'resource:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (28, 'DIRECTOR', 'resource:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (29, 'DIRECTOR', 'resource:prep');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (30, 'DIRECTOR', 'evaluation:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (31, 'DIRECTOR', 'coverage:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (32, 'DIRECTOR', 'dashboard:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (33, 'DIRECTOR', 'dashboard:director');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (34, 'TEACHER', 'course:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (35, 'TEACHER', 'offering:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (36, 'TEACHER', 'resource:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (37, 'TEACHER', 'resource:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (38, 'TEACHER', 'resource:prep');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (39, 'TEACHER', 'evaluation:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (40, 'TEACHER', 'dashboard:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (41, 'TEACHER', 'dashboard:teacher');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (42, 'SUPERVISOR', 'course:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (43, 'SUPERVISOR', 'offering:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (44, 'SUPERVISOR', 'teacher:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (45, 'SUPERVISOR', 'evaluation:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (46, 'SUPERVISOR', 'supervision:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (47, 'SUPERVISOR', 'supervision:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (48, 'SUPERVISOR', 'coverage:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (49, 'SUPERVISOR', 'dashboard:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (50, 'SUPERVISOR', 'dashboard:supervisor');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (51, 'COURSE_ADMIN', 'course:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (52, 'COURSE_ADMIN', 'course:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (53, 'COURSE_ADMIN', 'offering:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (54, 'COURSE_ADMIN', 'offering:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (55, 'COURSE_ADMIN', 'teacher:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (56, 'COURSE_ADMIN', 'resource:read');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (57, 'COURSE_ADMIN', 'resource:write');
INSERT INTO `sys_role_permission` (`id`, `role_code`, `perm_code`) VALUES (58, 'COURSE_ADMIN', 'dashboard:read');

-- 角色-权限条数：ADMIN=23（全部权限） DIRECTOR=10 TEACHER=8 SUPERVISOR=9 COURSE_ADMIN=8，合计 58 条

-- 演示账号 6 个，密码统一 123456（BCrypt 加密存储，明文不落库）
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role_code`, `teacher_id`, `department_code`, `enabled`, `last_login_time`, `created_at`, `updated_at`) VALUES (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'ADMIN', NULL, NULL, 1, '2025-09-19 08:05:00', '2025-09-01 09:00:00', '2025-09-19 08:05:00');
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role_code`, `teacher_id`, `department_code`, `enabled`, `last_login_time`, `created_at`, `updated_at`) VALUES (2, 'director', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '周婷', 'DIRECTOR', NULL, 'MATH', 1, '2025-09-19 08:12:00', '2025-09-01 09:05:00', '2025-09-19 08:12:00');
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role_code`, `teacher_id`, `department_code`, `enabled`, `last_login_time`, `created_at`, `updated_at`) VALUES (3, 'teacher1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张伟', 'TEACHER', 1, 'CS', 1, '2025-09-19 08:20:00', '2025-09-01 09:10:00', '2025-09-19 08:20:00');
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role_code`, `teacher_id`, `department_code`, `enabled`, `last_login_time`, `created_at`, `updated_at`) VALUES (4, 'teacher2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李娜', 'TEACHER', 2, 'CS', 1, '2025-09-19 08:25:00', '2025-09-01 09:12:00', '2025-09-19 08:25:00');
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role_code`, `teacher_id`, `department_code`, `enabled`, `last_login_time`, `created_at`, `updated_at`) VALUES (5, 'supervisor', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '教学督导', 'SUPERVISOR', 7, NULL, 1, '2025-09-19 08:30:00', '2025-09-01 09:15:00', '2025-09-19 08:30:00');
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role_code`, `teacher_id`, `department_code`, `enabled`, `last_login_time`, `created_at`, `updated_at`) VALUES (6, 'courseadmin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '课程管理员', 'COURSE_ADMIN', NULL, NULL, 1, '2025-09-19 08:35:00', '2025-09-01 09:18:00', '2025-09-19 08:35:00');

-- director 账号：真实姓名周婷，所属院系 MATH；cross_department=false 时仅可查询 MATH 数据
-- teacher1 → teacher.id=1（张伟，CS）；teacher2 → teacher.id=2（李娜，CS）
-- supervisor → teacher.id=7（吴磊，MATH 教授），兼任教学督导

-- 系统配置：cross_department 控制教研室主任是否可跨院查询
INSERT INTO `sys_config` (`id`, `config_key`, `config_value`, `remark`) VALUES (1, 'system.name', '爱教学-教学质量全链路数字化管理平台', '前端标题与登录页展示的系统名称');
INSERT INTO `sys_config` (`id`, `config_key`, `config_value`, `remark`) VALUES (2, 'current.semester', '2025-2026-1', '当前学期，作为开课/评教/督导相关查询的默认学期筛选值');
INSERT INTO `sys_config` (`id`, `config_key`, `config_value`, `remark`) VALUES (3, 'cross_department', 'false', '教研室主任是否可跨院查询：false=仅限本教研室(本院系)数据；true=可查询全校数据。由 DIRECTOR 角色数据范围逻辑读取');


INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (1, 'system', 'ADMIN', 'IMPORT', 'SAMPLE_DATA', 'INIT', '导入 Sprint 1 样例数据：教师12/课程24/开课30/学生人次40/资源25/评价60/听评课20', '127.0.0.1', '2025-09-19 08:00:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (2, 'admin', 'ADMIN', 'LOGIN', 'USER', '1', '系统管理员登录成功', '127.0.0.1', '2025-09-19 08:05:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (3, 'director', 'DIRECTOR', 'LOGIN', 'USER', '2', '教研室主任登录成功，数据范围=本院系(MATH)', '127.0.0.1', '2025-09-19 08:12:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (4, 'teacher1', 'TEACHER', 'LOGIN', 'USER', '3', '教师张伟登录成功，数据范围=仅本人(teacher_id=1)', '127.0.0.1', '2025-09-19 08:20:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (5, 'teacher2', 'TEACHER', 'LOGIN', 'USER', '4', '教师李娜登录成功，数据范围=仅本人(teacher_id=2)', '127.0.0.1', '2025-09-19 08:25:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (6, 'supervisor', 'SUPERVISOR', 'LOGIN', 'USER', '5', '教学督导登录成功', '127.0.0.1', '2025-09-19 08:30:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (7, 'courseadmin', 'COURSE_ADMIN', 'LOGIN', 'USER', '6', '课程管理员登录成功', '127.0.0.1', '2025-09-19 08:35:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (8, 'supervisor', 'SUPERVISOR', 'QUERY', 'COVERAGE', 'ALL', '查询督导覆盖率：分母24条正常开课，分子16条已完成听评课，覆盖率66.7%', '127.0.0.1', '2025-09-19 08:42:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (9, 'teacher1', 'TEACHER', 'QUERY', 'RESOURCE', 'PREP', '备课查看：查询本人(teacher_id=1)教学班的可用资源', '127.0.0.1', '2025-09-19 08:50:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (10, 'admin', 'ADMIN', 'UPDATE', 'ROLE', 'TEACHER', '调整角色 TEACHER 的权限码集合', '127.0.0.1', '2025-09-19 09:05:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (11, 'courseadmin', 'COURSE_ADMIN', 'EXPORT', 'COURSE', 'CSV', '导出课程清单CSV，条件：全部院系', '127.0.0.1', '2025-09-19 09:15:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (12, 'director', 'DIRECTOR', 'QUERY', 'TEACHER', '6', '查看教师周婷的课程简介与开课情况', '127.0.0.1', '2025-09-19 09:22:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (13, 'supervisor', 'SUPERVISOR', 'QUERY', 'OFFERING', 'ALL', '查询2025-2026-1学期正常开课清单，按院系筛选', '127.0.0.1', '2025-09-19 09:30:00');
INSERT INTO `sys_audit_log` (`id`, `operator`, `role_code`, `action`, `target_type`, `target_id`, `detail`, `ip`, `created_at`) VALUES (14, 'teacher1', 'TEACHER', 'QUERY', 'EVALUATION', 'SELF', '查看本人课程评教均分', '127.0.0.1', '2025-09-19 09:38:00');

-- ============================ 二、基础主数据（US-02 / US-05） ============================

-- 教师 12 位，覆盖 CS/SE/MATH/EN/PHY 五个院系；每位教师恰好负责 2 门课程
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (1, 'TE001', '张伟', '男', '教授', 'CS', '计算机学院', 'zhangwei@aijiaoxue.edu.cn', '13801010001', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (2, 'TE002', '李娜', '女', '副教授', 'CS', '计算机学院', 'lina@aijiaoxue.edu.cn', '13801010002', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (3, 'TE003', '王强', '男', '讲师', 'CS', '计算机学院', 'wangqiang@aijiaoxue.edu.cn', '13801010003', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (4, 'TE004', '刘洋', '男', '教授', 'SE', '软件学院', 'liuyang@aijiaoxue.edu.cn', '13801010004', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (5, 'TE005', '赵敏', '女', '副教授', 'SE', '软件学院', 'zhaomin@aijiaoxue.edu.cn', '13801010005', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (6, 'TE006', '周婷', '女', '副教授', 'MATH', '数学与统计学院', 'zhouting@aijiaoxue.edu.cn', '13801010006', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (7, 'TE007', '吴磊', '男', '教授', 'MATH', '数学与统计学院', 'wulei@aijiaoxue.edu.cn', '13801010007', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (8, 'TE008', '孙杰', '男', '讲师', 'EN', '外国语学院', 'sunjie@aijiaoxue.edu.cn', '13801010008', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (9, 'TE009', '郑爽', '女', '副教授', 'EN', '外国语学院', 'zhengshuang@aijiaoxue.edu.cn', '13801010009', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (10, 'TE010', '何俊', '男', '教授', 'PHY', '物理学院', 'hejun@aijiaoxue.edu.cn', '13801010010', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (11, 'TE011', '罗丹', '女', '副教授', 'PHY', '物理学院', 'luodan@aijiaoxue.edu.cn', '13801010011', '2025-08-20 10:00:00', '2025-08-20 10:00:00');
INSERT INTO `teacher` (`id`, `teacher_no`, `name`, `gender`, `title`, `department_code`, `department_name`, `email`, `phone`, `created_at`, `updated_at`) VALUES (12, 'TE012', '高翔', '男', '讲师', 'PHY', '物理学院', 'gaoxiang@aijiaoxue.edu.cn', '13801010012', '2025-08-20 10:00:00', '2025-08-20 10:00:00');

INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (1, 'CS101', '程序设计基础', '必修', 4.0, 64, 'CS', '计算机学院', 1, '程序设计基础是计算机学院面向本科生开设的必修课程，共4.0学分、64学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (2, 'CS102', '面向对象程序设计', '必修', 3.5, 56, 'CS', '计算机学院', 1, '面向对象程序设计是计算机学院面向本科生开设的必修课程，共3.5学分、56学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (3, 'CS201', '数据结构', '必修', 4.0, 64, 'CS', '计算机学院', 2, '数据结构是计算机学院面向本科生开设的必修课程，共4.0学分、64学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (4, 'CS202', '计算机组成原理', '必修', 3.5, 56, 'CS', '计算机学院', 2, '计算机组成原理是计算机学院面向本科生开设的必修课程，共3.5学分、56学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (5, 'CS203', '计算机网络', '选修', 3.0, 48, 'CS', '计算机学院', 3, '计算机网络是计算机学院面向本科生开设的选修课程，共3.0学分、48学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (6, 'CS204', 'Python程序设计', '选修', 2.5, 40, 'CS', '计算机学院', 3, 'Python程序设计是计算机学院面向本科生开设的选修课程，共2.5学分、40学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (7, 'SE301', '软件工程导论', '必修', 3.0, 48, 'SE', '软件学院', 4, '软件工程导论是软件学院面向本科生开设的必修课程，共3.0学分、48学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (8, 'SE302', '软件项目管理', '必修', 3.0, 48, 'SE', '软件学院', 4, '软件项目管理是软件学院面向本科生开设的必修课程，共3.0学分、48学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (9, 'SE303', '软件测试技术', '选修', 2.5, 40, 'SE', '软件学院', 5, '软件测试技术是软件学院面向本科生开设的选修课程，共2.5学分、40学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (10, 'SE304', '软件体系结构', '选修', 3.0, 48, 'SE', '软件学院', 5, '软件体系结构是软件学院面向本科生开设的选修课程，共3.0学分、48学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (11, 'MATH101', '高等数学A', '必修', 5.0, 80, 'MATH', '数学与统计学院', 6, '高等数学A是数学与统计学院面向本科生开设的必修课程，共5.0学分、80学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (12, 'MATH102', '概率论与数理统计', '必修', 3.5, 56, 'MATH', '数学与统计学院', 6, '概率论与数理统计是数学与统计学院面向本科生开设的必修课程，共3.5学分、56学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (13, 'MATH201', '线性代数', '必修', 3.0, 48, 'MATH', '数学与统计学院', 7, '线性代数是数学与统计学院面向本科生开设的必修课程，共3.0学分、48学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (14, 'MATH202', '离散数学', '必修', 3.0, 48, 'MATH', '数学与统计学院', 7, '离散数学是数学与统计学院面向本科生开设的必修课程，共3.0学分、48学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (15, 'EN101', '大学英语（一）', '通识', 3.0, 48, 'EN', '外国语学院', 8, '大学英语（一）是外国语学院面向本科生开设的通识课程，共3.0学分、48学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (16, 'EN102', '英语视听说', '通识', 2.0, 32, 'EN', '外国语学院', 8, '英语视听说是外国语学院面向本科生开设的通识课程，共2.0学分、32学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (17, 'EN201', '学术英语写作', '选修', 2.0, 32, 'EN', '外国语学院', 9, '学术英语写作是外国语学院面向本科生开设的选修课程，共2.0学分、32学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (18, 'EN202', '英汉翻译基础', '选修', 2.0, 32, 'EN', '外国语学院', 9, '英汉翻译基础是外国语学院面向本科生开设的选修课程，共2.0学分、32学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (19, 'EN203', '英语国家社会与文化', '通识', 2.0, 32, 'EN', '外国语学院', 12, '英语国家社会与文化是外国语学院面向本科生开设的通识课程，共2.0学分、32学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (20, 'EN204', '英语语音训练', '选修', 1.5, 24, 'EN', '外国语学院', 12, '英语语音训练是外国语学院面向本科生开设的选修课程，共1.5学分、24学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (21, 'PHY101', '大学物理', '必修', 4.0, 64, 'PHY', '物理学院', 10, '大学物理是物理学院面向本科生开设的必修课程，共4.0学分、64学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (22, 'PHY102', '物理实验', '必修', 1.5, 24, 'PHY', '物理学院', 10, '物理实验是物理学院面向本科生开设的必修课程，共1.5学分、24学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (23, 'PHY201', '理论力学', '选修', 3.0, 48, 'PHY', '物理学院', 11, '理论力学是物理学院面向本科生开设的选修课程，共3.0学分、48学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');
INSERT INTO `course` (`id`, `course_code`, `course_name`, `course_type`, `credit`, `total_hours`, `department_code`, `department_name`, `owner_teacher_id`, `intro`, `status`, `created_at`, `updated_at`) VALUES (24, 'PHY202', '电磁学', '选修', 3.0, 48, 'PHY', '物理学院', 11, '电磁学是物理学院面向本科生开设的选修课程，共3.0学分、48学时。课程围绕核心知识体系与工程实践能力组织教学，配套课件、案例与实验资源，支撑本专业人才培养方案中的对应毕业要求。', '已发布', '2025-08-21 09:00:00', '2025-08-21 09:00:00');

-- 课程 24 门：必修 12 门 / 选修 9 门 / 通识 3 门；院系分布 CS6 SE4 MATH4 EN6 PHY4

INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (1, 1, '本课程是计算机类专业的第一门专业基础课，以C语言为载体讲授程序设计的基本方法与思想，涵盖数据类型、流程控制、函数、数组、指针、结构体与文件操作。课程强调动手实践，通过不少于8个编程实验帮助学生建立计算思维与调试能力。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《程序设计基础》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (2, 2, '本课程在程序设计基础之上讲授面向对象的核心思想与实现技术，涵盖类与对象、封装继承多态、接口与抽象类、异常处理、集合框架与常用设计模式，并通过综合项目训练学生的面向对象建模能力。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《面向对象程序设计》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (3, 3, '本课程系统讲授线性表、栈与队列、串、树与二叉树、图等基本数据结构及其存储实现，并结合查找与排序算法分析时间与空间复杂度。课程是后续操作系统、数据库、编译原理等课程的先修基础。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《数据结构》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (4, 4, '本课程讲授计算机硬件系统的组成与工作原理，包括运算器、控制器、存储器、总线与输入输出系统，重点讲解指令系统、CPU数据通路与流水线技术，帮助学生建立软硬件协同的系统观。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《计算机组成原理》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (5, 5, '本课程讲授计算机网络的体系结构与协议原理，以TCP/IP五层模型为主线，涵盖物理层编码、数据链路层可靠传输、网络层路由、传输层拥塞控制与应用层典型协议。', '在核心知识基础上拓展专业视野，具备针对特定方向的实践与工程应用能力。', '平时成绩40% + 课程项目/报告40% + 期末考核20%', '《计算机网络》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (6, 6, '本课程讲授Python语言的核心语法与常用生态库，涵盖数据类型、函数式编程、文件与数据处理、正则表达式、NumPy与Pandas基础，并通过数据分析小项目强化工程实践能力。', '在核心知识基础上拓展专业视野，具备针对特定方向的实践与工程应用能力。', '平时成绩40% + 课程项目/报告40% + 期末考核20%', '《Python程序设计》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (7, 7, '本课程系统介绍软件工程的基本概念、过程模型与方法学，涵盖需求工程、软件设计、编码规范、软件测试与维护，重点讲解敏捷方法与Scrum框架下的团队协作开发模式。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《软件工程导论》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (8, 8, '本课程以项目管理知识体系为主线，讲授范围、进度、成本、质量、风险与团队管理方法，并结合Scrum框架开展贯穿式项目实践，培养学生作为DRI或Scrum Master的组织协调能力。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《软件项目管理》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (9, 9, '本课程讲授软件测试的基本理论与工程方法，涵盖黑盒与白盒测试、单元与集成测试、测试用例设计、自动化测试框架与缺陷管理流程，强调测试左移与质量内建。', '在核心知识基础上拓展专业视野，具备针对特定方向的实践与工程应用能力。', '平时成绩40% + 课程项目/报告40% + 期末考核20%', '《软件测试技术》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (10, 10, '本课程讲授软件体系结构的设计原理与风格，涵盖分层、微内核、事件驱动、微服务等架构模式，并通过架构评审案例训练学生的架构权衡与决策能力。', '在核心知识基础上拓展专业视野，具备针对特定方向的实践与工程应用能力。', '平时成绩40% + 课程项目/报告40% + 期末考核20%', '《软件体系结构》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (11, 11, '本课程是理工类各专业的核心数学基础课，讲授极限与连续、一元与多元微积分、无穷级数、常微分方程等内容，注重数学建模意识与计算能力的培养。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《高等数学A》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (12, 12, '本课程讲授随机事件与概率、随机变量及其分布、数字特征、大数定律与中心极限定理、参数估计与假设检验，强调概率统计方法在工程与数据分析中的应用。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《概率论与数理统计》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (13, 13, '本课程讲授行列式、矩阵、向量组的线性相关性、线性方程组、特征值与二次型等内容，为后续数值计算、机器学习与工程建模提供必要的代数工具。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《线性代数》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (14, 14, '本课程讲授命题逻辑、集合与关系、函数、图论基础、树与代数系统等内容，为计算机类专业学习算法设计与离散建模提供数学基础。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《离散数学》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (15, 15, '本课程面向非英语专业本科生，训练英语听说读写的综合应用能力，采用主题式教学与任务型活动，兼顾学术英语的过渡需求，配套线上听说训练资源。', '建立跨学科的知识视野与通用能力，提升科学素养与表达沟通能力。', '平时成绩50% + 期末考查50%', '《大学英语（一）》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (16, 16, '本课程聚焦英语听说能力的专项训练，通过真实语料听辨、情景对话与主题陈述，提升学生的语音辨识、口语表达与跨文化沟通能力。', '建立跨学科的知识视野与通用能力，提升科学素养与表达沟通能力。', '平时成绩50% + 期末考查50%', '《英语视听说》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (17, 17, '本课程面向已具备一定英语基础的学生，讲授学术论文的语篇结构、文献综述写法、论证表达与引用规范，训练学生撰写规范英文学术文本的能力。', '在核心知识基础上拓展专业视野，具备针对特定方向的实践与工程应用能力。', '平时成绩40% + 课程项目/报告40% + 期末考核20%', '《学术英语写作》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (18, 18, '本课程讲授英汉互译的基本理论与常用技巧，涵盖词义选择、语序调整、长句拆分、文化负载词处理与译文润色，并通过大量对比练习提升翻译准确度与地道性。', '在核心知识基础上拓展专业视野，具备针对特定方向的实践与工程应用能力。', '平时成绩40% + 课程项目/报告40% + 期末考核20%', '《英汉翻译基础》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (19, 19, '本课程介绍英语主要国家的历史地理、政治制度、教育体系与风俗文化，通过语篇阅读与主题讨论提升学生的跨文化理解力与英语表达能力。', '建立跨学科的知识视野与通用能力，提升科学素养与表达沟通能力。', '平时成绩50% + 期末考查50%', '《英语国家社会与文化》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (20, 20, '本课程针对中国学生的常见语音问题进行专项训练，涵盖元音与辅音发音部位、连读弱读、重音节奏与语调模式，帮助学生改善语音面貌、提升口语可懂度。', '在核心知识基础上拓展专业视野，具备针对特定方向的实践与工程应用能力。', '平时成绩40% + 课程项目/报告40% + 期末考核20%', '《英语语音训练》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (21, 21, '本课程讲授力学、热学、电磁学、振动与波动、光学及近代物理基础，注重物理思想与实验方法的结合，是理工类专业的重要通识基础课。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《大学物理》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (22, 22, '本课程是大学物理的配套实验课，涵盖力学、电磁学与光学的基础测量实验，训练学生使用仪器、处理数据与撰写实验报告的能力。', '掌握课程核心知识体系，具备运用所学方法分析并解决本专业典型问题的能力，达成专业毕业要求中的对应指标点。', '平时成绩30% + 实验/作业30% + 期末考试40%', '《物理实验》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (23, 23, '本课程系统讲授质点与质点系力学、刚体力学、拉格朗日方程与哈密顿力学初步，强调用数学工具描述与求解力学问题，是理论物理系列课程的入门课程。', '在核心知识基础上拓展专业视野，具备针对特定方向的实践与工程应用能力。', '平时成绩40% + 课程项目/报告40% + 期末考核20%', '《理论力学》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');
INSERT INTO `course_intro` (`id`, `course_id`, `content`, `objective`, `assessment_method`, `textbook`, `updated_at`) VALUES (24, 24, '本课程系统讲授静电场、稳恒磁场、电磁感应与麦克斯韦方程组，注重场的观点与数学描述方法，是电磁学方向的骨干课程。', '在核心知识基础上拓展专业视野，具备针对特定方向的实践与工程应用能力。', '平时成绩40% + 课程项目/报告40% + 期末考核20%', '《电磁学》主编，高等教育出版社（第3版）', '2025-08-21 09:30:00');

-- 课程简介 24 条，与课程一对一
-- 故事原文"查看5位教师的课程简介与开课情况"由 GET /api/teachers/{id} 联查
--   course_intro + offering 满足；12 位教师每人 2 门课程，均有开课记录

-- ============================ 三、开课与学生人次（US-02 / US-04） ============================

-- 开课 30 条：2025-2026-1 共 22 条（全部正常开课）；2024-2025-2 共 8 条（3正常/4已结课/1停开）
-- 正常开课合计 24 条 —— 督导覆盖率的分母
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (1, 'CS101-2025-2026-1-01', 1, '2025-2026-1', '计算机2301班', 1, 'CS', 40, 37, '正常开课', '周一3-4节 教一101', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (2, 'CS102-2025-2026-1-01', 2, '2025-2026-1', '计算机2302班', 1, 'CS', 100, 32, '正常开课', '周二1-2节 教二203', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (3, 'CS201-2025-2026-1-01', 3, '2025-2026-1', '计算机2201班', 2, 'CS', 45, 40, '正常开课', '周三5-6节 教一305', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (4, 'CS202-2025-2026-1-01', 4, '2025-2026-1', '计算机2301班', 2, 'CS', 45, 29, '正常开课', '周四3-4节 教三102', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (5, 'CS203-2025-2026-1-01', 5, '2025-2026-1', '计算机2302班', 3, 'CS', 40, 38, '正常开课', '周五1-2节 教二108', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (6, 'CS204-2025-2026-1-01', 6, '2025-2026-1', '计算机2201班', 3, 'CS', 60, 42, '正常开课', '周一7-8节 实验楼A201', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (7, 'SE301-2025-2026-1-01', 7, '2025-2026-1', '软件工程2301班', 4, 'SE', 50, 40, '正常开课', '周二5-6节 教一210', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (8, 'SE302-2025-2026-1-01', 8, '2025-2026-1', '软件工程2302班', 4, 'SE', 60, 29, '正常开课', '周三1-2节 教三401', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (9, 'SE303-2025-2026-1-01', 9, '2025-2026-1', '软件工程2201班', 5, 'SE', 80, 53, '正常开课', '周四7-8节 实验楼B305', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (10, 'SE304-2025-2026-1-01', 10, '2025-2026-1', '软件工程2301班', 5, 'SE', 40, 36, '正常开课', '周五3-4节 教二302', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (11, 'MATH101-2025-2026-1-01', 11, '2025-2026-1', '数学2302班', 6, 'MATH', 45, 43, '正常开课', '周一3-4节 教一101', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (12, 'MATH102-2025-2026-1-01', 12, '2025-2026-1', '统计2301班', 6, 'MATH', 80, 28, '正常开课', '周二1-2节 教二203', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (13, 'MATH201-2025-2026-1-01', 13, '2025-2026-1', '数学2301班', 7, 'MATH', 40, 35, '正常开课', '周三5-6节 教一305', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (14, 'MATH202-2025-2026-1-01', 14, '2025-2026-1', '数学2302班', 7, 'MATH', 50, 48, '正常开课', '周四3-4节 教三102', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (15, 'EN101-2025-2026-1-01', 15, '2025-2026-1', '翻译2301班', 8, 'EN', 60, 60, '正常开课', '周五1-2节 教二108', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (16, 'EN102-2025-2026-1-01', 16, '2025-2026-1', '英语2301班', 8, 'EN', 45, 35, '正常开课', '周一7-8节 实验楼A201', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (17, 'EN201-2025-2026-1-01', 17, '2025-2026-1', '英语2302班', 9, 'EN', 50, 35, '正常开课', '周二5-6节 教一210', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (18, 'EN202-2025-2026-1-01', 18, '2025-2026-1', '翻译2301班', 9, 'EN', 40, 32, '正常开课', '周三1-2节 教三401', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (19, 'EN203-2025-2026-1-01', 19, '2025-2026-1', '英语2301班', 12, 'EN', 50, 37, '正常开课', '周四7-8节 实验楼B305', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (20, 'PHY101-2025-2026-1-01', 21, '2025-2026-1', '物理2302班', 10, 'PHY', 80, 31, '正常开课', '周五3-4节 教二302', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (21, 'PHY102-2025-2026-1-01', 22, '2025-2026-1', '应用物理2301班', 10, 'PHY', 50, 43, '正常开课', '周一3-4节 教一101', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (22, 'PHY201-2025-2026-1-01', 23, '2025-2026-1', '物理2301班', 11, 'PHY', 80, 69, '正常开课', '周二1-2节 教二203', '2025-08-12 10:00:00', '2025-08-12 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (23, 'EN204-2024-2025-2-01', 20, '2024-2025-2', '英语2302班', 12, 'EN', 40, 37, '正常开课', '周三5-6节 教一305', '2025-02-03 10:00:00', '2025-02-03 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (24, 'MATH202-2024-2025-2-01', 14, '2024-2025-2', '统计2301班', 7, 'MATH', 80, 51, '正常开课', '周四3-4节 教三102', '2025-02-03 10:00:00', '2025-02-03 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (25, 'CS201-2024-2025-2-01', 3, '2024-2025-2', '计算机2301班', 2, 'CS', 40, 35, '已结课', '周五1-2节 教二108', '2025-02-03 10:00:00', '2025-02-03 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (26, 'SE303-2024-2025-2-01', 9, '2024-2025-2', '软件工程2302班', 5, 'SE', 50, 35, '已结课', '周一7-8节 实验楼A201', '2025-02-03 10:00:00', '2025-02-03 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (27, 'MATH201-2024-2025-2-01', 13, '2024-2025-2', '统计2301班', 4, 'MATH', 50, 44, '已结课', '周二5-6节 教一210', '2025-02-03 10:00:00', '2025-02-03 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (28, 'EN101-2024-2025-2-01', 15, '2024-2025-2', '英语2301班', 8, 'EN', 45, 39, '已结课', '周三1-2节 教三401', '2025-02-03 10:00:00', '2025-02-03 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (29, 'CS101-2024-2025-2-01', 1, '2024-2025-2', '计算机2302班', 1, 'CS', 60, 45, '停开', '周四7-8节 实验楼B305', '2025-02-03 10:00:00', '2025-02-03 10:00:00');
INSERT INTO `offering` (`id`, `offering_code`, `course_id`, `semester`, `class_name`, `teacher_id`, `department_code`, `capacity`, `student_count`, `offering_status`, `schedule_info`, `created_at`, `updated_at`) VALUES (30, 'PHY202-2024-2025-2-01', 24, '2024-2025-2', '应用物理2301班', 11, 'PHY', 50, 40, '停开', '周五3-4节 教二302', '2025-02-03 10:00:00', '2025-02-03 10:00:00');

INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (1, 1, 1, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (2, 2, 1, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (3, 3, 2, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (4, 4, 2, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (5, 4, 1, '辅导');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (6, 5, 3, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (7, 6, 3, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (8, 7, 4, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (9, 8, 4, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (10, 8, 5, '辅导');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (11, 9, 5, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (12, 10, 5, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (13, 11, 6, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (14, 12, 6, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (15, 12, 7, '辅导');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (16, 13, 7, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (17, 14, 7, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (18, 15, 8, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (19, 16, 8, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (20, 16, 9, '辅导');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (21, 17, 9, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (22, 18, 9, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (23, 19, 12, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (24, 20, 10, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (25, 20, 11, '辅导');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (26, 21, 10, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (27, 21, 12, '合上');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (28, 22, 11, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (29, 23, 12, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (30, 24, 7, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (31, 24, 6, '辅导');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (32, 25, 2, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (33, 26, 5, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (34, 27, 4, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (35, 28, 8, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (36, 28, 9, '辅导');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (37, 29, 1, '主讲');
INSERT INTO `offering_teacher` (`id`, `offering_id`, `teacher_id`, `role_type`) VALUES (38, 30, 11, '主讲');

-- 开课授课教师 38 条 = 主讲 30 条 + 辅导/合上 8 条

INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (1, 1, '2025-09-15', '计算机2301班', '计算机科学与技术', 37, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (2, 2, '2025-09-15', '计算机2302班', '计算机科学与技术', 32, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (3, 3, '2025-09-15', '计算机2201班', '计算机科学与技术', 24, '主修专业人数');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (4, 3, '2025-09-15', '计算机2201班', '软件工程', 16, '含跨专业选修16人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (5, 4, '2025-09-15', '计算机2301班', '计算机科学与技术', 29, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (6, 5, '2025-09-15', '计算机2302班', '计算机科学与技术', 38, '含重修5人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (7, 6, '2025-09-15', '计算机2201班', '计算机科学与技术', 25, '主修专业人数');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (8, 6, '2025-09-15', '计算机2201班', '软件工程', 17, '含跨专业选修17人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (9, 7, '2025-09-15', '软件工程2301班', '软件工程', 40, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (10, 8, '2025-09-15', '软件工程2302班', '软件工程', 29, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (11, 9, '2025-09-15', '软件工程2201班', '软件工程', 31, '主修专业人数');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (12, 9, '2025-09-15', '软件工程2201班', '数据科学与大数据技术', 22, '含跨专业选修22人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (13, 10, '2025-09-15', '软件工程2301班', '软件工程', 36, '含重修1人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (14, 11, '2025-09-15', '数学2302班', '数学与应用数学', 43, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (15, 12, '2025-09-15', '统计2301班', '数学与应用数学', 16, '主修专业人数');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (16, 12, '2025-09-15', '统计2301班', '统计学', 12, '含跨专业选修12人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (17, 13, '2025-09-15', '数学2301班', '数学与应用数学', 35, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (18, 14, '2025-09-15', '数学2302班', '数学与应用数学', 48, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (19, 15, '2025-09-15', '翻译2301班', '英语', 36, '主修专业人数');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (20, 15, '2025-09-15', '翻译2301班', '翻译', 24, '含跨专业选修24人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (21, 16, '2025-09-15', '英语2301班', '英语', 35, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (22, 17, '2025-09-15', '英语2302班', '英语', 35, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (23, 18, '2025-09-15', '翻译2301班', '英语', 19, '主修专业人数');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (24, 18, '2025-09-15', '翻译2301班', '翻译', 13, '含跨专业选修13人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (25, 19, '2025-09-15', '英语2301班', '英语', 37, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (26, 20, '2025-09-15', '物理2302班', '物理学', 31, '含重修5人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (27, 21, '2025-09-15', '应用物理2301班', '物理学', 25, '主修专业人数');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (28, 21, '2025-09-15', '应用物理2301班', '应用物理学', 18, '含跨专业选修18人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (29, 22, '2025-09-15', '物理2301班', '物理学', 69, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (30, 23, '2025-03-09', '英语2302班', '英语', 37, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (31, 24, '2025-03-09', '统计2301班', '数学与应用数学', 30, '主修专业人数');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (32, 24, '2025-03-09', '统计2301班', '统计学', 21, '含跨专业选修21人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (33, 25, '2025-03-09', '计算机2301班', '计算机科学与技术', 35, '含重修4人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (34, 26, '2025-03-09', '软件工程2302班', '软件工程', 35, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (35, 27, '2025-03-09', '统计2301班', '数学与应用数学', 26, '主修专业人数');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (36, 27, '2025-03-09', '统计2301班', '统计学', 18, '含跨专业选修18人');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (37, 28, '2025-03-09', '英语2301班', '英语', 39, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (38, 29, '2025-03-09', '计算机2302班', '计算机科学与技术', 45, '学期初选课人数快照');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (39, 30, '2025-03-09', '应用物理2301班', '物理学', 24, '主修专业人数');
INSERT INTO `student_count` (`id`, `offering_id`, `stat_date`, `class_name`, `major`, `student_num`, `remark`) VALUES (40, 30, '2025-03-09', '应用物理2301班', '应用物理学', 16, '含跨专业选修16人');

-- 学生人次 40 条；按 offering 汇总 student_num 与 offering.student_count 完全一致

-- ============================ 四、教学资源（US-03） ============================

INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (1, '程序设计基础-第1章 课件.pptx', '课件', '/files/resources/TE001/TE001-001.pptx', 2619030, 1, 1, '程序设计基础（计算机2301班）配套课件，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (2, '面向对象程序设计-第2章 教案.docx', '教案', '/files/resources/TE001/TE001-002.docx', 16194846, 2, 1, '面向对象程序设计（计算机2302班）配套教案，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (3, '数据结构-第3章 视频.mp4', '视频', '/files/resources/TE002/TE002-003.mp4', 65586531, 25, 2, '数据结构（计算机2301班）配套视频，适用于2024-2025-2学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (4, '数据结构-第4章 习题.pdf', '习题', '/files/resources/TE002/TE002-004.pdf', 46272951, 3, 2, '数据结构（计算机2201班）配套习题，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (5, '计算机网络-第5章 参考文献.pdf', '参考文献', '/files/resources/TE003/TE003-005.pdf', 52947738, 5, 3, '计算机网络（计算机2302班）配套参考文献，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (6, 'Python程序设计-第6章 课件.pptx', '课件', '/files/resources/TE003/TE003-006.pptx', 281384, 6, 3, 'Python程序设计（计算机2201班）配套课件，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (7, '软件工程导论-第7章 教案.docx', '教案', '/files/resources/TE004/TE004-007.docx', 3428288, 7, 4, '软件工程导论（软件工程2301班）配套教案，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (8, '软件项目管理-第8章 视频.mp4', '视频', '/files/resources/TE004/TE004-008.mp4', 76736762, 8, 4, '软件项目管理（软件工程2302班）配套视频，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (9, '软件测试技术-第1章 习题.pdf', '习题', '/files/resources/TE005/TE005-009.pdf', 30878378, 26, 5, '软件测试技术（软件工程2302班）配套习题，适用于2024-2025-2学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (10, '软件测试技术-第2章 参考文献.pdf', '参考文献', '/files/resources/TE005/TE005-010.pdf', 55588594, 9, 5, '软件测试技术（软件工程2201班）配套参考文献，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (11, '高等数学A-第3章 课件.pptx', '课件', '/files/resources/TE006/TE006-011.pptx', 50469903, 11, 6, '高等数学A（数学2302班）配套课件，适用于2025-2026-1学期', '待审核', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (12, '概率论与数理统计-第4章 教案.docx', '教案', '/files/resources/TE006/TE006-012.docx', 5619862, 12, 6, '概率论与数理统计（统计2301班）配套教案，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (13, '线性代数-第5章 视频.mp4', '视频', '/files/resources/TE007/TE007-013.mp4', 30688688, 13, 7, '线性代数（数学2301班）配套视频，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (14, '离散数学-第6章 习题.pdf', '习题', '/files/resources/TE007/TE007-014.pdf', 67747834, 14, 7, '离散数学（数学2302班）配套习题，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (15, '大学英语（一）-第7章 参考文献.pdf', '参考文献', '/files/resources/TE008/TE008-015.pdf', 13604798, 28, 8, '大学英语（一）（英语2301班）配套参考文献，适用于2024-2025-2学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (16, '大学英语（一）-第8章 课件.pptx', '课件', '/files/resources/TE008/TE008-016.pptx', 33411971, 15, 8, '大学英语（一）（翻译2301班）配套课件，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (17, '学术英语写作-第1章 教案.docx', '教案', '/files/resources/TE009/TE009-017.docx', 56533942, 17, 9, '学术英语写作（英语2302班）配套教案，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (18, '英汉翻译基础-第2章 视频.mp4', '视频', '/files/resources/TE009/TE009-018.mp4', 59517510, 18, 9, '英汉翻译基础（翻译2301班）配套视频，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (19, '大学物理-第3章 习题.pdf', '习题', '/files/resources/TE010/TE010-019.pdf', 17337463, 20, 10, '大学物理（物理2302班）配套习题，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (20, '电磁学-第4章 参考文献.pdf', '参考文献', '/files/resources/TE011/TE011-020.pdf', 37342012, 30, 11, '电磁学（应用物理2301班）配套参考文献，适用于2024-2025-2学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (21, '英语国家社会与文化-第5章 课件.pptx', '课件', '/files/resources/TE012/TE012-021.pptx', 52897882, 19, 12, '英语国家社会与文化（英语2301班）配套课件，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (22, '程序设计基础-第6章 教案.docx', '教案', '/files/resources/TE001/TE001-022.docx', 3363140, 1, 1, '程序设计基础（计算机2301班）配套教案，适用于2025-2026-1学期', '待审核', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (23, '计算机组成原理-第7章 视频.mp4', '视频', '/files/resources/TE002/TE002-023.mp4', 27892770, 4, 2, '计算机组成原理（计算机2301班）配套视频，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (24, '线性代数-第8章 习题.pdf', '习题', '/files/resources/TE004/TE004-024.pdf', 21784073, 27, 4, '线性代数（统计2301班）配套习题，适用于2024-2025-2学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');
INSERT INTO `teaching_resource` (`id`, `resource_name`, `resource_type`, `file_url`, `file_size`, `offering_id`, `teacher_id`, `description`, `status`, `created_at`, `updated_at`) VALUES (25, '线性代数-第1章 参考文献.pdf', '参考文献', '/files/resources/TE007/TE007-025.pdf', 61656097, 13, 7, '线性代数（数学2301班）配套参考文献，适用于2025-2026-1学期', '有效', '2025-08-25 14:00:00', '2025-08-25 14:00:00');

-- 教学资源 25 条，覆盖 5 种资源类型；12 位教师均有资源，可验证教师角色数据范围

INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (1, 1, 1, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (2, 2, 2, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (3, 3, 3, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (4, 3, 4, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (5, 5, 5, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (6, 6, 6, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (7, 7, 7, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (8, 8, 8, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (9, 9, 9, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (10, 9, 10, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (11, 11, 11, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (12, 12, 12, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (13, 13, 13, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (14, 14, 14, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (15, 15, 15, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (16, 15, 16, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (17, 17, 17, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (18, 18, 18, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (19, 21, 19, 1);
INSERT INTO `course_resource` (`id`, `course_id`, `resource_id`, `associated`) VALUES (20, 24, 20, 1);

-- 课程资源挂接 20 条（associated=1 有效挂接；DELETE 接口解除挂接时置 0 软解绑）

-- ============================ 五、教学评价（史诗三，只读展示） ============================

INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (1, 1, '学生评教', '2025-2026-1', 69.1, '匿名学生002', '课程内容充实，但进度略快，希望增加习题讲解。', '2025-12-17 11:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (2, 2, '学生评教', '2025-2026-1', 78.8, '匿名学生003', '希望增加课后答疑时间。', '2025-12-27 12:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (3, 3, '学生评教', '2025-2026-1', 81.6, '匿名学生004', '课堂节奏偏快，建议补充基础概念回顾。', '2025-11-04 13:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (4, 4, '学生评教', '2025-2026-1', 75.2, '匿名学生005', '老师讲解条理清晰，重点突出，课堂互动充分。', '2025-10-31 14:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (5, 5, '学生评教', '2025-2026-1', 76.2, '匿名学生006', '希望增加课后答疑时间。', '2025-12-15 15:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (6, 6, '学生评教', '2025-2026-1', 71.5, '匿名学生007', '老师认真负责，答疑耐心。', '2025-12-12 16:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (7, 7, '学生评教', '2025-2026-1', 86.0, '匿名学生008', '课程难度适中，考核方式公平。', '2025-12-22 17:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (8, 8, '学生评教', '2025-2026-1', 89.3, '匿名学生009', '课堂节奏偏快，建议补充基础概念回顾。', '2025-11-11 10:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (9, 9, '学生评教', '2025-2026-1', 79.4, '匿名学生010', '课堂节奏偏快，建议补充基础概念回顾。', '2025-11-09 11:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (10, 10, '学生评教', '2025-2026-1', 83.6, '匿名学生011', '作业量适中，批改及时，反馈具体。', '2025-11-17 12:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (11, 11, '学生评教', '2025-2026-1', 91.7, '匿名学生012', '老师认真负责，答疑耐心。', '2025-11-16 13:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (12, 12, '学生评教', '2025-2026-1', 77.1, '匿名学生013', '课堂节奏偏快，建议补充基础概念回顾。', '2025-11-02 14:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (13, 13, '学生评教', '2025-2026-1', 84.6, '匿名学生014', '实验环节安排合理，对动手能力提升帮助很大。', '2025-12-07 15:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (14, 14, '学生评教', '2025-2026-1', 70.3, '匿名学生015', '老师讲解条理清晰，重点突出，课堂互动充分。', '2025-12-11 16:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (15, 15, '学生评教', '2025-2026-1', 77.4, '匿名学生016', '课程难度适中，考核方式公平。', '2025-12-01 17:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (16, 16, '学生评教', '2025-2026-1', 81.7, '匿名学生017', '课程内容充实，但进度略快，希望增加习题讲解。', '2025-11-07 10:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (17, 17, '学生评教', '2025-2026-1', 75.0, '匿名学生018', '课程难度适中，考核方式公平。', '2025-12-05 11:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (18, 18, '学生评教', '2025-2026-1', 87.0, '匿名学生019', '希望增加课后答疑时间。', '2025-11-15 12:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (19, 19, '学生评教', '2025-2026-1', 79.5, '匿名学生020', '课程难度适中，考核方式公平。', '2025-12-23 13:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (20, 20, '学生评教', '2025-2026-1', 85.7, '匿名学生021', '老师认真负责，答疑耐心。', '2025-11-09 14:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (21, 21, '学生评教', '2025-2026-1', 70.6, '匿名学生022', '希望增加课后答疑时间。', '2025-11-01 15:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (22, 22, '学生评教', '2025-2026-1', 94.7, '匿名学生023', '实验环节安排合理，对动手能力提升帮助很大。', '2025-12-05 16:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (23, 23, '学生评教', '2024-2025-2', 73.0, '匿名学生024', '老师讲解条理清晰，重点突出，课堂互动充分。', '2025-05-16 17:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (24, 24, '学生评教', '2024-2025-2', 87.0, '匿名学生025', '作业量适中，批改及时，反馈具体。', '2025-05-06 10:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (25, 25, '学生评教', '2024-2025-2', 79.1, '匿名学生026', '作业量适中，批改及时，反馈具体。', '2025-05-03 11:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (26, 26, '学生评教', '2024-2025-2', 91.9, '匿名学生027', '案例贴近工程实际，收获很大。', '2025-05-28 12:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (27, 27, '学生评教', '2024-2025-2', 90.8, '匿名学生028', '课程难度适中，考核方式公平。', '2025-06-11 13:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (28, 28, '学生评教', '2024-2025-2', 71.7, '匿名学生029', '课程难度适中，考核方式公平。', '2025-06-16 14:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (29, 2, '学生评教', '2025-2026-1', 74.9, '匿名学生030', '希望增加课后答疑时间。', '2025-11-01 15:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (30, 4, '学生评教', '2025-2026-1', 82.6, '匿名学生031', '老师认真负责，答疑耐心。', '2025-12-12 16:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (31, 5, '学生评教', '2025-2026-1', 87.1, '匿名学生032', '案例贴近工程实际，收获很大。', '2025-11-03 17:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (32, 6, '学生评教', '2025-2026-1', 89.7, '匿名学生033', '实验环节安排合理，对动手能力提升帮助很大。', '2025-12-24 10:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (33, 7, '学生评教', '2025-2026-1', 88.1, '匿名学生034', '课堂节奏偏快，建议补充基础概念回顾。', '2025-11-27 11:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (34, 9, '学生评教', '2025-2026-1', 69.0, '匿名学生035', '作业量适中，批改及时，反馈具体。', '2025-11-16 12:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (35, 11, '学生评教', '2025-2026-1', 83.1, '匿名学生036', '课程难度适中，考核方式公平。', '2025-12-06 13:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (36, 13, '学生评教', '2025-2026-1', 73.5, '匿名学生037', '课程难度适中，考核方式公平。', '2025-12-08 14:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (37, 14, '学生评教', '2025-2026-1', 92.2, '匿名学生038', '案例贴近工程实际，收获很大。', '2025-12-05 15:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (38, 15, '学生评教', '2025-2026-1', 84.0, '匿名学生039', '案例贴近工程实际，收获很大。', '2025-12-14 16:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (39, 16, '学生评教', '2025-2026-1', 81.1, '匿名学生040', '作业量适中，批改及时，反馈具体。', '2025-12-30 17:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (40, 17, '学生评教', '2025-2026-1', 81.6, '匿名学生041', '课件质量高，复习很方便。', '2025-11-27 10:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (41, 18, '学生评教', '2025-2026-1', 95.5, '匿名学生042', '老师讲解条理清晰，重点突出，课堂互动充分。', '2025-12-22 11:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (42, 19, '学生评教', '2025-2026-1', 87.6, '匿名学生043', '课堂节奏偏快，建议补充基础概念回顾。', '2025-11-30 12:20:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (43, 1, '督导评教', '2025-2026-1', 82.1, '教学督导', '课堂互动充分，注意后排学生听课状态。', '2025-11-25 15:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (44, 3, '督导评教', '2025-2026-1', 85.9, '教学督导', '课堂互动充分，注意后排学生听课状态。', '2025-12-06 15:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (45, 7, '督导评教', '2025-2026-1', 85.2, '教学督导', '课堂互动充分，注意后排学生听课状态。', '2025-11-22 15:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (46, 11, '督导评教', '2025-2026-1', 88.3, '教学督导', '课堂互动充分，注意后排学生听课状态。', '2025-11-20 15:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (47, 13, '督导评教', '2025-2026-1', 88.6, '教学督导', '课堂互动充分，注意后排学生听课状态。', '2025-11-12 15:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (48, 19, '督导评教', '2025-2026-1', 83.2, '教学督导', '课堂组织规范，教学目标明确，学生参与度高。', '2025-12-19 15:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (49, 21, '督导评教', '2025-2026-1', 83.1, '教学督导', '课堂组织规范，教学目标明确，学生参与度高。', '2025-11-27 15:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (50, 23, '督导评教', '2024-2025-2', 90.5, '教学督导', '课堂组织规范，教学目标明确，学生参与度高。', '2025-05-14 15:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (51, 24, '督导评教', '2024-2025-2', 86.7, '教学督导', '课堂组织规范，教学目标明确，学生参与度高。', '2025-06-13 15:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (52, 4, '同行评议', '2025-2026-1', 89.3, '李娜同行', '课堂管理有序，教学语言清晰。', '2025-12-04 16:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (53, 6, '同行评议', '2025-2026-1', 79.2, '王强同行', '建议在课程中增加更多工程案例。', '2025-12-21 16:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (54, 8, '同行评议', '2025-2026-1', 94.6, '刘洋同行', '课堂管理有序，教学语言清晰。', '2025-11-20 16:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (55, 10, '同行评议', '2025-2026-1', 79.9, '赵敏同行', '建议在课程中增加更多工程案例。', '2025-12-21 16:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (56, 12, '同行评议', '2025-2026-1', 87.2, '周婷同行', '课堂管理有序，教学语言清晰。', '2025-12-18 16:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (57, 14, '同行评议', '2025-2026-1', 83.1, '吴磊同行', '教学设计合理，重难点处理得当。', '2025-12-09 16:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (58, 16, '同行评议', '2025-2026-1', 88.2, '孙杰同行', '教学设计合理，重难点处理得当。', '2025-12-14 16:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (59, 18, '同行评议', '2025-2026-1', 87.1, '郑爽同行', '课堂管理有序，教学语言清晰。', '2025-12-20 16:00:00');
INSERT INTO `evaluation` (`id`, `offering_id`, `evaluation_type`, `semester`, `score`, `evaluator`, `content`, `evaluated_at`) VALUES (60, 22, '同行评议', '2025-2026-1', 94.7, '罗丹同行', '课堂管理有序，教学语言清晰。', '2025-12-04 16:00:00');

-- 评价 60 条（学生评教 45 / 督导评教 9 / 同行评议 9），分数区间 69.0—95.5
-- 学生评教覆盖全部 28 个非停开教学班（共 42 条）

-- ============================ 六、督导与听评课（US-04） ============================

INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (1, 2, 10, '2025-2026-1', '2025-11-27', '2025-11-30', '已完成', 96.6, '课堂组织规范，教学目标明确，学生参与度高。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (2, 5, 11, '2025-2026-1', '2025-11-01', '2025-11-03', '已完成', 89.6, '例题讲解透彻，建议增加随堂练习。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (3, 7, 12, '2025-2026-1', '2025-10-25', '2025-10-26', '已完成', 82.0, '实验指导到位，学生动手积极性高。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (4, 9, 10, '2025-2026-1', '2025-11-02', '2025-11-07', '已完成', 82.4, '教态自然，注意控制语速与板书节奏。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (5, 11, 11, '2025-2026-1', '2025-10-17', '2025-10-18', '已完成', 83.1, '项目案例贴近工程实际，课堂气氛活跃。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (6, 13, 12, '2025-2026-1', '2025-11-07', '2025-11-09', '已完成', 92.2, '教学内容充实，建议适当精简部分内容。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (7, 17, 10, '2025-2026-1', '2025-10-23', '2025-10-24', '已完成', 95.6, '讲解清晰，时间分配合理。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (8, 19, 11, '2025-2026-1', '2025-11-22', '2025-11-26', '已完成', 83.0, '板书规范，重难点突出。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (9, 20, 12, '2025-2026-1', '2025-10-24', '2025-10-26', '已完成', 90.6, '课堂组织规范，教学目标明确，学生参与度高。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (10, 14, 10, '2025-2026-1', '2025-12-01', '2025-12-05', '已完成', 92.1, '例题讲解透彻，建议增加随堂练习。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (11, 16, 11, '2025-2026-1', '2025-10-16', '2025-10-17', '已完成', 88.0, '实验指导到位，学生动手积极性高。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (12, 18, 12, '2025-2026-1', '2025-10-15', '2025-10-20', '已完成', 82.8, '教态自然，注意控制语速与板书节奏。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (13, 21, 10, '2025-2026-1', '2025-11-06', '2025-11-10', '已完成', 85.9, '项目案例贴近工程实际，课堂气氛活跃。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (14, 4, 11, '2025-2026-1', '2025-11-01', '2025-11-06', '已完成', 88.5, '教学内容充实，建议适当精简部分内容。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (15, 6, 12, '2025-2026-1', '2025-11-16', '2025-11-21', '已完成', 83.0, '讲解清晰，时间分配合理。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (16, 23, 10, '2024-2025-2', '2025-04-13', '2025-04-15', '已完成', 94.4, '板书规范，重难点突出。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (17, 1, 10, '2025-2026-1', '2025-12-15', NULL, '已安排', NULL, '已发出听课安排，等待实施。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (18, 12, 10, '2025-2026-1', '2025-12-15', NULL, '已安排', NULL, '已发出听课安排，等待实施。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (19, 3, 11, '2025-2026-1', NULL, NULL, '待安排', NULL, '尚未确定听课时间。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');
INSERT INTO `supervision` (`id`, `offering_id`, `supervisor_id`, `semester`, `plan_date`, `finish_date`, `status`, `score`, `comment`, `created_at`, `updated_at`) VALUES (20, 10, 11, '2025-2026-1', NULL, NULL, '待安排', NULL, '尚未确定听课时间。', '2025-08-28 09:00:00', '2025-08-28 09:00:00');

-- 听评课 20 条：已完成 16 条 / 已安排 2 条 / 待安排 2 条
-- 督导覆盖率 = 16（status='已完成' 的 offering 去重数）÷ 24（offering_status='正常开课'）= 66.7%

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
--  样例数据导入完成。校验语句与预期结果见 database/README.md。
-- =============================================================================
