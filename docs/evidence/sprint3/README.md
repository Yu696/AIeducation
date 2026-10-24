# Sprint 3 证据索引（US-11 / T-02 / T-05 / 回归 / 冒烟 / 旅程）

> 记录时间：2026-10-24
> 责任人：杨继鑫（US-11、T-02 发布基线与 CI、T-05 技术债台账）、刘鹏杰（回归、冒烟、权限/异常旅程）
> DRI 终检：刘润七
> 代码基线：`main@1f129b6`（US-11 与回归证据已合入）；US-11 合入前基线为 `main@83aca2d`

## 1. T-02 发布基线与 CI 证据（杨继鑫）

| 项 | 内容 |
| --- | --- |
| 操作系统 | Windows 11 Home China |
| JDK | Oracle JDK 17.0.18（发布基线） |
| Maven | 3.9.11 |
| 后端测试命令 | `cd backend && mvn -B -ntp test` |
| 后端测试结果（US-11 合入后） | **Tests run: 109, Failures: 0, Errors: 0, Skipped: 0**（Sprint 2 基线 108 + US-11 新增 1） |
| 本地等价流水线 | `powershell -ExecutionPolicy Bypass -File scripts/ci.ps1` |
| 本地等价结果 | 后端 109 测试通过 + 前端全部 JS `node --check` 通过，输出 `CI equivalent passed.` |
| 云端 GitHub Actions | **本轮未推送、未运行**；按 `docs/CI说明.md` 口径，只能声明“本地等价流水线通过”，不得声称云端 CI 已通过 |

证据文件：

| 文件 | 内容 |
| --- | --- |
| `t02-jdk17-test.log` | JDK 17 下 `mvn -B -ntp test` 完整输出与汇总（109 项） |
| `t02-local-ci.log` | `scripts/ci.ps1` 本地等价流水线完整输出 |
| `t02-backend-startup.log` | 真实 MySQL（localhost:3306）启动日志，用于演示环境复现 |

## 2. US-11 页面验证（杨继鑫，真实 MySQL + 无头 Edge）

后端以真实 MySQL 启动（端口 8080），通过无头 Edge 走完登录与页面交互：

| 场景 | 结果 |
| --- | --- |
| ADMIN 登录后访问 `#/schedule` | 页面标题“排课统筹总览”；2 个学期卡片、20 个教师分组、30 行教学班；统计卡（教学班 30 / 课程 24 / 教师 12 / 待补排课信息 0）正确 |
| 关键词无匹配 | 显示空态“该筛选条件下暂无排课数据”，无异常 |
| teacher1 访问 `#/schedule` | 仅 4 个本人（含合上）教学班；无教师筛选下拉；无新建/编辑/删除按钮 |
| 控制台 | 0 条 console error、0 条未捕获异常 |
| 写操作 | 页面无任何 `POST/PUT/DELETE` 调用（只读） |

证据文件：`us11-schedule-page.png`（ADMIN 排课总览页截图）。

## 3. 回归、冒烟与权限旅程（刘鹏杰）

### 3.1 证据索引

| 目录 / 文件 | 内容 | 状态 |
| --- | --- | --- |
| `regression/summary.md` | 108 项全量回归结论、环境、分测试类明细（US-11 合入前基线） | 已完成 |
| `regression/jdk17-mvn-test.log` | JDK 17 发布基线原始 Maven 日志（108 项） | 已完成 |
| `regression/jdk17-mvn-test-after-us11.log` | **US-11 合入后 JDK 17 全量回归（109 项）** | 已完成 |
| `regression/jdk21-supplemental-mvn-test.log` | JDK 21 补充运行日志（US-11 合入前，108 项） | 已完成 |
| `smoke/summary.md` | 56 + 4 = 60 项真实 MySQL 冒烟结论与 SM-01~04 明细 | 已完成 |
| `smoke/smoke-test.log` | 冒烟脚本原始输出（US-11 合入前，60 PASS / 0 FAIL） | 已完成 |
| `smoke/smoke-test-after-us11.log` | **US-11 合入后真实 MySQL 冒烟复跑（60 PASS / 0 FAIL）** | 已完成 |
| `journey/collect_journey.py` | 权限/异常旅程接口证据采集脚本（只读） | 已完成 |
| `journey/permission-anomaly-journey.md` | 教师/主任/督导/未登录/越权与空态接口响应 | 已完成（接口级） |
| `journey/capture_ui_journey.js` | 页面旅程截图脚本（Playwright + 系统 Chrome/Edge） | 已完成 |
| `journey/ui-journey-evidence.md` | 8 个页面场景：教师/督导/主任/管理员、未登录、空态、未填写、后端不可用 | 已完成（页面级） |
| `journey/screenshots/*.png` | 8 张页面截图 | 已完成 |
| `metrics/retrospective-data.md` | T-07 三轮速率、燃起图、技术债、改进闭环数据 | 已完成 |
| `metrics/make_metrics_charts.py` | 度量图表生成脚本 | 已完成 |
| `metrics/sprint-velocity.png`、`metrics/cumulative-burnup.png` | 三轮吞吐与累计燃起图 | 已完成 |
| `release-checklist.md` | 12 项发布检查清单 | 已归档（刘润七确认） |

