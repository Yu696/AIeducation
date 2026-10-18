# 《软件项目管理》实验四 Sprint 3 代码分工说明（文件/模块/接口/测试级）

> 项目：爱教学（教学质量全链路数字化管理平台）· 16 组
> 本轮 DRI：刘润七（第 4 位轮值，兼任 PO / Scrum Master / 发布总负责人）
> 成员：余润东、杨继鑫、刘鹏杰、刘润七
> 基线：`main@10c616f`；JDK 17 发布基线；108 个后端测试 + 56 项真实 MySQL 冒烟已通过（2026-10-18）
> 对应范围：US-11 排课统筹总览（只读）+ T-02—T-07 发布收尾任务包
> 配套文件：`16组_Sprint3分工规划.md`（计划与会议记录模板）、`16组_Sprint3分工规划.docx`、`16组_Sprint3分工规划_代码分工版.docx`

---

## 1. 代码范围与实现口径（先冻结，再动手）

### 1.1 本轮只做这些代码

| 编号 | 内容 | 代码性质 | 是否改表 | 是否新增接口 |
| --- | --- | --- | --- | --- |
| US-11 | 排课统筹总览（只读；原 US-DEAN-05） | 前端新增页面 + 路由 + 后端契约测试 | 否 | 否 |
| T-02 | 发布基线与 CI 证据 | 构建/测试/证据，不改业务代码 | 否 | 否 |
| T-03 | 端到端主旅程与权限旅程 | 测试脚本/证据 | 否 | 否 |
| T-04 | Release Notes/用户手册/README | 文档 | 否 | 否 |
| T-05 | 技术债台账 | 文档 | 否 | 否 |
| T-06 | Release Checklist/证据/Tag | 文档 + Git Tag | 否 | 否 |
| T-07 | 项目度量与复盘数据 | 文档 | 否 | 否 |

### 1.2 US-11 的实现路线（推荐且已冻结）

**前端只读页 + 复用现有开课接口，后端只补契约测试。**

理由：

1. 现有 `GET /api/offerings` 已返回排课总览所需全部字段：
   - `offeringCode`、`courseCode`、`courseName`、`semester`、`className`
   - `teacherName`、`departmentName`、`capacity`、`studentCount`
   - `offeringStatus`、`scheduleInfo`、`supervised`、`supervisionTimes`
2. 该接口已支持 `semester`、`teacherId`、`keyword`、`departmentCode`、`offeringStatus`、`supervised` 筛选；
3. 该接口已在服务端执行数据范围收窄：教师只看本人，主任只看本教研室，督导全校只读；
4. 当前种子数据只有 16 条开课记录，前端一次取 200 条后按“学期—教师—教学班”分组足够，不需要新增聚合接口；
5. 不改表、不新增接口，能把发布风险压到最低，符合本轮“发布就绪优先”的目标。

**明确不做**：

- 不做排课冲突检测（无结构化时间片/教室/教师可用性模型）；
- 不写 `offering`、`student_count`、`supervision` 等任何表；
- 不新增 `schedule` 表、不改 `offering.schedule_info` 字段；
- 不改 `pom.xml` 运行时依赖；
- 不为了让页面“看起来完整”而伪造未实现功能。

**升级条件**：如果实现中发现现有接口无法表达总览（例如必须服务端分页聚合、按时间片分组），由杨继鑫在站会上提出，DRI 批准后才可新增 `GET /api/offerings/schedule-overview`；新增接口必须同时完成：契约、Controller/Service/Mapper、权限码、数据范围、测试、文档，且重新评估发布时间。

---

## 2. 分支与合并顺序

| 顺序 | 分支 | Owner | 范围 | 从哪来 | 合入前门禁 |
| --- | --- | --- | --- | --- | --- |
| 1 | `feature/sprint3-schedule-overview` | 杨继鑫 | US-11 前端页/路由/契约测试/技术债台账 | 最新 `main` | 109+ 测试通过；页面手工走通；无写接口调用 |
| 2 | `feature/sprint3-regression-evidence` | 刘鹏杰 | 冒烟新增 4 项、全量回归、测试报告修正、证据归档 | `main`（可在 1 合入后 rebase） | 56+4 冒烟通过；108+ 回归通过；证据路径完整 |
| 3 | `feature/sprint3-release-docs` | 余润东 | Release Notes、用户手册、README、主旅程证据 | `main`（建议在 1、2 合入后） | 文档逐条对照真实功能；命令可复现 |
| 4 | `feature/sprint3-closeout` | 刘润七 | 分工规划/代码分工说明、发布检查清单、复盘与自评模板、Tag | `main`（最后） | 12 项清单签字；8 项交付物齐备 |

