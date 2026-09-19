# 数据库说明 · 爱教学 Sprint 1「查课程」

> 对应任务书 US-05（课程／开课／教师统一建模与样例数据导入）
> 责任人：杨继鑫（16组 DRI / 技术与架构）　·　数据库：MySQL 8.0.43

## 1. 文件清单

| 文件 | 作用 |
| --- | --- |
| `schema.sql` | 建库建表脚本：先 `DROP DATABASE` 再 `CREATE DATABASE aijiaoxue`，共 16 张表，每字段带 COMMENT |
| `data.sql` | 样例数据导入脚本：US-05 数据导入，含 6 个演示账号与全部业务样例数据 |
| `数据字典.md` | 逐表字段明细 + 关键统计口径（覆盖率／学生人次／评教均分） |
| `README.md` | 本文件：初始化、校验、重置说明 |

## 2. 环境要求

| 项 | 要求 |
| --- | --- |
| 数据库 | MySQL 8.0+（本实验在 **MySQL 8.0.43** 上验证通过） |
| 账号 | `root` / `123456`（与 `backend/src/main/resources/application.yml` 保持一致） |
| 字符集 | `utf8mb4` / `utf8mb4_general_ci`（脚本内已显式指定，无需改 my.ini） |
| 客户端 | `mysql.exe`（Windows 默认路径 `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`） |

> ⚠️ `schema.sql` 第一条语句是 `DROP DATABASE IF EXISTS aijiaoxue;`，**会清空已有数据**，请勿在存有真实数据的实例上执行。

## 3. 初始化步骤

### 3.1 进入脚本目录

```powershell
cd D:\java\idea\idea_java_projects\aijiaoxue\database
```

### 3.2 执行建表脚本

```powershell
mysql -uroot -p123456 < schema.sql
```

或使用 `source`（路径用正斜杠）：

```powershell
mysql -uroot -p123456 -e "source D:/java/idea/idea_java_projects/aijiaoxue/database/schema.sql"
```

### 3.3 执行样例数据脚本

```powershell
mysql -uroot -p123456 < data.sql
```

> 中文乱码时追加 `--default-character-set=utf8mb4`：
> `mysql -uroot -p123456 --default-character-set=utf8mb4 < data.sql`

### 3.4 两条命令一次跑完（推荐）

```powershell
mysql -uroot -p123456 --default-character-set=utf8mb4 -e "source D:/java/idea/idea_java_projects/aijiaoxue/database/schema.sql"
mysql -uroot -p123456 --default-character-set=utf8mb4 -e "source D:/java/idea/idea_java_projects/aijiaoxue/database/data.sql"
```

## 4. 导入结果实测（本机 MySQL 8.0.43 真实执行）

### 4.1 执行结果

| 脚本 | 退出码 | 报错 | 耗时 |
| --- | --- | --- | --- |
| `schema.sql` | 0 | 无 | < 2 s |
| `data.sql` | 0 | 无 | < 3 s |

两个脚本在 MySQL 8.0.43 上**均可直接执行成功，无任何 ERROR / WARNING**。

### 4.2 表与记录数（校验 ①）

```sql
USE aijiaoxue;
SELECT 'sys_user' t, COUNT(*) n FROM sys_user
UNION ALL SELECT 'sys_role', COUNT(*) FROM sys_role
UNION ALL SELECT 'sys_permission', COUNT(*) FROM sys_permission
UNION ALL SELECT 'sys_role_permission', COUNT(*) FROM sys_role_permission
UNION ALL SELECT 'sys_config', COUNT(*) FROM sys_config
UNION ALL SELECT 'sys_audit_log', COUNT(*) FROM sys_audit_log
UNION ALL SELECT 'teacher', COUNT(*) FROM teacher
UNION ALL SELECT 'course', COUNT(*) FROM course
UNION ALL SELECT 'course_intro', COUNT(*) FROM course_intro
UNION ALL SELECT 'offering', COUNT(*) FROM offering
UNION ALL SELECT 'offering_teacher', COUNT(*) FROM offering_teacher
UNION ALL SELECT 'student_count', COUNT(*) FROM student_count
UNION ALL SELECT 'teaching_resource', COUNT(*) FROM teaching_resource
UNION ALL SELECT 'course_resource', COUNT(*) FROM course_resource
UNION ALL SELECT 'evaluation', COUNT(*) FROM evaluation
UNION ALL SELECT 'supervision', COUNT(*) FROM supervision;
```