### 3.2 复现方式

```bash
# 全量回归（JDK 17）
cd backend && mvn -B -ntp test

# 真实 MySQL 冒烟（先启动后端）
SMOKE_BASE_URL=http://127.0.0.1:8081 python docs/smoke_test.py

# 权限/异常旅程接口证据（先启动后端）
SMOKE_BASE_URL=http://127.0.0.1:8081 python docs/evidence/sprint3/journey/collect_journey.py

# 页面旅程截图（需先 npm install playwright-core，并可用 CHROME_PATH 指定浏览器）
NODE_PATH=<node_modules 路径> SMOKE_BASE_URL=http://127.0.0.1:8081 \
  node docs/evidence/sprint3/journey/capture_ui_journey.js

# T-07 图表
python docs/evidence/sprint3/metrics/make_metrics_charts.py
```

> Windows 下设置环境变量：`set SMOKE_BASE_URL=http://127.0.0.1:8081`。

### 3.3 结论摘要

| 门禁项 | 结果 | 证据 |
| --- | --- | --- |
| 108 项自动化回归（US-11 合入前） | 108 / 108 通过 | `regression/jdk17-mvn-test.log` |
| 109 项自动化回归（US-11 合入后） | 109 / 109 通过 | `regression/jdk17-mvn-test-after-us11.log`、`t02-jdk17-test.log` |
| 56 项既有冒烟 | 56 / 56 通过 | `smoke/smoke-test-after-us11.log` |
| 新增排课总览冒烟 | 4 / 4 通过 | `smoke/smoke-test-after-us11.log` |
| 权限旅程（接口级） | 通过 | `journey/permission-anomaly-journey.md` |
| 异常旅程（接口级） | 空学期、`scheduleInfo` 字段通过 | `journey/permission-anomaly-journey.md` |
| 页面权限/异常旅程 | 8 / 8 通过 | `journey/ui-journey-evidence.md` |
| T-07 项目度量 | 数据完成 | `metrics/retrospective-data.md` |

## 4. 已知问题与证据状态

| 编号 | 类型 | 说明 | 处置 |
| --- | --- | --- | --- |
| BUG-S3-01 | 一般缺陷 | 合上教师（`offering_teacher`）在开课列表中可见，但打开该教学班详情返回 403；`assertOfferingAccessible` 仅比较 `offering.teacher_id` | 本轮后端只读，登记到 `docs/测试报告.md` 遗留缺陷，不影响 US-11 总览列表；建议 Sprint 4 修复 |
| E-S3-01 | 已补齐 | `/schedule` 页面截图/录屏依赖 `feature/sprint3-schedule-overview` 合入 | `us11-schedule-page.png`（杨继鑫）+ `journey/screenshots/`（刘鹏杰 8 张） |
| E-S3-02 | 已补齐 | “`scheduleInfo` 为空显示未填写”需页面构造空值场景 | `journey/screenshots/empty-schedule-info.png`（路由拦截模拟空值，不改数据库） |
| E-S3-03 | 已补齐（截图） | 主旅程（主任登录→排课总览→筛选→返回）截图/录屏 | 已归档截图：`journey/screenshots/P-08-director-schedule.png`、`us11-schedule-page.png`；录屏见成果展演 |

## 5. 未覆盖 / 交接说明

- 60 项真实 MySQL 冒烟由刘鹏杰执行与归档（US-11 合入后已复跑）；
- 主旅程页面截图已归档（`P-08-director-schedule.png`、`us11-schedule-page.png`），录屏见成果展演；权限/异常旅程页面证据由刘鹏杰完成；
- `release-checklist.md` 与 `v1.0.0-sprint3` Tag 已由刘润七归档与创建；
- `ConfigController` 的 `/api/config/public` 与 `/health` 仍硬编码 `"Sprint 1"`，属 T-04 品牌口径，建议余润东确认后单独修改（不在本轮后端只读范围内）。

## 6. 环境备注（刘鹏杰采集环境）

- 本机 8080 被其他项目占用，冒烟临时使用 8081；已为 `smoke_test.py` 增加 `SMOKE_BASE_URL` 支持，默认行为仍为 8080。
- 本机 Maven 离线仓库无 `maven-jar-plugin`，无法 `mvn package`；回归与冒烟分别通过 `mvn test` 与 `spring-boot:run` 完成，不影响发布门禁结论。
- 冒烟为只读检查，未调用任何 POST/PUT/DELETE 业务接口；旅程脚本中的写请求仅用于验证 403 拦截，不落库。
