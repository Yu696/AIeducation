# 爱教学 Sprint 1 / Sprint 2 · 接口契约（冻结点：计划会开场 10 分钟）

> 本文件是 Sprint 1 的**接口冻结基线**。Sprint 2 在 v1.1 基础上增量补充：
> ① US-07 教学资源挂接与维护闭环（不改变 Sprint 1 已冻结接口的入参、出参与权限码）；
> ② US-08 备课查看统计字段；
> ③ US-09 评教分析（趋势/教师汇总/分析导出）；
> ④ US-10 听评课状态机、督导候选教师接口与待督导清单状态字段。
> 前后端、AI 编码智能体一律以此为准，禁止无契约并行开发（见表7改进措施第2条）。

## 0. 通用约定

- 服务地址：`http://localhost:8080`，所有接口前缀 `/api`
- 认证：登录后返回 `token`，后续请求头携带 `Authorization: Bearer <token>`
- 请求/响应：`application/json;charset=UTF-8`（导出接口为 `text/csv`）
- 统一响应包：

```json
{ "code": 0, "message": "ok", "data": {} }
```

| code | 含义 |
| --- | --- |
| 0 | 成功 |
| 400 | 参数校验失败 |
| 401 | 未登录或 token 失效 |
| 403 | 无权限（越权访问，US-01 验收标准③） |
| 404 | 资源不存在 |
| 409 | 业务冲突（如课程编号重复） |
| 500 | 服务器内部错误 |

分页响应 `data`：

```json
{ "total": 42, "page": 1, "size": 10, "records": [] }
```

## 1. 角色与账号（US-01）

| 角色码 | 角色名 | 数据可见范围 |
| --- | --- | --- |
| `ADMIN` | 系统管理员 | 全量 |
| `DIRECTOR` | 教研室主任 | 本教研室（是否跨院由 `sys_config.cross_department` 控制） |
| `TEACHER` | 教师 | 仅本人课程与本人资源 |
| `SUPERVISOR` | 教学督导 | 全校开课与督导数据（只读） |
| `COURSE_ADMIN` | 课程管理员 | 课程库与开课（只读） |

**演示账号**（密码统一 `123456`）：`admin` / `director` / `teacher1` / `teacher2` / `supervisor` / `courseadmin`

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | 匿名 | 入参 `{username,password}`；出参 `{token,user}` |
| POST | `/api/auth/logout` | 登录 | 注销 |
| GET | `/api/auth/me` | 登录 | 当前用户、角色、可见范围、权限码清单 |
| GET | `/api/admin/users` | `user:read` | 分页查询，支持 `keyword/roleCode/enabled/page/size` |
| POST | `/api/admin/users` | `user:write` | 新建用户 |
| PUT | `/api/admin/users/{id}` | `user:write` | 修改用户（含启停用、改角色） |
| PUT | `/api/admin/users/{id}/password` | `user:write` | 重置密码 `{password}` |
| DELETE | `/api/admin/users/{id}` | `user:write` | 删除用户 |
| GET | `/api/admin/roles` | `role:read` | 角色清单（含账号数） |
| GET | `/api/admin/roles/{roleCode}/permissions` | `role:read` | 该角色权限码清单 |
| PUT | `/api/admin/roles/{roleCode}/permissions` | `role:write` | 保存角色权限 `{permissions:[...]}` |
| GET | `/api/admin/permissions` | `role:read` | 全部权限码（用于权限矩阵勾选框） |
| GET | `/api/admin/audit-logs` | `audit:read` | 操作日志，支持 `operator/action/targetType/page/size` |

## 2. 课程信息管理（US-02 / US-05，史诗一）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/courses` | `course:read` | 分页；筛选 `keyword/departmentCode/courseType/status/page/size` |
| GET | `/api/courses/{id}` | `course:read` | 详情，`data` 为 `{course, offerings, resources}`：课程对象、教学班列表、**已挂接资源列表**（仅 `associated=1`） |
| POST | `/api/courses` | `course:write` | 新建；`courseCode` 唯一，重复返回 409 |
| PUT | `/api/courses/{id}` | `course:write` | 修改 |
| DELETE | `/api/courses/{id}` | `course:write` | 删除；已开课则返回 409 |
| GET | `/api/courses/{id}/offerings` | `offering:read` | 该课程的全部开课记录 |
| GET | `/api/courses/{id}/resources` | `course:read` | **US-07 新增**：该课程已挂接资源列表（数组，仅 `associated=1`）。与课程详情同权限码，避免 `COURSE_ADMIN` 访问课程详情时 403 |
| POST | `/api/courses/{id}/resources` | `resource:write` | 挂接资源 `{resourceId}`；幂等，重复挂接返回 200 且不新增行 |
| DELETE | `/api/courses/{id}/resources/{resourceId}` | `resource:write` | 解除挂接（软解绑，`associated` 置 0）；已解绑再次解绑返回 200 |
| GET | `/api/courses/export` | `course:read` | 导出 CSV |
| POST | `/api/courses/import` | `course:write` | 批量导入 CSV（US-05 数据导入） |

