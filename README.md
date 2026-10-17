# 爱教学 · 教学质量全链路数字化管理平台

> **实验二 Sprint 1「查课程」** —— 《软件项目管理》16组
> 敏捷闭环实践：Sprint 计划会 → 每日站会 → 人机协同开发 → Sprint 评审 → Sprint 回顾

| 项 | 内容 |
| --- | --- |
| 团队 | 16组 |
| 项目背景 | 爱教学（教学质量全链路数字化管理平台）· 进阶1「查课程」 |
| 本轮 DRI | 杨继鑫（第2位轮值，兼任 PO 与 Scrum Master） |
| 团队成员 | 余润东（产品与范围）、刘鹏杰（质量与风险）、刘润七（AI协调与记录） |
| Sprint 切片 | 实验一用户故事地图第 1 个发布切片 |
| 项目周期 | 6 周（本实验为教学模拟 Sprint，约 180 分钟） |

> **Sprint 2 增量（实验三）**：US-07 资源挂接闭环（刘润七）、US-08 备课查看增强（杨继鑫）、US-09 评教分析导出（余润东）、US-10 听评课状态机（刘鹏杰）、T-01 CI/回归基线（杨继鑫）。US-07 新增 13 个、US-08/US-10 新增 8 个、US-09 新增 5 个回归用例，合并后当前后端共 **107 个自动化用例全绿**，并新增 GitHub Actions 与本地等价 CI 脚本。
> 详细分工见 `16组_Sprint2分工规划.md`，执行记录见 `docs/evidence/sprint2/ci/local-ci-run.md`。

---

## 一、Sprint 1 目标

> **交付一个可运行的「查课程」最小原型**，实现课程、开课、教师数据统一建模与全量查询，并让教研室主任、教师、教学督导、系统管理员四类角色可登录并按权限查看各自工作台的核心数据。

打通 **统一数据建模 → 样例数据导入 → 角色权限 → 三类业务工作台查询** 的最短闭环，为 Sprint 2 的资源、评教与统计功能奠定基线与接口契约。

## 二、Sprint 1 用户故事清单（6 条，全部 Must）

| 编号 | 用户故事 | 主责 | 验收标准 | 状态 |
| --- | --- | --- | --- | --- |
| US-01 | 作为**系统管理员**，我想配置角色与权限，以便不同角色只能访问其职责范围内的数据 | 杨继鑫 | ① 含管理员/教研室主任/教师/教学督导四类角色；② 各角色登录后仅能看到权限矩阵内允许的页面与数据；③ 越权访问被拦截并给出提示 | ☐ |
| US-02 | 作为**教研室主任**，我想查看 5 位教师的课程简介与开课情况，以便统筹排课 | 余润东 | ① 可按教师查看课程简介、开课学期与班级；② 支持按教师/学期筛选；③ 查询结果字段与数据口径一致 | ☐ |
| US-03 | 作为**教师**，我想查看学生人次与课程资源，以便备课 | 刘润七 | ① 教师登录后只见本人课程；② 可查看每门课的学生人次；③ 可查看已挂接的课程资源列表 | ☐ |
| US-04 | 作为**教学督导**，我想查看课程开设与督导覆盖率，以便安排听评课 | 刘鹏杰 | ① 可查看全校开课清单；② 可按开课/未开课、是否已督导筛选；③ 督导覆盖率计算口径明确且结果正确 | ☐ |
| US-05 | 作为**开发团队**，我想统一课程、开课、教师数据模型并导入样例数据，以便全量查询有可靠数据来源 | 杨继鑫 | ① 课程/开课/教师/学生人次/资源/评价基础表建成；② 样例数据可一键导入；③ 数据来源与性质有明确标注 | ☑ 数据层完成 |
| US-06 | 作为**质量负责人**，我想对关键路径执行测试并输出报告，以便确认增量达到 DoD | 刘鹏杰 | ① 查询、筛选、权限拦截关键路径有测试用例；② 全量测试通过并可复现；③ 输出测试报告含用例、结果与遗留缺陷 | ☐ |

