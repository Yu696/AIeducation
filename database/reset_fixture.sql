-- ============================================================================
-- 幂等重置脚本（用于清理历史残留数据，恢复样例数据基线）
-- 适用：H2 测试库与 MySQL 开发库均可执行（仅使用标准 SQL）
-- 基线目标：教师 5 / 课程 7 / 开课 7 / 评价 7 / 听评课 5 / 资源 6 / 人次 5
-- 执行方式（MySQL）：mysql -uroot -p123456 aijiaoxue < reset_fixture.sql
-- ============================================================================

-- 先清理测试期新增的引用数据（按依赖顺序）
-- 注意：使用「保留基线 ID 白名单」而非 id > N，避免自增序列不回退导致残留行混入基线区间
DELETE FROM supervision WHERE id NOT IN (1,2,3,4,5);
DELETE FROM evaluation WHERE id NOT IN (1,2,3,4,5,6,7);
DELETE FROM course_resource WHERE id NOT IN (1,2,3,4,5,6);
DELETE FROM teaching_resource WHERE id NOT IN (1,2,3,4,5,6);
DELETE FROM student_count WHERE id NOT IN (1,2,3,4,5);
DELETE FROM offering_teacher WHERE id NOT IN (1,2,3,4,5,6,7);
DELETE FROM offering WHERE id NOT IN (1,2,3,4,5,6,7);
DELETE FROM course_intro WHERE course_id NOT IN (1,3);
DELETE FROM course WHERE id NOT IN (1,2,3,4,5,6,7);
DELETE FROM teacher WHERE id NOT IN (1,2,3,4,5);
DELETE FROM sys_audit_log WHERE id <> 1;
DELETE FROM sys_role_permission;
DELETE FROM sys_user WHERE id NOT IN (1,2,3,4,5,6);

-- 恢复被修改的样例数据
UPDATE sys_config SET config_value = 'false' WHERE config_key = 'cross_department';
UPDATE sys_user SET enabled = 1 WHERE id = 3;
UPDATE offering SET offering_status = '正常开课',
                    student_count = 76
 WHERE id = 5;
UPDATE supervision SET status = '已安排', finish_date = NULL, score = NULL
 WHERE id = 2;
UPDATE supervision SET status = '已完成', finish_date = DATE '2025-04-10', score = 88.0
 WHERE id = 5;
UPDATE course SET course_name = '人工智能导论', credit = 2.0, total_hours = 32, status = '草稿'
 WHERE id = 7;

-- 校验输出
SELECT '教师' AS 表, COUNT(1) AS 行数 FROM teacher
UNION ALL SELECT '课程', COUNT(1) FROM course
UNION ALL SELECT '开课', COUNT(1) FROM offering
UNION ALL SELECT '评价', COUNT(1) FROM evaluation
UNION ALL SELECT '听评课', COUNT(1) FROM supervision
UNION ALL SELECT '教学资源', COUNT(1) FROM teaching_resource
UNION ALL SELECT '学生人次', COUNT(1) FROM student_count
UNION ALL SELECT '账号', COUNT(1) FROM sys_user
UNION ALL SELECT '角色权限覆写', COUNT(1) FROM sys_role_permission;