**US-07 挂接 / 解绑语义**

| 场景 | 行为 |
| --- | --- |
| 首次挂接 | `course_resource` 新增一行，`associated=1`，返回 200 |
| 重复挂接（已 `associated=1`） | 不新增行，返回 200（**幂等**） |
| 解绑后重新挂接（已有 `associated=0` 行） | 复用原行并将 `associated` 置 1，不新增行 |
| 解除挂接 | 保留行、`associated=0`（**软解绑**，保留历史可追溯） |
| 对已解绑记录再次解绑 | 返回 200（幂等） |
| 从未挂接过该资源 | 返回 404「该课程未挂接此资源」 |
| 缺少 `resourceId` | 返回 400「资源ID不能为空」 |
| 课程或资源不存在 | 返回 404 |
| TEACHER 越权 | 课程不属于本人，或资源不在本人可见范围 → 403 |
| 审计 | 挂接 / 解绑写入 `sys_audit_log`，`target_type=COURSE_RESOURCE`，`target_id=课程ID`，`detail` 含资源ID |

**备课查看口径**：`GET /api/resources/prep` 按资源自身 `offering.course_id` 分组。资源须满足：`status='有效'`，且其所属教学班课程存在 `associated=1` 的挂接；`offering_id IS NULL` 的院系级公共资源保持原有行为（归入“未关联课程”）。解绑后该资源不再出现在备课视图。

## 3. 开课管理（US-02 / US-04，史诗一）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/offerings` | `offering:read` | 分页；筛选 `keyword/semester/teacherId/departmentCode/offeringStatus/offered/supervised/page/size` |
| GET | `/api/offerings/{id}` | `offering:read` | 详情（含授课教师、学生人次、督导状态） |
| POST | `/api/offerings` | `offering:write` | 新建开课 |
| PUT | `/api/offerings/{id}` | `offering:write` | 修改（学期/教师/班级/人数/状态） |
| DELETE | `/api/offerings/{id}` | `offering:write` | 删除 |
| GET | `/api/offerings/{id}/student-counts` | `offering:read` | 该教学班学生人次明细 |
| POST | `/api/offerings/{id}/student-counts` | `offering:write` | 新增/更新人数记录 |
| GET | `/api/offerings/export` | `offering:read` | 导出 CSV |

## 4. 教师信息管理（US-02，史诗一）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/teachers` | `teacher:read` | 分页；筛选 `keyword/departmentCode/title/page/size` |
| GET | `/api/teachers/{id}` | `teacher:read` | 详情（含课程简介与开课情况，满足故事原文"查看5位教师的课程简介与开课情况"） |
| POST | `/api/teachers` | `teacher:write` | 新建 |
| PUT | `/api/teachers/{id}` | `teacher:write` | 修改 |
| DELETE | `/api/teachers/{id}` | `teacher:write` | 删除；有在授课程则 409 |
| GET | `/api/teachers/{id}/offerings` | `teacher:read` | 该教师的开课清单 |
| GET | `/api/teachers/export` | `teacher:read` | 导出 CSV |

## 5. 教学资源管理（US-03，史诗二）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/resources` | `resource:read` | 分页；教师角色自动强制 `teacherId=自己`；筛选 `keyword/resourceType/status/offeringId/page/size` |
| GET | `/api/resources/{id}` | `resource:read` | 详情；TEACHER 仅可查看本人资源或本人任课/合上教学班的资源（否则 403） |
| POST | `/api/resources` | `resource:write` | 新建/上传资源；字段名 `fileUrl`（资源地址）、`resourceType`、`offeringId`、`description`；TEACHER 的 `teacherId` 由后端强制为本人 |
| PUT | `/api/resources/{id}` | `resource:write` | 修改；TEACHER 仅可修改**本人上传**的资源，`teacherId` 冻结不可转移 |
| DELETE | `/api/resources/{id}` | `resource:write` | 删除；TEACHER 仅可删除**本人上传**的资源 |
| GET | `/api/resources/prep` | `resource:prep` | **备课查看**：按教师聚合"我教的课 → 可用资源"列表；已解绑资源不再出现 |
| GET | `/api/resources/export` | `resource:read` | 导出 CSV |