> 状态列由 Sprint 评审会（表6）逐条确认后勾选；未满足 DoD 的故事标记「未完成」，不计入 Sprint 增量。

## 三、技术栈

| 层 | 技术 | 版本 |
| --- | --- | --- |
| 语言 | Java | 17 |
| 后端框架 | Spring Boot | 3.4.2 |
| 持久层 | MyBatis-Plus | 3.5.10 |
| 安全 | Spring Security（登录认证 + 角色/权限鉴权） | 随 Boot 3.4.2 |
| 数据库 | MySQL | 8.0+（验证于 8.0.43） |
| 测试库 | H2（内存库，保证 `mvn test` 不依赖本机 MySQL） | 2.2.224 |
| 测试框架 | JUnit 5 + Spring Boot Test（MockMvc 集成测试） | JUnit 5.12.2 |
| 前端 | 原生 HTML / CSS / JavaScript SPA（hash 路由） | — |
| 构建 | Maven | 3.9+ |

## 四、项目结构

```
aijiaoxue/
├── backend/                    后端服务（Spring Boot 3.4.2 + MyBatis-Plus 3.5.10）
│   ├── pom.xml
│   ├── start.bat               一键启动脚本（离线环境用 classpath 方式启动）
│   └── src/
│       ├── main/java/com/aijiaoxue/
│       │   ├── common/         统一响应、分页、异常处理
│       │   ├── config/         MyBatis-Plus 分页插件、Web 资源映射
│       │   ├── security/       令牌认证、权限矩阵、数据范围、安全配置
│       │   ├── audit/          操作审计切面（@AuditLog）
│       │   ├── controller/     REST 控制器（10 个）
│       │   ├── service/        业务服务（10 个）
│       │   ├── mapper/         MyBatis-Plus Mapper（16 个）
│       │   ├── entity/         实体与视图对象
│       │   ├── dto/            请求/响应对象
│       │   └── util/           CSV 导入导出工具
│       ├── main/resources/application.yml     MySQL 连接与 MyBatis-Plus 配置
│       └── test/
│           ├── java/com/aijiaoxue/            7 个测试类、107 个用例（Sprint 1 基线 81 + Sprint 2 新增 26）
│           └── resources/                     H2 测试库脚本与配置
├── frontend/                   前端 SPA（原生 HTML/CSS/JS，hash 路由）
│   ├── index.html              单页入口：登录 → 工作台 → 各功能页
│   ├── README.md               前端结构与联调说明
│   └── assets/
│       ├── css/style.css
│       └── js/                 api / ui / charts / router + pages/（11 个页面）
├── database/                   数据库脚本与数据字典
│   ├── schema.sql              建库建表（16 张表，每字段带 COMMENT）
│   ├── data.sql                样例数据（US-05 数据导入）
│   ├── 数据字典.md              逐表字段明细 + 关键统计口径
│   └── README.md               初始化 / 校验 / 重置说明
└── docs/                       契约与过程文档
    ├── API契约.md               接口冻结基线（第 1—8 节接口清单与权限码）
    ├── 测试报告.md              DoD 第②条：测试用例清单与执行记录
    └── smoke_test.py            端到端冒烟测试（56 项，针对真实 MySQL）
```

## 五、环境要求

| 项 | 要求 | 本机验证版本 |
| --- | --- | --- |
| JDK | 17 及以上 | 17.0.18 |
| Maven | 3.9 及以上 | 3.9.11 |
| MySQL | 8.0 及以上 | 8.0.43 |
| 浏览器 | Chrome / Edge 最新版 | — |

## 六、运行指南

> 以下命令可直接复制粘贴。假设项目根目录为 `D:\java\idea\idea_java_projects\aijiaoxue`。

### 步骤 1：初始化数据库