**合并规则**：

- `main` 保持可运行，禁止直接 push；
- 每个分支 push 后在 GitHub 开 PR（本机无 `gh`，用网页入口）；
- 作者不能自己合并；至少 1 名非作者审查 + DRI 终检；
- 后合入的分支先 rebase 最新 `main`，冲突由该分支 owner 解决；
- 合并前必须贴出：改动文件、测试命令与结果、证据路径、已知风险。

---

## 3. 文件级 Owner 矩阵

> “新增/修改/只读”指本轮预计动作；“备份”用于 owner 请假或阻塞时接手。

| 路径 | 动作 | 内容 | Owner | 审查人 | 备份 | 合并顺序 |
| --- | --- | --- | --- | --- | --- | --- |
| `frontend/assets/js/pages/schedule.js` | 新增 | 排课统筹总览页面模块：筛选、分组、只读表格、空态、`scheduleInfo` 缺失提示 | 杨继鑫 | 刘润七 + ChatGPT | 余润东 | 1 |
| `frontend/assets/js/router.js` | 修改 | 新增 `/schedule` 路由；标题“排课统筹总览”；分组“课程与教学”；角色与 `offering:read` 权限 | 杨继鑫 | 刘润七 | 刘鹏杰 | 1 |
| `frontend/index.html` | 修改 | 引入 `pages/schedule.js`；按余润东提供的文案更新品牌/版本信息 | 杨继鑫 | 余润东 | 刘润七 | 1 |
| `backend/src/test/java/com/aijiaoxue/us/OfferingAndDataIT.java` | 修改 | 新增 `offeringScheduleOverview`：按学期取数并断言 `scheduleInfo` 返回 | 杨继鑫 | 刘鹏杰 | 刘润七 | 1 |
| `docs/API契约.md` | 修改 | 增加 US-11 “复用 GET /api/offerings”的契约说明与字段清单 | 杨继鑫 | 余润东 | 刘润七 | 1 |
| `docs/技术债台账.md` | 新增 | TD-01—TD-05 总台账、处置决定、偿还计划、责任人 | 杨继鑫 | 刘润七 | 余润东 | 1 |
| `docs/smoke_test.py` | 修改 | 新增排课总览 4 项只读检查（主任登录、字段存在、学期筛选、教师筛选） | 刘鹏杰 | 杨继鑫 | 刘润七 | 2 |
| `docs/测试报告.md` | 修改 | 把 108/56 的实际执行结果、日期、环境写一致；删除“已跑/待跑”矛盾表述 | 刘鹏杰 | 杨继鑫 + 刘润七 | 余润东 | 2 |
| `docs/evidence/sprint3/**` | 新增 | 回归日志、冒烟日志、E2E 截图/录屏、CI 日志、检查清单 | 刘鹏杰/余润东/刘润七 | DRI | 各自备份 | 2—4 |
| `docs/发布说明.md` | 新增 | Release Notes：已发布/演示过/顺延、已知限制、升级/回滚说明 | 余润东 | 刘润七 | 杨继鑫 | 3 |
| `docs/用户手册.md` | 新增 | 登录、主旅程、排课总览、常见问题、演示账号 | 余润东 | 刘润七 | 刘鹏杰 | 3 |
| `README.md` | 修改 | Sprint 1/2/3 当前状态、JDK 17 构建命令、测试/冒烟命令、文档索引 | 余润东 | 杨继鑫 | 刘润七 | 3 |
| `16组_Sprint3分工规划.md` / `.docx` / `_代码分工版.docx` | 新增/修改 | 计划、会议记录模板、代码分工说明 | 刘润七 | 全员 | 余润东 | 4 |
| `docs/evidence/sprint3/release-checklist.md` | 新增 | 12 项 Release Checklist、逐项证据路径、确认人、日期 | 刘润七 | 刘鹏杰 | 余润东 | 4 |

### 3.1 本轮明确“只读/不改”的文件

| 路径 | 原因 |
| --- | --- |
| `backend/src/main/java/com/aijiaoxue/controller/OfferingController.java` | US-11 复用现有接口，不改 |
| `backend/src/main/java/com/aijiaoxue/service/OfferingService.java` | 数据范围与查询逻辑已满足只读总览 |
| `backend/src/main/java/com/aijiaoxue/mapper/OfferingMapper.java` | `VIEW_COLUMNS` 已包含 `schedule_info` |
| `backend/src/main/java/com/aijiaoxue/security/PermissionCodes.java` | 复用 `offering:read`，不新增权限码 |
| `database/schema.sql` / `database/data.sql` | 不改表、不改种子数据 |
| `backend/pom.xml` | 不新增/升级依赖 |
| `.github/workflows/sprint2-ci.yml` | 沿用；如重命名需单独评审，不在本轮临时改 |

