-- ============================================================================
-- 测试用样例数据（H2 内存库）
-- 说明：本文件为教学演示用虚构样例数据，不代表任何真实教学数据。
-- 数据量经过刻意设计，用于验证权限拦截、数据范围过滤与督导覆盖率口径：
--   · 教学班 7 个，其中「正常开课」5 个（CS 4 个 + MATH 1 个）
--   · CS 的 4 个正常开课教学班中 3 个有「已完成」听课记录 → 覆盖率 75.0%
--   · MATH 的 1 个正常开课教学班无已完成听课记录 → 覆盖率 0.0%
--   · 全部正常开课教学班覆盖率 = 3 / 5 = 60.0%
-- 演示账号口令统一为 123456（BCrypt 哈希）。
-- ============================================================================

DELETE FROM supervision;
DELETE FROM evaluation;
DELETE FROM course_resource;
DELETE FROM teaching_resource;
DELETE FROM student_count;
DELETE FROM offering_teacher;
DELETE FROM offering;
DELETE FROM course_intro;
DELETE FROM course;
DELETE FROM teacher;
DELETE FROM sys_audit_log;
DELETE FROM sys_role_permission;
DELETE FROM sys_config;
DELETE FROM sys_permission;
DELETE FROM sys_role;
DELETE FROM sys_user;

-- ---------------- 角色 ----------------
INSERT INTO sys_role (id, role_code, role_name, description, data_scope, sort_no) VALUES
 (1, 'ADMIN',        '系统管理员',   '全量数据与全部功能',                              'ALL',        1),
 (2, 'DIRECTOR',     '教研室主任',   '本教研室课程、开课、教师与统计',                   'DEPARTMENT', 2),
 (3, 'TEACHER',      '教师',         '仅本人课程、教学班与资源',                         'SELF',       3),
 (4, 'SUPERVISOR',   '教学督导',     '全校开课与督导数据，可安排听评课',                 'ALL',        4),
 (5, 'COURSE_ADMIN', '课程管理员',   '课程库与开课的只读维护',                           'ALL',        5);

-- ---------------- 权限码字典 ----------------
INSERT INTO sys_permission (perm_code, perm_name, module) VALUES
 ('user:read','查看用户','user'), ('user:write','维护用户','user'),
 ('role:read','查看角色与权限','role'), ('role:write','维护角色权限','role'),
 ('audit:read','查看操作日志','audit'),
 ('course:read','查看课程信息','course'), ('course:write','维护课程信息','course'),
 ('offering:read','查看开课情况','offering'), ('offering:write','维护开课情况','offering'),
 ('teacher:read','查看教师信息','teacher'), ('teacher:write','维护教师信息','teacher'),
 ('resource:read','查看教学资源','resource'), ('resource:write','维护教学资源','resource'),
 ('resource:prep','备课查看','resource'),
 ('evaluation:read','查看教学评价','evaluation'),
 ('supervision:read','查看听评课记录','supervision'), ('supervision:write','维护听评课安排','supervision'),
 ('coverage:read','查看督导覆盖率','coverage'),
 ('dashboard:read','查看工作台','dashboard'), ('dashboard:admin','管理员工作台','dashboard'),
 ('dashboard:director','教研室主任工作台','dashboard'), ('dashboard:teacher','教师工作台','dashboard'),
 ('dashboard:supervisor','教学督导工作台','dashboard');

-- ---------------- 系统配置 ----------------
INSERT INTO sys_config (config_key, config_value, remark) VALUES
 ('system.name', '爱教学-教学质量全链路数字化管理平台', '系统名称，登录页与页头展示'),
 ('current.semester', '2025-2026-1', '当前学期，工作台与筛选默认值'),
 ('cross_department', 'false', '教研室主任是否可跨院系查询：false 仅本院系，true 全校');