```powershell
cd D:\java\idea\idea_java_projects\aijiaoxue\database
mysql -uroot -p123456 --default-character-set=utf8mb4 < schema.sql
mysql -uroot -p123456 --default-character-set=utf8mb4 < data.sql
```

预期：两条命令退出码均为 0，无 ERROR。数据量为 教师12 / 课程24 / 开课30 / 学生人次40 / 资源25 / 评价60 / 听评课20。
完整校验语句与实测输出见 `database/README.md` 第 4 节。

### 步骤 2：启动后端

```powershell
cd D:\java\idea\idea_java_projects\aijiaoxue\backend
mvn spring-boot:run
```

> **离线/受限环境备选**：若本机 Maven 仓库不完整（`mvn spring-boot:run` 或 `mvn package` 因缺插件失败），
> 可改用一键脚本（先编译、再以 classpath 方式启动）：
>
> ```powershell
> cd D:\java\idea\idea_java_projects\aijiaoxue\backend
> mvn -o -B compile
> .\start.bat
> ```

启动成功后访问 <http://localhost:8080>（会自动跳转到登录页 `/index.html`）。
数据库连接配置见 `backend/src/main/resources/application.yml`（默认 `root` / `123456` / `aijiaoxue`）。

> **数据库初始化说明**：应用启动**不会**自动执行建表脚本（避免每次启动清空数据）。
> 首次运行前请先完成「步骤 1」。

#### 数据看起来"变少了"怎么办

`database/data.sql` **不是幂等脚本**（只含 INSERT），重复执行会因主键冲突中途失败，
留下"只导入了一部分"的半成品数据。若发现课程/教师数量明显偏少，
按下面步骤**完整重载**（`schema.sql` 会先重建库，因此必须先跑它）：

```powershell
cd D:\java\idea\idea_java_projects\aijiaoxue\database
mysql -uroot -p123456 --default-character-set=utf8mb4 < schema.sql   # 重建库与 16 张表
mysql -uroot -p123456 --default-character-set=utf8mb4 < data.sql    # 导入权威样例数据
```

重载后的预期行数（可复制到 mysql 中自检）：

| 表 | 行数 | 表 | 行数 |
| --- | --- | --- | --- |
| `teacher` | 12 | `course` | 24 |
| `offering` | 30（正常开课 24） | `student_count` | 40 |
| `teaching_resource` | 25 | `evaluation` | 60 |
| `supervision` | 20 | `sys_user` | 6 |

督导覆盖率应为 **16 / 24 = 66.7%**。完整校验 SQL 见 `database/README.md`。

### 步骤 3：打开前端

后端已托管 `frontend/` 静态资源，直接访问：

```
http://localhost:8080/index.html
```

或不经后端直接打开（需后端已启动以提供 API）：

```
D:\java\idea\idea_java_projects\aijiaoxue\frontend\index.html
```

前端路由（hash）：`#/login`、`#/dashboard`、`#/courses`、`#/offerings`、`#/teachers`、`#/resources`、`#/prep`、`#/evaluations`、`#/supervision`、`#/coverage`、`#/admin`。

### 步骤 4：跑测试

```powershell
# 4.1 自动化集成测试（H2 内存库，107 个用例，不依赖 MySQL）
cd D:\java\idea\idea_java_projects\aijiaoxue\backend
mvn -o test

# 4.2 端到端冒烟测试（针对真实 MySQL，需后端已在运行）
cd D:\java\idea\idea_java_projects\aijiaoxue
python docs\smoke_test.py
```

预期结果：`Tests run: 107, Failures: 0, Errors: 0` → `BUILD SUCCESS`；冒烟测试 `通过 56 项，失败 0 项`。
测试用例清单与执行记录见 `docs/测试报告.md`。

### 步骤 5：演示账号（密码统一 `123456`）

