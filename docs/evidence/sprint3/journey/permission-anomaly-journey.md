# Sprint 3 权限 / 异常旅程接口证据

> 执行时间：2026-10-24 16:06:43 
> 执行人：刘鹏杰（T-03 权限/异常旅程）
> 后端：http://127.0.0.1:8081；数据库：真实 MySQL `aijiaoxue`
> 脚本：`docs/evidence/sprint3/journey/collect_journey.py`（只读采集）

说明：本文件保存接口级真实响应；页面截图依赖 `feature/sprint3-schedule-overview`
合入后的 `/schedule` 页面，合入后由刘鹏杰补拍并追加到本目录。

## 一、权限旅程

### P-01 未登录访问受保护接口

| 项 | 内容 |
| --- | --- |
| 角色 | 未登录 |
| 请求 | `GET /api/courses` |
| HTTP | 401 |
| 结论 | **通过** |
| 判定依据 | 期望 401，实际 401 |

响应摘录：

```json
{"code":401,"message":"未登录或登录已失效，请重新登录"}
```

### P-02 教师登录并获取令牌

| 项 | 内容 |
| --- | --- |
| 角色 | teacher1 |
| 请求 | `POST /api/auth/login` |
| HTTP | 200 |
| 结论 | **通过** |
| 判定依据 | 期望 200 且 roleCode=TEACHER，实际 roleCode=TEACHER |

响应摘录：

```json
{"code": 0, "message": "ok", "data": {"token": "***REDACTED***", "user": {"id": 3, "username": "teacher1", "realName": "张伟", "roleCode": "TEACHER", "roleName": "教师", "teacherId": 1, "teacherName": "张伟", "departmentCode": "CS", "departmentName": "计算机学院", "dataScope": "SELF", "lastLoginTime": "2026-10-24T16:06:44"}, "permissions": ["course:read", "dashboard:read", "dashb
```

### P-03 教师只看到本人教学班（含合上）

| 项 | 内容 |
| --- | --- |
| 角色 | teacher1 |
| 请求 | `GET /api/offerings?size=200` |
| HTTP | 200 |
| 结论 | **通过** |
| 判定依据 | 期望 ['CS101-2024-2025-2-01', 'CS101-2025-2026-1-01', 'CS102-2025-2026-1-01', 'CS202-2025-2026-1-01']；实际 ['CS101-2024-2025-2-01', 'CS101-2025-2026-1-01', 'CS102-2025-2026-1-01', 'CS202-2025-2026-1-01']；合上记录 CS202 任课关系含张伟=True |

响应摘录：

```json
{"total": 4, "offeringCodes": ["CS101-2024-2025-2-01", "CS101-2025-2026-1-01", "CS102-2025-2026-1-01", "CS202-2025-2026-1-01"]}
```

CS202 合上身份核验（管理员读取详情接口 `teachers`）：

```json
{"http": 200, "teachers": [{"teacher_name": "李娜", "role_type": "主讲"}, {"teacher_name": "张伟", "role_type": "辅导"}]}
```

### P-03b 教师打开合上教学班详情

| 项 | 内容 |
| --- | --- |
| 角色 | teacher1 |
| 请求 | `GET /api/offerings/4` |
| HTTP | 403 |
| 结论 | **已知缺陷** |
| 判定依据 | 合上教学班在列表中可见，详情应返回 200；实际 403（BUG-S3-01） |

响应摘录：

```json
{"code":403,"message":"教师只能访问本人所授教学班的数据"}
```

### P-04 教师按 ID 枚举他人教学班

| 项 | 内容 |
| --- | --- |
| 角色 | teacher1 |
| 请求 | `GET /api/offerings/3` |
| HTTP | 403 |
| 结论 | **通过** |
| 判定依据 | 期望 403（教学班 3 主讲为李娜），实际 403 |

响应摘录：

```json
{"code":403,"message":"教师只能访问本人所授教学班的数据"}
```

### P-05 教师访问用户管理

| 项 | 内容 |
| --- | --- |
| 角色 | teacher1 |
| 请求 | `GET /api/admin/users` |
| HTTP | 403 |
| 结论 | **通过** |
| 判定依据 | 期望 403，实际 403 |

响应摘录：

```json
{"code":403,"message":"无权限访问该功能（越权访问已拦截）"}
```

### P-06 督导全校只读查看开课总览