-- ---------------- 教师档案 ----------------
INSERT INTO teacher (id, teacher_no, name, gender, title, department_code, department_name, email, phone, created_at, updated_at) VALUES
 (1, 'T2001', '张伟', '男', '教授',   'CS',   '计算机学院', 'zhangwei@example.edu',  '13800000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, 'T2002', '李娜', '女', '副教授', 'CS',   '计算机学院', 'lina@example.edu',      '13800000002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, 'T2003', '王强', '男', '讲师',   'MATH', '数学学院',   'wangqiang@example.edu', '13800000003', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, 'T2004', '赵敏', '女', '讲师',   'MATH', '数学学院',   'zhaomin@example.edu',   '13800000004', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (5, 'T2005', '陈静', '女', '教授',   'FL',   '外国语学院', 'chenjing@example.edu',  '13800000005', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------- 用户账号（口令均为 123456） ----------------
INSERT INTO sys_user (id, username, password, real_name, role_code, teacher_id, department_code, enabled, created_at, updated_at) VALUES
 (1, 'admin',      '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'ADMIN',        NULL, NULL,   1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, 'director',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '教研室主任', 'DIRECTOR',     NULL, 'CS',   1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, 'teacher1',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张伟',       'TEACHER',      1,    'CS',   1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, 'teacher2',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李娜',       'TEACHER',      2,    'CS',   1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (5, 'supervisor', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '教学督导',   'SUPERVISOR',   NULL, NULL,   1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (6, 'courseadmin','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '课程管理员', 'COURSE_ADMIN', NULL, NULL,   1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------- 课程库 ----------------
INSERT INTO course (id, course_code, course_name, course_type, credit, total_hours, department_code, department_name, owner_teacher_id, intro, status, created_at, updated_at) VALUES
 (1, 'CS101', '程序设计基础',   '必修', 4.0, 64, 'CS',   '计算机学院', 1, '面向一年级学生的程序设计入门课程，讲授结构化编程与基本算法。', '已发布', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, 'CS102', '数据结构',       '必修', 4.0, 64, 'CS',   '计算机学院', 1, '讲授线性表、树、图等基本数据结构及其经典算法实现。',           '已发布', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, 'CS103', '软件工程',       '必修', 3.0, 48, 'CS',   '计算机学院', 2, '讲授软件过程模型、需求工程、设计与测试等工程化方法。',         '已发布', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, 'MA201', '高等数学',       '通识', 5.0, 80, 'MATH', '数学学院',   3, '面向工科专业的数学基础课程，涵盖微积分与级数。',               '已发布', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (5, 'MA202', '线性代数',       '通识', 3.0, 48, 'MATH', '数学学院',   4, '讲授矩阵、向量空间与线性变换及其工程应用。',                   '已发布', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (6, 'FL301', '大学英语',       '通识', 3.0, 48, 'FL',   '外国语学院', 5, '培养学术英语读写与听说能力。',                                 '已发布', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (7, 'CS104', '人工智能导论',   '选修', 2.0, 32, 'CS',   '计算机学院', 2, '介绍人工智能基本概念、搜索、机器学习与神经网络入门。',         '草稿',   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------- 课程简介详情 ----------------
INSERT INTO course_intro (course_id, content, objective, assessment_method, textbook, updated_at) VALUES
 (1, '本课程是计算机类专业的第一门专业基础课，以 C 语言为载体，讲解程序设计的基本方法与调试技能，强调动手实践。',
     '掌握顺序、分支、循环三种基本结构；能独立完成 500 行以内的程序设计与调试。',
     '平时作业 30% + 上机实验 30% + 期末机考 40%', '《C 程序设计（第五版）》谭浩强', CURRENT_TIMESTAMP),
 (3, '本课程以 Scrum 敏捷方法为主线，结合真实项目案例讲解软件过程管理、需求分析与质量保证。',
     '能独立完成用户故事地图、Sprint 计划与项目复盘；理解 DRI 责任制。',
     '小组项目 50% + 个人作业 20% + 期末笔试 30%', '《软件工程导论（第 6 版）》张海藩', CURRENT_TIMESTAMP);

-- ---------------- 开课记录（教学班） ----------------
INSERT INTO offering (id, offering_code, course_id, semester, class_name, teacher_id, department_code, capacity, student_count, offering_status, schedule_info, created_at, updated_at) VALUES
 (1, 'OFF-2025-1-CS101-01', 1, '2025-2026-1', '软件2201班', 1, 'CS',   60, 58, '正常开课', '周一 1-2 节 / 信息楼 A101', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, 'OFF-2025-1-CS102-01', 2, '2025-2026-1', '软件2202班', 1, 'CS',   60, 55, '正常开课', '周二 3-4 节 / 信息楼 A102', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, 'OFF-2025-1-CS103-01', 3, '2025-2026-1', '软件2201班', 2, 'CS',   50, 48, '正常开课', '周三 5-6 节 / 信息楼 B201', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, 'OFF-2025-1-CS104-01', 7, '2025-2026-1', '软件2202班', 2, 'CS',   45, 0,  '停开',     '周四 7-8 节 / 信息楼 B202', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (5, 'OFF-2025-1-MA201-01', 4, '2025-2026-1', '计算机2201班', 3, 'MATH', 80, 76, '正常开课', '周一 3-4 节 / 理科楼 C301', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (6, 'OFF-2024-2-CS101-01', 1, '2024-2025-2', '软件2101班', 1, 'CS',   60, 60, '已结课',   '周二 1-2 节 / 信息楼 A101', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (7, 'OFF-2024-2-MA202-01', 5, '2024-2025-2', '计算机2101班', 4, 'MATH', 70, 68, '正常开课', '周三 3-4 节 / 理科楼 C302', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------- 教学班—教师关联 ----------------
INSERT INTO offering_teacher (offering_id, teacher_id, role_type) VALUES
 (1, 1, '主讲'), (2, 1, '主讲'), (3, 2, '主讲'), (3, 1, '合上'),
 (5, 3, '主讲'), (6, 1, '主讲'), (7, 4, '主讲');

-- ---------------- 学生人次明细 ----------------
INSERT INTO student_count (id, offering_id, stat_date, class_name, major, student_num, remark) VALUES
 (1, 1, DATE '2025-09-15', '软件2201班', '软件工程', 30, '样例数据：按班级分项统计'),
 (2, 1, DATE '2025-09-15', '软件2202班', '软件工程', 28, '样例数据：按班级分项统计'),
 (3, 2, DATE '2025-09-15', '软件2202班', '软件工程', 55, '样例数据'),
 (4, 3, DATE '2025-09-15', '软件2201班', '软件工程', 48, '样例数据'),
 (5, 5, DATE '2025-09-15', '计算机2201班', '计算机科学', 76, '样例数据');

-- ---------------- 教学资源 ----------------
INSERT INTO teaching_resource (id, resource_name, resource_type, file_url, file_size, offering_id, teacher_id, description, status, created_at, updated_at) VALUES
 (1, '程序设计基础-第1章课件', '课件',   '/files/cs101-ch01.pdf', 2048000, 1, 1, '变量与数据类型，含课堂示例代码', '有效', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, '程序设计基础-实验指导书', '教案',   '/files/cs101-lab.docx', 512000,  1, 1, '8 次上机实验的任务与评分标准',   '有效', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, '数据结构-树与二叉树讲解', '视频',  '/files/cs102-tree.mp4', 104857600, 2, 1, '二叉树遍历与线索化录屏',        '有效', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, '软件工程-Scrum实践案例', '参考文献', '/files/cs103-scrum.pdf', 1024000, 3, 2, '蜂鸟项目 Sprint 实践案例',       '有效', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (5, '高等数学-极限习题集',    '习题',   '/files/ma201-ex.pdf', 307200, 5, 3, '极限与连续配套练习',            '有效', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (6, '数据结构-旧版课件',      '课件',   '/files/cs102-old.pdf', 1024000, 2, 1, '已过期版本，保留备查',          '失效', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------- 课程—资源挂接 ----------------
INSERT INTO course_resource (id, course_id, resource_id, associated) VALUES
 (1, 1, 1, 1), (2, 1, 2, 1), (3, 2, 3, 1), (4, 3, 4, 1), (5, 4, 5, 1), (6, 2, 6, 0);

-- ---------------- 教学评价 ----------------
INSERT INTO evaluation (id, offering_id, evaluation_type, semester, score, evaluator, content, evaluated_at, created_at) VALUES
 (1, 1, '学生评教', '2025-2026-1', 92.5, '学生(匿名)', '讲解清晰，示例丰富，作业量适中。',        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, 1, '学生评教', '2025-2026-1', 88.0, '学生(匿名)', '实验环节收获较大，希望多讲调试技巧。',    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, 2, '学生评教', '2025-2026-1', 95.0, '学生(匿名)', '课程难度合适，老师答疑及时。',            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, 2, '学生评教', '2025-2026-1', 78.5, '学生(匿名)', '进度偏快，树的部分需要再讲一遍。',        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (5, 3, '学生评教', '2025-2026-1', 90.0, '学生(匿名)', 'Sprint 实践很有帮助，团队协作能力提升。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (6, 3, '督导评教', '2025-2026-1', 89.0, '教学督导',   '课堂组织良好，建议增加随堂练习。',        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (7, 1, '同行评议', '2025-2026-1', 91.0, '同行教师',   '教学设计完整，板书与课件配合良好。',      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------- 听评课记录（覆盖率口径的数据基础） ----------------
-- 已完成听课覆盖教学班 1、3（CS）与 6（CS，2024-2025-2）；教学班 2、5、7 均无已完成记录
-- → CS 覆盖率 3/4 = 75.0%，MATH 覆盖率 0/1 = 0.0%，总体 3/5 = 60.0%
INSERT INTO supervision (id, offering_id, supervisor_id, semester, plan_date, finish_date, status, score, comment, created_at, updated_at) VALUES
 (1, 1, 5, '2025-2026-1', DATE '2025-10-08', DATE '2025-10-08', '已完成', 90.0, '课堂节奏好，师生互动充分。',           CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, 2, 5, '2025-2026-1', DATE '2025-10-09', NULL,              '已安排', NULL,  '已排入第 6 周听课计划，尚未听课。',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, 3, 5, '2025-2026-1', DATE '2025-10-10', DATE '2025-10-10', '已完成', 92.0, '项目驱动教学效果好，Sprint 组织规范。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (4, 5, 5, '2025-2026-1', DATE '2025-10-15', NULL,              '已安排', NULL,  '计划第 7 周听课',                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (5, 6, 5, '2024-2025-2', DATE '2025-04-10', DATE '2025-04-10', '已完成', 88.0, '结课听课记录，教学效果良好。',         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------- 操作日志样例 ----------------
INSERT INTO sys_audit_log (operator, role_code, action, target_type, target_id, detail, ip, created_at) VALUES
 ('admin', 'ADMIN', '新增', 'USER', '6', '初始化课程管理员账号', '127.0.0.1', CURRENT_TIMESTAMP);