| 账号 | 姓名 | 角色 | 数据可见范围 | 建议演示内容 |
| --- | --- | --- | --- | --- |
| `admin` | 系统管理员 | ADMIN | 全量 | 用户与权限管理、权限矩阵、操作日志 |
| `director` | 周婷 | DIRECTOR | 本教研室（`cross_department=false`） | 主任工作台：课程/开课/教师/覆盖率/评教概览 |
| `teacher1` | 张伟 | TEACHER | 仅本人（teacher_id=1） | 教师工作台、我的课程、学生人次、备课查看 |
| `teacher2` | 李娜 | TEACHER | 仅本人（teacher_id=2） | 教师工作台（对比 teacher1 验证数据隔离） |
| `supervisor` | 教学督导 | SUPERVISOR | 全校（只读为主） | 开课清单、听评课登记、督导覆盖率 66.7% |
| `courseadmin` | 课程管理员 | COURSE_ADMIN | 全校 | 课程库与开课维护、资源维护 |

> BCrypt 哈希已用 `bcrypt.checkpw` 验证对应明文 `123456`。
> 具体院系代码与教师绑定关系以 `database/data.sql` 为准。

**各账号可见数据量**（基于权威样例数据，可作为验收自检基准）：

| 账号 | 课程 | 开课 | 教师 | 资源 | 评价 | 听评课 |
| --- | --- | --- | --- | --- | --- | --- |
| `admin` | 24 | 30 | 12 | 25 | 60 | 20 |
| `director`（MATH 教研室） | 4 | 6 | 2 | 6 | 14 | 无权访问 |
| `teacher1`（张伟，CS） | 2 | 4 | 无权访问 | 4 | 4 | 无权访问 |
| `teacher2`（李娜，CS） | 2 | 3 | 无权访问 | 3 | 6 | 无权访问 |
| `supervisor` | 24 | 30 | 12 | 无权访问 | 60 | 20 |
| `courseadmin` | 24 | 30 | 12 | 无权访问 | 无权访问 | 无权访问 |

> 出现「无权访问」是 US-01 越权拦截的正确表现（该角色无对应权限码），不是故障。
> 各账号看到的数据条数不同，正是 ALL / DEPARTMENT / SELF 三重数据范围生效的证明。

**`director` 账号的演示说明**：该账号绑定 `MATH` 教研室（`cross_department=false`），
因此主任工作台只统计 MATH 院系数据 —— 这是数据范围收窄的正确表现，演示时数字比 admin 少属预期。
若想展示全校规模的主任视图，可临时开启跨院查询：

```sql
UPDATE sys_config SET config_value = 'true'  WHERE config_key = 'cross_department';  -- 允许跨院
UPDATE sys_config SET config_value = 'false' WHERE config_key = 'cross_department';  -- 恢复默认
```

## 七、分支策略

```
main（可运行基线，任何人不得直推）
 ├── feature/us01-permission    US-01 角色与权限配置
 ├── feature/us02-director      US-02 教研室主任查课程/开课/教师
 ├── feature/us03-teacher       US-03 教师查本人课程与学生人次、备课查看
 ├── feature/us04-supervisor    US-04 教学督导查开课情况与督导覆盖率
 └── feature/us05-model         US-05 统一数据建模与样例数据导入
```

规则：
1. **AI 编码智能体只在 `feature/*` 分支工作**，负责创建分支、编写代码与配套测试、生成提交。
2. 人类成员**审查 diff 而非逐行编写**；审查通过并测试通过后，由 **DRI 合入 `main`**。
3. `main` 分支任何时刻都必须可运行（README 中的启动命令可跑通）。
4. 合入前必须满足 DoD 四项，未通过不得合入。

## 八、DoD（完成定义）

本 Sprint 团队协商确定的 DoD 四项：