| 项 | 内容 |
| --- | --- |
| 角色 | supervisor |
| 请求 | `GET /api/offerings?size=200` |
| HTTP | 200 |
| 结论 | **通过** |
| 判定依据 | 期望 200 且 total=30（全校 30 条种子开课），实际 total=30 |

响应摘录：

```json
{"total": 30, "first": {"id": 1, "offeringCode": "CS101-2025-2026-1-01", "courseId": 1, "courseCode": "CS101", "courseName": "程序设计基础", "courseType": "必修", "semester": "2025-2026-1", "className": "计算机2301班", "teacherId": 1, "teacherName": "张伟", "departmentCode": "CS", "departmentName": "计算机学院", "capacity": 40, "studentCount": 37, "offeringStatus": "正常开课", "scheduleInfo": "周一3-4节 教一101", "supervised": 0, "supervisionTimes": 0, "supervisionStatus": "已安排"}}
```

### P-07 督导写开课被拦截（只读约束）

| 项 | 内容 |
| --- | --- |
| 角色 | supervisor |
| 请求 | `POST /api/offerings` |
| HTTP | 403 |
| 结论 | **通过** |
| 判定依据 | 期望 403，实际 403；若通过则存在越权写风险 |

响应摘录：

```json
{"code":403,"message":"无权限访问该功能（越权访问已拦截）"}
```

### P-08 主任数据范围收窄（仅本院系）

| 项 | 内容 |
| --- | --- |
| 角色 | director |
| 请求 | `GET /api/offerings?size=200` |
| HTTP | 200 |
| 结论 | **通过** |
| 判定依据 | 期望所有记录 departmentCode=MATH，实际 ['MATH'] |

响应摘录：

```json
{"total": 6, "departments": ["MATH"], "offeringCodes": ["MATH101-2025-2026-1-01", "MATH102-2025-2026-1-01", "MATH201-2024-2025-2-01", "MATH201-2025-2026-1-01", "MATH202-2024-2025-2-01", "MATH202-2025-2026-1-01"]}
```

## 二、异常旅程（接口级）

### A-01 学期无数据返回空列表（前端空态数据源）

| 项 | 内容 |
| --- | --- |
| 角色 | admin |
| 请求 | `GET /api/offerings?semester=1900-1901&size=200` |
| HTTP | 200 |
| 结论 | **通过** |
| 判定依据 | 期望 200、total=0、records=[] |

响应摘录：

```json
{"total": 0, "records": []}
```

### A-02 scheduleInfo 字段完整性与空值口径

| 项 | 内容 |
| --- | --- |
| 角色 | admin |
| 请求 | `GET /api/offerings?size=200` |
| HTTP | 200 |
| 结论 | **通过** |
| 判定依据 | 期望记录均含 scheduleInfo 且至少 1 条非空；实际字段齐全=True，非空=30/30 |

响应摘录：

```json
[{"offeringCode": "CS101-2025-2026-1-01", "scheduleInfo": "周一3-4节 教一101"}, {"offeringCode": "CS102-2025-2026-1-01", "scheduleInfo": "周二1-2节 教二203"}, {"offeringCode": "CS201-2025-2026-1-01", "scheduleInfo": "周三5-6节 教一305"}]
```

> 种子数据中 `schedule_info` 全部有值（30/30），因此“空值显示未填写”
> 分支需在页面模块 `pages/schedule.js` 合入后用构造数据或截图验证；
> 后端只读契约已由 SM-02 / A-02 锁定为字符串字段。

### A-03 后端不可用时的前端提示（待页面合入后截图）

| 项 | 内容 |
| --- | --- |
| 场景 | 后端停止或网络不可达 |
| 期望 | 页面显示友好错误提示，不出现白屏/未捕获异常 |
| 当前状态 | **待验证**：`/schedule` 页面依赖分支 1 合入 |

## 三、汇总

| 类别 | 数量 | 说明 |
| --- | --- | --- |
| 通过 | 见上表 | 权限与异常主旅程 |
| 已知缺陷 | 1 | BUG-S3-01：合上教学班详情鉴权未覆盖 `offering_teacher` |
| 失败 | 0 | 需要立即修复或阻断发布 |
| 待补证据 | 3 | 页面截图/录屏：主旅程、异常空态、后端不可用提示 |

> 页面截图类证据在 `feature/sprint3-schedule-overview` 合入后补拍；
> 接口级结论已可复现，脚本与原始响应见本目录。