**资源字段与枚举（与 `database/数据字典.md` 一致）**

| 字段 | 说明 |
| --- | --- |
| `fileUrl` | 资源地址 URL，新建必填，最长 512 字符 |
| `resourceType` | `课件 / 教案 / 视频 / 习题 / 参考文献` |
| `status` | `有效 / 待审核 / 失效`；备课查看仅取 `有效` |
| `className` | 资源列表/详情返回其关联教学班名称（`offering.class_name`），未关联教学班时为 `null` |

**备课查看返回结构（Sprint 2 v1.2 增强）**：

- `groups[]` 增加 `offeringCount`、`classNames[]`、`teacherNames[]`、`resourceCount`、`missingResources`；
- 正常开课但暂无资源的课程也会出现在 `groups[]` 中，`resourceCount=0`、`missingResources=true`；
- 顶层增加 `courseCount`、`coursesWithoutResources`、`averageResourcesPerCourse`、`resourceTypeStats[]`。

## 6. 教学评价管理（史诗三，只读展示）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/evaluations` | `evaluation:read` | 分页；教师角色强制只看本人；筛选 `offeringId/evaluationType/semester/scoreMin/scoreMax/page/size` |
| GET | `/api/evaluations/summary` | `evaluation:read` | **评教分析**：筛选 `semester/courseId/teacherId/evaluationType`；返回课程汇总、学期趋势、教师汇总、分数分布；不传类型默认学生评教，`evaluationType=全部` 统计全部类型 |
| GET | `/api/evaluations/summary/export` | `evaluation:read` | 评教分析 CSV：按课程/学期/教师三个维度输出，筛选参数与 summary 一致 |
| GET | `/api/evaluations/{id}` | `evaluation:read` | 评价详情 |
| GET | `/api/evaluations/export` | `evaluation:read` | 评价明细 CSV |

**评教分析口径（Sprint 2 v1.3）**：

- 数据范围：TEACHER 仅本人，DIRECTOR 仅本教研室，SUPERVISOR/ADMIN 全校；
- 课程汇总按 `course + semester + teacher` 聚合；学期趋势按 `semester` 聚合；教师汇总按 `teacher` 聚合；
- 三种聚合均直接基于原始 `evaluation.score` 计算，避免“先四舍五入再加权”的误差；
- 趋势与教师汇总均返回 `avgScore`、`evaluationCount`；教师汇总额外返回 `courseCount`；
- 分析导出与 summary 使用同一筛选条件，UTF-8 BOM，中文表头。

## 7. 督导与听评课（US-04）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/supervisions` | `supervision:read` | 分页；筛选 `offeringId/supervisorId/semester/status/page/size` |
| POST | `/api/supervisions` | `supervision:write` | 安排/登记听评课 |
| PUT | `/api/supervisions/{id}` | `supervision:write` | 更新听评课结果 |
| DELETE | `/api/supervisions/{id}` | `supervision:write` | 删除听评课记录 |
| GET | `/api/supervisions/export` | `supervision:read` | 听评课明细导出 CSV（与覆盖率导出的区别：本接口导出明细记录） |
| GET | `/api/supervisions/supervisors` | `supervision:read` | 督导候选教师（`id/name/departmentCode/departmentName`），供教学督导账号安排听评课时选择，不依赖 `user:read` |
| GET | `/api/supervisions/statuses` | `supervision:read` | 状态候选值：待安排/已安排/已完成 |
| GET | `/api/supervision/coverage` | `coverage:read` | **督导覆盖率**：`{totalOfferings, supervisedOfferings, coverageRate, byDepartment[], bySemester[]}` |
| GET | `/api/supervision/coverage/pending` | `coverage:read` | 待督导教学班清单；每条增加 `supervisionStatus`（待安排/已安排），便于页面显示安排状态 |
| GET | `/api/supervision/coverage/export` | `coverage:read` | 覆盖率导出 CSV（按院系 + 按学期两个维度） |

**听评课状态机（Sprint 2 v1.2）**：

- 状态取值：`待安排`、`已安排`、`已完成`；
- 允许流转：`待安排 → 已安排 → 已完成`，只能向前；`已完成` 为终态，非法流转返回 400；
- `已安排` 必须填写 `planDate`（计划听课日期），且不得填写 `finishDate`；
- `已完成` 未填 `finishDate` 时自动补当天；`finishDate` 不得早于 `planDate`；
- 只有 `已完成` 状态可填写 `score`（0—100）；
- 更新接口支持部分字段提交，未提交字段沿用原值。