| # | 标准 | 本轮达成情况 |
| --- | --- | --- |
| ① | **功能可运行**：四类角色可登录并完成核心查询，无阻塞性错误 | ☑ **已达成**：后端接 MySQL 启动成功，56 项接口冒烟全部通过 |
| ② | **有基本测试**：关键路径（查询、筛选、权限拦截）单元测试或集成测试通过 | ☑ **已达成**：107 个自动化用例全绿（Sprint 1 基线 81 + Sprint 2 新增 26），含权限拦截、覆盖率计算、CSV 导出 |
| ③ | **有代码注释**：关键函数、Service、接口与数据模型有清晰注释 | ☑ **已达成**：Java 类/方法、16 表 127 字段、前端模块均含中文注释 |
| ④ | **代码已审查**：AI 生成代码经人工审查 diff 后合入，测试通过方可标记完成 | ☑ **已达成**：审查中发现并修复 7 个真实缺陷（见 `docs/测试报告.md` 5.2） |

> DoD 达成情况在 Sprint 评审会逐条确认，并写入 `docs/测试报告.md` 与自评报告（表8）。

## 九、常见问题排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| **MySQL 连不上**：`Communications link failure` / `Connection refused` | MySQL 服务未启动 | `Get-Service MySQL80` 查看状态；`Start-Service MySQL80`（管理员 PowerShell）启动 |
| **MySQL 连不上**：`Access denied for user 'root'@'localhost'` | 密码不是 `123456` | 改 `backend/src/main/resources/application.yml` 的 `spring.datasource.password` |
| **MySQL 连不上**：`Unknown database 'aijiaoxue'` | 未初始化数据库 | 执行本文档「步骤 1」 |
| **端口占用**：`Web server failed to start. Port 8080 was already in use` | 8080 被占用 | 换端口：`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`；或查占用：`netstat -ano \| findstr :8080` 后 `taskkill /PID <PID> /F` |
| **端口占用**：`Port 3306 was already in use` | 本机已有其他 MySQL | 直接用已运行的实例，或改端口后同步改 `application.yml` |
| **乱码**：页面中文显示 `???` 或数据库中文乱码 | 客户端/连接字符集不是 utf8mb4 | 导入时加 `--default-character-set=utf8mb4`；连接串已配置 `useUnicode=true&characterEncoding=UTF-8`（库/表层为 utf8mb4） |
| **乱码**：`mvn` 编译报 `unmappable character for encoding` | 编译字符集不对 | 确认 `pom.xml` 中 `project.build.sourceEncoding=UTF-8`；控制台执行 `chcp 65001` |
| **前端 API 请求全部 401** | 未登录或 token 失效 | 先访问 `#/login` 用演示账号登录；刷新页面后重新登录 |
| **前端 API 请求 403** | 当前角色无该权限码（越权被拦截，属预期行为） | 用对应角色的账号登录；US-01 验收标准③要求的正是此拦截 |
| **`mvn test` 报数据库连接失败** | 测试误用了 MySQL 配置 | 测试库为 H2，检查 `backend/src/test/resources/application-test.yml` |
| **导入 `data.sql` 提示 `Using a password ... insecure`** | MySQL 安全警告，非错误 | 可忽略，退出码仍为 0 |

## 十、相关文档

| 文档 | 说明 |
| --- | --- |
| `docs/API契约.md` | 接口冻结基线（v1.0，计划会开场 10 分钟冻结），前后端一律以此为准 |
| `docs/测试报告.md` | DoD 第②条：测试范围、环境、107 用例执行结果、缺陷清单、遗留风险 |
| `docs/smoke_test.py` | 端到端冒烟脚本（56 项，校验 6 个账号、核心接口、越权拦截、前端资源） |
| `database/数据字典.md` | 16 张表逐字段明细 + 关键统计口径（覆盖率/学生人次/评教均分） |
| `database/README.md` | 数据库初始化、校验语句与实测结果、数据重置 |
| `16组_Sprint1分工规划.docx` | 实验二交付物：表1—表8 过程记录与分工规划 |

## 十一、样例数据声明

`database/data.sql` 中全部数据均为**教学演示用虚构样例数据**，不代表任何真实教学数据；学生人次与评教分数为**模拟生成**，仅用于验证查询与统计功能。
