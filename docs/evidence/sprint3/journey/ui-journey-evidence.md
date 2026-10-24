# Sprint 3 权限 / 异常旅程页面证据

> 执行时间：2026/10/24 16:50:54
> 执行人：刘鹏杰（T-03 权限/异常旅程）
> 页面地址：http://127.0.0.1:8081/index.html#/schedule
> 浏览器：C:/Program Files/Google/Chrome/Application/chrome.exe（无头模式）
> 脚本：`docs/evidence/sprint3/journey/capture_ui_journey.js`

> 说明：三项异常场景通过 Playwright 路由拦截模拟，未修改数据库或种子数据；
> 所有页面操作均为只读，未点击新增/编辑/删除/保存按钮。

| 编号 | 场景 | 角色 | 预期 | 实测 | 结论 | 截图 |
| --- | --- | --- | --- | --- | --- | --- |
| P-03-teacher-schedule | 教师只看到本人教学班（含合上） | teacher1 | 4 个教学班；无写操作按钮 | 4 行；统计 [{"label":"教学班","value":"4 个"},{"label":"课程","value":"3 门"},{"label":"授课教师","value":"2 人"},{"label":"待补排课信息","value":"0 个"}]；筛选=["关键词","学期"]；写按钮=0 | **通过** | [screenshots/P-03-teacher-schedule.png](screenshots/P-03-teacher-schedule.png) |
| P-06-supervisor-schedule | 督导全校只读 | supervisor | 30 个教学班；无写操作按钮 | 30 行；统计 [{"label":"教学班","value":"30 个"},{"label":"课程","value":"24 门"},{"label":"授课教师","value":"12 人"},{"label":"待补排课信息","value":"0 个"}]；筛选=["关键词","学期","授课教师"]；写按钮=0 | **通过** | [screenshots/P-06-supervisor-schedule.png](screenshots/P-06-supervisor-schedule.png) |
| P-08-director-schedule | 主任仅本院系 | director | 6 个教学班；无写操作按钮 | 6 行；统计 [{"label":"教学班","value":"6 个"},{"label":"课程","value":"4 门"},{"label":"授课教师","value":"3 人"},{"label":"待补排课信息","value":"0 个"}]；筛选=["关键词","学期","授课教师"]；写按钮=0 | **通过** | [screenshots/P-08-director-schedule.png](screenshots/P-08-director-schedule.png) |
| admin-schedule | 管理员排课总览 | admin | 30 个教学班；无写操作按钮 | 30 行；统计 [{"label":"教学班","value":"30 个"},{"label":"课程","value":"24 门"},{"label":"授课教师","value":"12 人"},{"label":"待补排课信息","value":"0 个"}]；筛选=["关键词","学期","授课教师"]；写按钮=0 | **通过** | [screenshots/admin-schedule.png](screenshots/admin-schedule.png) |
| U-01 | 未登录访问 /schedule | 未登录 | 跳转登录页，不展示排课数据 | hash=#/login，登录页可见 | **通过** | [screenshots/unauth-redirect-login.png](screenshots/unauth-redirect-login.png) |
| A-01 | 学期无数据空态 | admin | 显示空态文案，不白屏 | 显示“该筛选条件下暂无排课数据” | **通过** | [screenshots/empty-semester.png](screenshots/empty-semester.png) |
| A-02 | scheduleInfo 为空显示“未填写” | admin | 空值单元格显示“未填写”，统计卡提示待补排课信息 | “未填写”单元格=1；统计={"label":"待补排课信息","value":"1 个"} | **通过** | [screenshots/empty-schedule-info.png](screenshots/empty-schedule-info.png) |
| A-03 | 后端不可用友好提示 | admin | 显示错误提示与“重试”，不白屏 | 错误框：无法连接后端服务，请确认服务已启动（/api）重试 | **通过** | [screenshots/backend-unavailable.png](screenshots/backend-unavailable.png) |

> 教师页面统计显示“授课教师 2 人”，是合上教学班 CS202（主讲李娜、张伟辅导）按主讲教师分组的结果；
> 4 行均为张伟主讲或辅导的教学班，不是越权看到李娜的独立教学班。教师角色不显示“授课教师”筛选下拉。

## 控制台检查

| 场景 | console error / pageerror |
| --- | --- |
| P-03-teacher-schedule | 0 |
| P-06-supervisor-schedule | 0 |
| P-08-director-schedule | 0 |
| admin-schedule | 0 |
| unauth | 0 |
| empty-semester | 0 |
| empty-schedule-info | 0 |
| backend-down | 1：Failed to load resource: net::ERR_FAILED |

> 后端不可用场景会记录一次网络请求失败，属于预期行为；其余场景为 0 条 console error。

