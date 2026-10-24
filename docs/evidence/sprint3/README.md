# Sprint 3 证据索引（杨继鑫：US-11 / T-02 / T-05）

> 记录时间：2026-10-24
> 责任人：杨继鑫（US-11 只读排课总览、T-02 发布基线与 CI 证据、T-05 技术债台账）

## 1. T-02 发布基线与 CI 证据

| 项 | 内容 |
| --- | --- |
| 操作系统 | Windows 11 Home China |
| JDK | Oracle JDK 17.0.18（发布基线） |
| Maven | 3.9.11 |
| 后端测试命令 | `cd backend && mvn -B -ntp test` |
| 后端测试结果 | **Tests run: 109, Failures: 0, Errors: 0, Skipped: 0**（Sprint 2 基线 108 + US-11 新增 1） |
| 本地等价流水线 | `powershell -ExecutionPolicy Bypass -File scripts/ci.ps1` |
| 本地等价结果 | 后端 109 测试通过 + 前端全部 JS `node --check` 通过，输出 `CI equivalent passed.` |
| 云端 GitHub Actions | **本轮未推送、未运行**；按 `docs/CI说明.md` 口径，只能声明“本地等价流水线通过”，不得声称云端 CI 已通过 |

证据文件：

| 文件 | 内容 |
| --- | --- |
| `t02-jdk17-test.log` | JDK 17 下 `mvn -B -ntp test` 完整输出与汇总 |
| `t02-local-ci.log` | `scripts/ci.ps1` 本地等价流水线完整输出 |
| `t02-backend-startup.log` | 真实 MySQL（localhost:3306）启动日志，用于演示环境复现 |

## 2. US-11 页面验证（真实 MySQL + 无头 Edge）

后端以真实 MySQL 启动（端口 8080），通过无头 Edge 走完登录与页面交互：

| 场景 | 结果 |
| --- | --- |
| ADMIN 登录后访问 `#/schedule` | 页面标题“排课统筹总览”；2 个学期卡片、20 个教师分组、30 行教学班；统计卡（教学班 30 / 课程 24 / 教师 12 / 待补排课信息 0）正确 |
| 关键词无匹配 | 显示空态“该筛选条件下暂无排课数据”，无异常 |
| teacher1 访问 `#/schedule` | 仅 4 个本人（含合上）教学班；无教师筛选下拉；无新建/编辑/删除按钮 |
| 控制台 | 0 条 console error、0 条未捕获异常 |
| 写操作 | 页面无任何 `POST/PUT/DELETE` 调用（只读） |

证据文件：`us11-schedule-page.png`（ADMIN 排课总览页截图）。

## 3. 未覆盖 / 交接说明

- 56 项真实 MySQL 冒烟由刘鹏杰执行与归档（本轮未由本人重复执行）；
- 主旅程截图/录屏由余润东负责；
- `ConfigController` 的 `/api/config/public` 与 `/health` 仍硬编码 `"Sprint 1"`，属 T-04 品牌口径，建议余润东确认后单独修改（不在本轮后端只读范围内）。