**状态枚举（与数据库一致，中文）**：`supervision.status ∈ {待安排, 已安排, 已完成}`；
`offering.offering_status ∈ {正常开课, 停开, 已结课}`；
`evaluation.evaluation_type ∈ {学生评教, 督导评教, 同行评议}`。

**覆盖率口径（写入数据字典，US-04 验收标准③）**：
`覆盖率 = 统计周期内存在有效听课记录（supervision.status='已完成'）的「正常开课」教学班数 ÷ 统计周期内「正常开课」的教学班总数 × 100%`，
按教学班（offering）去重计数，同一教学班多次听课只计 1。
**院系归属统一取课程所属院系（course.department_code）**，保证「按学期过滤的总体数字」与「按院系分组数字」可交叉核对。

## 8. 统计与工作台（US-02/03/04）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/dashboard/summary` | `dashboard:read` | 按当前角色返回的统计卡片数据 |
| GET | `/api/dashboard/director` | `dashboard:director` | 主任工作台：课程/开课/教师/资源/覆盖率/评教概览 + 教师授课分布 |
| GET | `/api/dashboard/teacher` | `dashboard:teacher` | 教师工作台：我的课程数、我的教学班数、学生人次、资源数、我的评教均分 |
| GET | `/api/dashboard/supervisor` | `dashboard:supervisor` | 督导工作台：开课清单概览、覆盖率、待安排听评课清单 |
| GET | `/api/dashboard/admin` | `dashboard:admin` | 管理员工作台：账号数、角色分布、操作日志摘要 |
| GET | `/api/config/public` | 匿名 | 前端可读的公共配置（系统名称、当前学期） |

## 9. 前端页面（骨架冻结）

| 页面 | 路由 hash | 可见角色 |
| --- | --- | --- |
| 登录页 | `#/login` | 匿名 |
| 工作台首页 | `#/dashboard` | 全部登录用户（按角色渲染不同卡片） |
| 课程信息管理 | `#/courses` | ADMIN、DIRECTOR、COURSE_ADMIN、SUPERVISOR |
| 开课与授课管理 | `#/offerings` | ADMIN、DIRECTOR、COURSE_ADMIN、SUPERVISOR、TEACHER（仅本人） |
| 教师信息管理 | `#/teachers` | ADMIN、DIRECTOR、COURSE_ADMIN |
| 教学资源管理 | `#/resources` | 全部（TEACHER 仅本人） |
| 备课查看 | `#/prep` | TEACHER、DIRECTOR、ADMIN |
| 教学评价管理 | `#/evaluations` | ADMIN、DIRECTOR、SUPERVISOR、TEACHER（仅本人） |
| 督导与听评课 | `#/supervision` | ADMIN、SUPERVISOR、DIRECTOR |
| 督导覆盖率 | `#/coverage` | ADMIN、SUPERVISOR、DIRECTOR |
| 用户与权限管理 | `#/admin` | ADMIN |

## 10. 变更记录

| 版本 | 时间 | 变更 | 责任人 |
| --- | --- | --- | --- |
| v1.0 | Sprint 1 计划会 | 接口冻结，上述清单为 Sprint 1 交付范围 | 杨继鑫（DRI） |
| v1.1 | Sprint 1 执行期 | ① 补齐听评课明细导出 `/api/supervisions/export` 与状态候选值接口；② 状态枚举统一为中文（`已完成` 等）并写明映射；③ 明确覆盖率院系归属取课程院系 | 杨继鑫（DRI） |
| v1.2 | Sprint 2 计划会/执行期 | ① 听评课状态机与日期/评分校验；② 新增督导候选教师接口 `/api/supervisions/supervisors`；③ 待督导清单返回 `supervisionStatus`；④ 备课查看增加课程数、缺资源标记、平均资源数与资源类型统计 | 刘鹏杰（DRI）、杨继鑫 |
| v2.0 | Sprint 2 执行期 | ① 新增 `GET /api/courses/{id}/resources`（权限 `course:read`）；② 明确 `GET /api/courses/{id}` 返回 `{course, offerings, resources}`；③ 明确挂接幂等、软解绑、重复解绑、404/400/403 语义与审计字段；④ 补资源 `fileUrl`/`className` 字段及前后端统一枚举；⑤ 明确 TEACHER 资源查看/修改/删除的数据范围与备课“解绑后不可见”口径 | 刘润七（US-07 负责人） |
| v1.3 | Sprint 2 执行期 | ① `summary` 增加 `courseId/teacherId/evaluationType` 筛选；② 返回 `trend`、`teacherSummary`、`courseCount`；③ 新增 `/summary/export` 分析导出；④ 明确趋势/教师汇总直接按原始评价记录聚合 | 余润东（US-09 负责人）、刘鹏杰 |