---

## 4. 接口契约分工

### 4.1 本轮使用的现有接口

| 方法 | 路径 | 用途 | 关键参数 | 关键返回字段 | Owner | 消费方 | 测试 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/offerings` | 排课总览数据源 | `page`、`size`、`keyword`、`semester`、`teacherId`、`departmentCode`、`offeringStatus`、`supervised` | `records[].offeringCode/courseName/semester/className/teacherName/scheduleInfo/offeringStatus/studentCount` | 杨继鑫（契约说明） | `pages/schedule.js` | `OfferingAndDataIT#offeringScheduleOverview` + 现有开课测试 |
| GET | `/api/offerings/semesters` | 学期下拉 | 无 | `string[]` | 杨继鑫（复用） | `pages/schedule.js` | 现有 `OfferingAndDataIT` |
| GET | `/api/teachers` | 教师下拉/筛选 | `keyword`、`page`、`size` | `records[].id/name` | 杨继鑫（复用） | `pages/schedule.js` | 现有教师测试 |

### 4.2 本轮明确不调用的接口

| 方法 | 路径 | 原因 |
| --- | --- | --- |
| POST/PUT/DELETE | `/api/offerings/**` | 只读总览，禁止写操作 |
| POST | `/api/offerings/{id}/student-counts` | 不修改学生人次 |
| 任意 | `/api/supervisions/**` | 不做听评课安排/状态流转 |
| 任意 | `/api/admin/**` | 不做权限配置变更 |

### 4.3 可选接口（未批准，不得先写）

`GET /api/offerings/schedule-overview`

- 触发条件：现有分页接口无法满足总览分组/性能；
- 需 DRI 书面同意；
- 必须同时交付：`OfferingController` 只读方法、`OfferingService` 只读方法、`OfferingMapper` 查询、`offering:read` 权限、数据范围、契约、测试、文档；
- 未走完上述流程前，代码评审直接拒绝。

---

## 5. 测试分工

### 5.1 自动化测试

| 测试类/方法 | 类型 | Owner | 覆盖点 | 通过标准 |
| --- | --- | --- | --- | --- |
| `OfferingAndDataIT#offeringScheduleOverview`（新增） | MockMvc + H2 | 杨继鑫 | 按学期查询开课总览；`scheduleInfo` 字段返回；分页结构正确 | 断言状态 200、`code=0`、记录含目标学期、字段非缺失 |
| `DataScopeIT`（现有） | MockMvc + H2 | 刘鹏杰复核 | 教师只看本人教学班；主任只看本教研室 | 不出现越权数据 |
| `PermissionAccessControlIT`（现有） | MockMvc + H2 | 刘鹏杰复核 | 未登录/无权限访问开课接口 | 401/403 符合契约 |
| `OfferingAndDataIT`（现有） | MockMvc + H2 | 杨继鑫复核 | 开课筛选、详情、导出、增删改回归 | 全部保持通过 |
| 其余 6 个测试类 | MockMvc + H2 | 刘鹏杰 | 全量回归 | 108 个既有用例全绿 |

**测试命令**：

```bash
# JDK 17 发布基线
cd backend
mvn -B -ntp test

# 本机 JDK 23 临时开发（必须带 proc=full）
mvn -B -Dmaven.compiler.proc=full test
```

### 5.2 真实 MySQL 冒烟

| 检查 | 内容 | Owner | 通过标准 |
| --- | --- | --- | --- |
| SM-01 | 主任登录后 `GET /api/offerings?size=200` | 刘鹏杰 | 200；记录数 > 0 |
| SM-02 | 返回记录包含 `scheduleInfo` 字段 | 刘鹏杰 | 至少 1 条有值；无值为空串而非报错 |
| SM-03 | `GET /api/offerings?semester=2025-2026-1` | 刘鹏杰 | 返回记录的 `semester` 全部匹配 |
| SM-04 | `GET /api/offerings?teacherId=<teacher1>` | 刘鹏杰 | 返回记录均为该教师（含合上） |
| 现有 56 项 | 登录、课程、开课、资源、评教、听评课、覆盖率、导出、权限 | 刘鹏杰 | 56/56 通过 |

**冒烟命令**（后端已启动、真实 MySQL 已初始化）：

```bash
python docs/smoke_test.py
```

### 5.3 端到端旅程