**实测输出**：

| t | n |
| --- | --- |
| sys_user | 6 |
| sys_role | 5 |
| sys_permission | 23 |
| sys_role_permission | 58 |
| sys_config | 3 |
| sys_audit_log | 14 |
| teacher | 12 |
| course | 24 |
| course_intro | 24 |
| offering | 30 |
| offering_teacher | 38 |
| student_count | 40 |
| teaching_resource | 25 |
| course_resource | 20 |
| evaluation | 60 |
| supervision | 20 |

### 4.3 督导覆盖率（校验 ②，US-04 验收标准③）

```sql
SELECT
  (SELECT COUNT(*) FROM offering WHERE offering_status='正常开课') AS 分母_正常开课,
  (SELECT COUNT(DISTINCT offering_id) FROM supervision WHERE status='已完成') AS 分子_已完成去重,
  ROUND((SELECT COUNT(DISTINCT offering_id) FROM supervision WHERE status='已完成')
      / (SELECT COUNT(*) FROM offering WHERE offering_status='正常开课') * 100, 1) AS 覆盖率百分比;
```

**实测输出**：

| 分母_正常开课 | 分子_已完成去重 | 覆盖率百分比 |
| --- | --- | --- |
| 24 | 16 | **66.7** |

按院系维度同样有意义（覆盖率为 50.0%—83.3%，无 0% 院系）：

```sql
SELECT o.department_code 院系, COUNT(*) 正常开课, COUNT(DISTINCT s.offering_id) 已督导,
       ROUND(COUNT(DISTINCT s.offering_id)/COUNT(*)*100,1) 覆盖率
FROM offering o
LEFT JOIN supervision s ON s.offering_id=o.id AND s.status='已完成'
WHERE o.offering_status='正常开课'
GROUP BY o.department_code ORDER BY o.department_code;
```

**实测输出**：

| 院系 | 正常开课 | 已督导 | 覆盖率 |
| --- | --- | --- | --- |
| CS | 6 | 4 | 66.7 |
| EN | 6 | 5 | 83.3 |
| MATH | 5 | 3 | 60.0 |
| PHY | 3 | 2 | 66.7 |
| SE | 4 | 2 | 50.0 |

按学期：

| 学期 | 正常开课 | 已督导 | 覆盖率 |
| --- | --- | --- | --- |
| 2025-2026-1 | 22 | 15 | 68.2 |
| 2024-2025-2 | 2 | 1 | 50.0 |

### 4.4 状态分布（校验 ③）

```sql
SELECT offering_status, COUNT(*) n FROM offering GROUP BY offering_status;
SELECT status, COUNT(*) n FROM supervision GROUP BY status;
SELECT evaluation_type, COUNT(*) n, ROUND(AVG(score),2) avg_score, MIN(score) mn, MAX(score) mx
FROM evaluation GROUP BY evaluation_type;
```

**实测输出**：

| offering_status | n |
| --- | --- |
| 正常开课 | 24 |
| 已结课 | 4 |
| 停开 | 2 |

| status | n | 有完成日期 |
| --- | --- | --- |
| 待安排 | 2 | 0 |
| 已安排 | 2 | 0 |
| 已完成 | 16 | 16 |

| evaluation_type | n | avg_score | mn | mx |
| --- | --- | --- | --- | --- |
| 学生评教 | 42 | 81.65 | 69.0 | 95.5 |
| 督导评教 | 9 | 85.96 | 82.1 | 90.5 |
| 同行评议 | 9 | 87.03 | 79.2 | 94.7 |

> 全部 60 条评价分数均落在合理区间（69.0—95.5），无越界值；`已完成` 的 16 条听评课全部有 `finish_date`。

### 4.5 学期分布（校验 ④）

| semester | offerings |
| --- | --- |
| 2025-2026-1 | 22 |
| 2024-2025-2 | 8 |

### 4.6 故事原文可支撑性（校验 ⑤）

故事原文“作为教研室主任，我想查看 **5 位教师**的课程简介与开课情况，以便统筹排课”。

