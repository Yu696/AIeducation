# US-07 人机协作原始记录（对应分工规划表 5）

> 记录人：刘润七
> 记录时间：2026-10-17（实施会话内实时追加，非事后补造）
> 说明：分工规划表 5 的智能体角色按团队计划登记为 DeepSeek / 豆包 / ChatGPT；
> 本次实施会话实际使用的编码、测试、审查工具均为 **Claude Code**，下表按“实际角色 + 实际工具”如实记录。
> 计划中尚未发生的其他智能体会话，由团队在对应会话中继续追加，不预填。

| 序号 | 时间 | AI智能体角色 | 任务场景 | 人类输入（提示词/任务描述摘要） | AI输出要点 | 团队处置（采纳/修改/否决）及理由 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 2026-10-17 会话开始 | 编码/需求分析智能体（Claude Code） | 认领 US-07，梳理刘润七本轮紧急任务 | “阅读分工规划，整理刘润七现在急需要做的事情，分步骤完成，注意功能审核和代码可读性”；并明确“下一阶段当 DRI 负责的部分不在本次任务中” | 从规划中拆出 US-07 + AI 记录/证据链两项职责；对照代码识别 7 个阻塞点：新接口缺失、详情返回体误用、`guard` 越界、`url/fileUrl` 字段错位、枚举不一致、解绑后备课仍可见、教师缺少数据范围校验；给出“基线→契约→后端→测试→前端→证据”实施顺序 | **采纳**。人工确认范围排除：US-08/09/10、T-01、Sprint 3 DRI（排课统筹、统计导出）；不自动 commit |
| 2 | 2026-10-17 契约阶段 | 编码智能体（Claude Code） | 冻结 US-07 接口与数据口径 | 现有关联表结构、Sprint 1 权限矩阵、81 项回归基线 | 新增 `GET /api/courses/{id}/resources`，权限用 `course:read`；明确挂接幂等、软解绑、重复解绑、从未挂接 404；写明 `target_type=COURSE_RESOURCE` 审计口径与备课可见口径 | **采纳**。理由：`COURSE_ADMIN` 没有 `resource:read`，复用 `course:read` 可避免课程详情区 403 的 Sprint 1 回归；契约与数据字典同步更新 |
| 3 | 2026-10-17 后端编码阶段 | 编码智能体（Claude Code） | 后端读路径：课程资源列表、教师可见范围、备课过滤 | 契约 v2、`ResourceMapper` 现有 SQL、备课视图基线（teacher1 为 4 条/3 组） | `VIEW_SQL` 补 `className`；新增 `countVisibleToTeacher`（本人上传或任课/合上教学班）；`selectPrepResources` 增加 `associated=1` 的 `EXISTS` 条件 | **采纳**。修改点：保留 `offering_id IS NULL` 公共资源旧口径，避免扩大行为变更；未改 `selectCoursesOfTeacher`，防止 `courseCount=2` 回归 |
| 4 | 2026-10-17 后端编码阶段 | 编码智能体（Claude Code） | 后端写路径：幂等挂接、软解绑、教师数据范围 | 关联表唯一键 `uk_course_resource`、角色权限码、教师课程/资源范围 | 挂接按“查存在行→插入或置 1”实现幂等，并捕获 `DuplicateKeyException` 兜底并发；解绑置 `associated=0`；资源详情/修改/删除按可见范围与 owner-only 分层；新建资源强制归属本人 | **采纳并修改**。审查发现 `create` 原顺序是先 `validate` 后强制 `teacherId`，教师伪造不存在的 `teacherId` 会得到 404 而非被忽略；已改为**先强制归属、再校验** |
| 5 | 2026-10-17 前端阶段 | 编码智能体（Claude Code） | 课程页资源区、资源管理页字段与筛选 | 现有 `AJ.UI` 组件、`.drawer` z-index=2001、`.modal-mask` z-index=2000 | 课程详情改为调用新接口；模块级 `guardTask` 修复抽屉 `ReferenceError`；`unwrapCourse` 统一解包 `{course,...}`；资源表单改 `fileUrl`；枚举对齐后端；资源页新增关联教学班筛选与 `className` 列 | **采纳并修改**。否决“第二层抽屉”方案（同层叠覆盖、Esc 双关），改为内联挂接面板；候选资源限定为本课程或未关联教学班的公共资源，并排除已挂接项 |
| 6 | 2026-10-17 17:27（见 `test-run.log`） | 测试/回归智能体（Claude Code 同一会话） | US-07 回归用例与 Sprint 1 基线回归 | 验收标准、基线 81 项、软解绑与教师隔离场景 | 新增 13 项用例：幂等挂接/解绑、重挂、审计日志、备课可见性、教师越权、owner-only、强制归属、404/403；最终全量 94 项全绿 | **采纳**。修改点：在 `BaseIntegrationTest` 恢复 `course_resource` 基线（id 1–5 为 1、id 6 为 0），避免非事务写用例污染后续测试类 |
| 7 | 2026-10-17 17:27–17:35 | 代码审查/归档智能体（Claude Code 同一会话） | 增量 diff 审查与证据链归档 | 代码开发纪律 4（跨模块接口、权限码、数据范围、空值、软解绑）、DoD 六条 | 复核跨模块依赖、权限码、空值与软解绑分支；更新 API 契约、数据字典、测试报告；生成 `README.md`、`test-run.log`、`review-checklist.md` | **采纳**。审查发现 `.gitignore` 的 `*.log` 会漏掉证据日志，已增加 `!docs/evidence/**/*.log` 白名单；待人工项（浏览器点检、MySQL 冒烟、DRI 审 diff）记入 `review-checklist.md` |

## 人工介入与待确认

1. 人类明确限定了本次范围，排除下一阶段 DRI 工作与 US-08/09/10、T-01。
2. 人类要求“功能审核和代码可读性”，因此每个新增/变更方法补充中文 Javadoc，
   并让测试覆盖验收标准而不是只覆盖正常路径。
3. 所有 AI 产出仍是候选实现，**未 commit / push**；需由 DRI 刘润七与团队按
   `review-checklist.md` 人工审查 diff 后再合入 main。