| 旅程 | 步骤 | Owner | 证据 |
| --- | --- | --- | --- |
| 主旅程 | 主任登录 → 工作台 → 排课统筹总览 → 按学期筛选 → 查看课程/教师/班级/上课时间地点 → 返回工作台 | 余润东 | 截图/录屏 + 步骤说明 |
| 权限旅程 | 教师登录 → 只看到本人教学班；督导登录 → 全校只读；未登录 → 401；越权 → 403 | 刘鹏杰 | 截图 + 接口响应 |
| 异常旅程 | 学期无数据 → 空态；`scheduleInfo` 为空 → “未填写”；后端不可用 → 友好错误提示 | 刘鹏杰 | 截图 |

---

## 6. 数据与 SQL 分工

| 事项 | Owner | 说明 |
| --- | --- | --- |
| 确认 `offering.schedule_info` 存在且类型为 `VARCHAR(255)` | 杨继鑫 | 只读核对，不改 schema |
| 确认 16 条种子开课记录可查询 | 刘鹏杰 | 数据基线：16 张表、覆盖率 16/24=66.7% |
| 确认 `scheduleInfo` 有值/空值的展示 | 杨继鑫 | 空值显示“未填写”，不报错 |
| 如确需补种子数据 | 杨继鑫 | 必须经 DRI 批准，只改 `database/data.sql`，不改表；重载后重跑 108+ 测试与 56+ 冒烟 |
| 禁止事项 | 全员 | 不新增表、不新增字段、不改字段类型、不写 `DROP DATABASE` 脚本进仓库 |

---

## 7. 提交规范

### 7.1 Commit 前缀

| 前缀 | 用途 | 示例 |
| --- | --- | --- |
| `feat(schedule):` | US-11 功能代码 | `feat(schedule): add read-only schedule overview page` |
| `test(schedule):` | 新增/修改测试 | `test(schedule): lock scheduleInfo contract` |
| `docs(release):` | 发布文档 | `docs(release): add release notes and user manual` |
| `docs(evidence):` | 证据归档 | `docs(evidence): archive sprint3 regression logs` |
| `chore(ci):` | CI/构建脚本 | `chore(ci): record jdk17 test evidence` |
| `fix(schedule):` | 缺陷修复 | `fix(schedule): handle empty scheduleInfo` |

### 7.2 提交前自检

- [ ] 只改自己 owner 的文件；共享文件改动已与 owner 协调；
- [ ] 没有提交 `backend/target/`、日志、截图临时文件、本机配置；
- [ ] 没有写入任何数据库口令、Token、密钥；
- [ ] 新增/修改测试已本地执行；
- [ ] commit message 含 `US-11` 或任务编号；
- [ ] PR 描述含：范围、改动文件、测试命令与结果、证据路径、风险、发布边界。

---

## 8. 代码审查与合入规则

### 8.1 审查清单（每个 PR 必查）

| 检查项 | 审查要点 | 不通过示例 |
| --- | --- | --- |
| 权限 | 页面路由角色与后端 `offering:read` 一致；未新增权限码 | 菜单对无权限角色可见但接口 403 |
| 数据范围 | 教师/主任/督导范围由后端保证；前端不传“万能参数”绕过 | 前端传 `teacherId` 空值导致越权 |
| 只读 | 页面不调用 POST/PUT/DELETE；无写库代码 | “顺手”加一个保存按钮 |
| 空值 | `scheduleInfo` 为空显示“未填写”；课程/教师关联为空不崩 | `undefined` 直接拼字符串 |
| 契约 | 复用接口的字段名、分页结构、错误码与 `docs/API契约.md` 一致 | 自造 `schedule` 字段 |
| 测试 | 新增测试能在 H2 下跑；不依赖执行顺序 | 测试之间共享可变数据 |
| 数据库 | 无 schema/data 改动；无新表/新字段 | 私自加 `schedule` 表 |
| 依赖 | 无新运行时依赖；无版本升级 | 为画图引入新前端库 |

### 8.2 合入流程

1. owner 在 feature 分支完成代码与测试；
2. push 后开 PR，贴测试/冒烟/证据；
3. 非作者审查（杨继鑫/刘鹏杰/余润东按矩阵）+ ChatGPT 辅助审查；
4. 作者按意见修复并复测；
5. DRI 终检：108+ 测试、56+ 冒烟、E2E、文档、无密钥；
6. DRI 合并到 `main`；
7. 发布时在通过 12 项清单的提交上打 `v1.0.0-sprint3` annotated tag。

**一票否决**：测试失败、发现写库、越权、改表、泄露口令、Release Notes 写未实现功能。

---

## 9. AI 编码边界与人工检查点