```sql
SELECT t.id, t.name, t.department_name,
       COUNT(DISTINCT c.id) AS 课程数, COUNT(DISTINCT ci.id) AS 简介数, COUNT(DISTINCT o.id) AS 开课数
FROM teacher t
LEFT JOIN course c ON c.owner_teacher_id=t.id
LEFT JOIN course_intro ci ON ci.course_id=c.id
LEFT JOIN offering o ON o.course_id=c.id
GROUP BY t.id, t.name, t.department_name ORDER BY t.id;
```

**实测输出（12 位教师全部满足“每人 ≥2 门课程 + 课程简介 + 开课记录”）**：

| id | name | department_name | 课程数 | 简介数 | 开课数 |
| --- | --- | --- | --- | --- | --- |
| 1 | 张伟 | 计算机学院 | 2 | 2 | 3 |
| 2 | 李娜 | 计算机学院 | 2 | 2 | 3 |
| 3 | 王强 | 计算机学院 | 2 | 2 | 2 |
| 4 | 刘洋 | 软件学院 | 2 | 2 | 2 |
| 5 | 赵敏 | 软件学院 | 2 | 2 | 3 |
| 6 | 周婷 | 数学与统计学院 | 2 | 2 | 2 |

> 全量校验：`HAVING 课程数 < 2` 返回 **0 行**（见下方校验 ⑦）。

### 4.7 学生人次一致性（校验 ⑥）

`offering.student_count` 是冗余快照，必须与 `student_count` 明细汇总**完全一致**。

```sql
SELECT o.id, o.student_count, SUM(sc.student_num) detail_sum
FROM offering o JOIN student_count sc ON sc.offering_id=o.id
GROUP BY o.id, o.student_count
HAVING o.student_count <> SUM(sc.student_num);
```

**实测输出**：**0 行**（全部 30 条开课记录的快照值与明细汇总一致）。

### 4.8 其他完整性校验（校验 ⑦）

```sql
-- 无字段遗漏 COMMENT（预期 0 行）
SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='aijiaoxue' AND (COLUMN_COMMENT='' OR COLUMN_COMMENT IS NULL);

-- 字段注释覆盖率（预期 127 / 127）
SELECT COUNT(*) AS 总字段数, SUM(COLUMN_COMMENT<>'') AS 有注释字段数
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='aijiaoxue';

-- 无数据表遗漏 COMMENT（预期 0 行）
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA='aijiaoxue' AND (TABLE_COMMENT='' OR TABLE_COMMENT IS NULL);

-- 每位教师课程数均 = 2（预期 0 行）
SELECT t.id, t.name, COUNT(c.id) courses FROM teacher t
LEFT JOIN course c ON c.owner_teacher_id=t.id
GROUP BY t.id, t.name HAVING courses <> 2;

-- 引擎与字符集（预期 16 行 InnoDB / utf8mb4）
SELECT ENGINE, TABLE_COLLATION, COUNT(*) n FROM information_schema.TABLES
WHERE TABLE_SCHEMA='aijiaoxue' GROUP BY ENGINE, TABLE_COLLATION;
```

**实测输出**：

| 校验项 | 预期 | 实测 |
| --- | --- | --- |
| 缺 COMMENT 的字段 | 0 行 | **0 行** ✅ |
| 字段注释覆盖 | 127 / 127 | **127 / 127** ✅ |
| 缺 COMMENT 的表 | 0 行 | **0 行** ✅ |
| 教师课程数 ≠ 2 | 0 行 | **0 行** ✅ |
| 引擎 / 字符集 | InnoDB / utf8mb4 | **16 张表全部 InnoDB / utf8mb4** ✅ |

### 4.9 演示账号（校验 ⑧）

```sql
SELECT username, real_name, role_code, teacher_id, department_code, enabled,
       LEFT(password,7) AS 哈希前缀, LENGTH(password) AS 长度 FROM sys_user ORDER BY id;
```

**实测输出**：