| AI 智能体 | 允许做 | 禁止做 | 人工检查点 |
| --- | --- | --- | --- |
| 编码智能体（DeepSeek） | 生成 `schedule.js`/路由/测试骨架；按审查意见修复 | 改权限矩阵、改表、加接口、改 `pom.xml`、伪造数据 | 杨继鑫逐行审 diff；重点看只读、空值、角色、字段名 |
| 测试/回归智能体（豆包） | 生成冒烟检查、回归用例、边界场景 | 为了让测试通过而放宽断言；改基线数字 | 刘鹏杰对照种子数据逐条验证 |
| 代码审查智能体（ChatGPT） | 审查 diff、列出跨模块影响与风险 | 代替人类批准合入 | 人类审查人确认每条意见采纳/否决 |
| 发布/质量门禁智能体（Kimi） | 汇总 12 项清单证据、标出缺失项 | 代替 DRI 签字；把“未运行”写成“通过” | 刘润七逐项核对证据路径并签字 |
| 文档智能体 | 起草 Release Notes/用户手册初稿 | 写未实现功能；编造截图/数据 | 余润东对照真实页面与接口逐条审定 |

**AI 记录**：每次使用都追加到 `16组_Sprint3分工规划.md` 表 5；记录时间、角色、任务、输入摘要、输出要点、团队处置；闭会前 DRI 确认，严禁事后补造。

---

## 10. 共享热点文件与冲突处理

| 文件 | 热点原因 | 唯一写 Owner | 冲突处理 |
| --- | --- | --- | --- |
| `frontend/assets/js/router.js` | 多人可能加路由/改菜单 | 杨继鑫 | 其他人只提建议；后合入分支 rebase 后重跑页面 |
| `frontend/index.html` | 脚本引入 + 品牌文案 | 杨继鑫 | 余润东提供文案，杨继鑫落盘 |
| `README.md` | 多人可能更新命令/状态 | 余润东 | 杨继鑫只审命令，不直接改 |
| `docs/测试报告.md` | 数字多、易冲突 | 刘鹏杰 | 其他人只提供数据，不直接改 |
| `docs/API契约.md` | 接口说明 | 杨继鑫 | 余润东审业务口径，不直接改 |
| `16组_Sprint3分工规划.md` | 会议记录持续追加 | 刘润七 | 表 5 只追加；其他人不覆盖历史行 |

**冲突解决顺序**：先由后合入分支 owner rebase；无法达成一致时由 DRI 在站会裁定；涉及发布范围的变更必须回到 PO 重新确认。

---

## 11. 代码级完成定义（DoD）

- [ ] `pages/schedule.js` 可加载，无 console 报错；
- [ ] `/schedule` 路由对 ADMIN/DIRECTOR/COURSE_ADMIN/SUPERVISOR 可见，教师按 `offering:read` 可见本人数据；
- [ ] 学期/教师/关键词筛选可用；空态明确；
- [ ] 列表展示课程、教师、班级、学期、上课时间地点、开课状态；
- [ ] `scheduleInfo` 为空显示“未填写”；
- [ ] 页面无 POST/PUT/DELETE 调用；
- [ ] 未改表、未新增接口、未新增运行时依赖；
- [ ] 108 个既有测试 + 新增测试全绿；
- [ ] 56 + 4 项冒烟通过；
- [ ] 主旅程/权限旅程/异常旅程证据归档；
- [ ] Release Notes/用户手册/README 与实际功能一致；
- [ ] 12 项 Release Checklist 逐项签字；
- [ ] 代码审查记录、AI 记录、技术债台账完整；
- [ ] 在通过检查的提交上打 `v1.0.0-sprint3` Tag。

---

## 12. 每人最终交付清单

| 成员 | 代码 | 测试/证据 | 文档 |
| --- | --- | --- | --- |
| 杨继鑫 | `pages/schedule.js`、`router.js`、`index.html`、`OfferingAndDataIT` 新测试、`docs/API契约.md` | 109+ 测试日志、CI/本地等价日志 | `docs/技术债台账.md` |
| 余润东 | 无强制代码；提供 `index.html` 文案 | 主旅程截图/录屏 | `docs/发布说明.md`、`docs/用户手册.md`、`README.md`、Sprint 评审纪要 |
| 刘鹏杰 | `docs/smoke_test.py` 新增 4 项 | 56+4 冒烟日志、108+ 回归日志、权限/异常旅程证据 | `docs/测试报告.md`、Sprint 回顾纪要、T-07 数据 |
| 刘润七 | 无业务代码；审查 diff | 12 项清单、AI 记录、Tag、8 项交付物核对 | `16组_Sprint3分工规划.md/.docx`、代码分工说明、发布边界、自评统稿、DRI 寄语 |