| username | real_name | role_code | teacher_id | department_code | enabled | 哈希前缀 | 长度 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| admin | 系统管理员 | ADMIN | NULL | NULL | 1 | `$2a$10$` | 60 |
| director | 周婷 | DIRECTOR | NULL | MATH | 1 | `$2a$10$` | 60 |
| teacher1 | 张伟 | TEACHER | 1 | CS | 1 | `$2a$10$` | 60 |
| teacher2 | 李娜 | TEACHER | 2 | CS | 1 | `$2a$10$` | 60 |
| supervisor | 教学督导 | SUPERVISOR | 7 | NULL | 1 | `$2a$10$` | 60 |
| courseadmin | 课程管理员 | COURSE_ADMIN | NULL | NULL | 1 | `$2a$10$` | 60 |

BCrypt 哈希已用 `bcrypt.checkpw` 验证：`$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi` 对应明文 **`123456`**，与 Spring Security `BCryptPasswordEncoder` 兼容。

## 5. 重置数据

三种方式，按需选择：

```powershell
# 方式 1（推荐）：完整重建——先删库重建表，再重新导入样例数据
cd D:\java\idea\idea_java_projects\aijiaoxue\database
mysql -uroot -p123456 --default-character-set=utf8mb4 < schema.sql
mysql -uroot -p123456 --default-character-set=utf8mb4 < data.sql

# 方式 2：仅清空业务数据但保留表结构（演示前恢复初始状态）
mysql -uroot -p123456 aijiaoxue -e "SET FOREIGN_KEY_CHECKS=0; TRUNCATE TABLE supervision; TRUNCATE TABLE evaluation; TRUNCATE TABLE course_resource; TRUNCATE TABLE teaching_resource; TRUNCATE TABLE student_count; TRUNCATE TABLE offering_teacher; TRUNCATE TABLE offering; TRUNCATE TABLE course_intro; TRUNCATE TABLE course; TRUNCATE TABLE teacher; TRUNCATE TABLE sys_audit_log; SET FOREIGN_KEY_CHECKS=1;"

# 方式 3：只重置账号密码为 123456
mysql -uroot -p123456 aijiaoxue -e "UPDATE sys_user SET password='\$2a\$10\$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', enabled=1;"
```

> 方式 2 执行后需再跑一次 `data.sql` 才能恢复样例数据（`TRUNCATE` 会重置自增 ID）。
> 因脚本未定义 `FOREIGN KEY` 约束，`TRUNCATE` / 单表清理**不会触发外键报错**。

## 6. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `ERROR 1045 Access denied for user 'root'` | 密码不是 `123456` | 改 `data.sql` 无关，需同步修改 `backend` 的 `application.yml` 与本文件命令 |
| `ERROR 1049 Unknown database 'aijiaoxue'` | 未先执行 `schema.sql` | 先跑 `schema.sql` |
| `ERROR 1062 Duplicate entry` | 重复导入 `data.sql` | 先跑 `schema.sql` 重建库，或按 §5 方式 1 重置 |
| 中文显示 `???` 或乱码 | 客户端字符集不是 utf8mb4 | 命令追加 `--default-character-set=utf8mb4` |
| `mysql` 不是内部或外部命令 | 未加 PATH | 用全路径 `& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'` |
| 导入 `data.sql` 时提示 `Using a password ... insecure` | MySQL 安全警告，非错误 | 可忽略；退出码仍为 0 |

## 7. 与其他目录的接口约定

| 约定 | 内容 |
| --- | --- |
| 后端连接 | `jdbc:mysql://localhost:3306/aijiaoxue?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai`，账号 `root/123456` |
| 表名映射 | 反向工程生成 MyBatis-Plus 实体时，`sys_user`→`SysUser` 等，字段 `snake_case`→`camelCase` |
| 覆盖率实现 | 必须按本文件 §4.3 与 `数据字典.md` §3.1 的 SQL 口径实现，禁止自行改写 |
| 学生人次实现 | 采用 `数据字典.md` §3.2 的**口径 A**（`SUM(student_count.student_num)`） |
| 枚举值 | 全部使用中文枚举（`正常开课`/`已完成`/`学生评教` 等），见 `数据字典.md` §2 各表“取值范围/枚举”列 |

## 8. 样例数据性质声明

`data.sql` 中全部数据均为**教学演示用虚构样例数据**，不代表任何真实教学数据；学生人次与评教分数为**模拟生成**，仅用于验证查询与统计功能。详见 `data.sql` 文件头的《样例数据来源与性质声明》。
