# Sprint 3 证据目录（刘鹏杰 / 质量与集成）

> 分支：`feature/sprint3-regression-evidence`
> 代码基线：`main@83aca2d`（后端业务代码零改动）
> 归档日期：2026-10-24
> 审查人：杨继鑫（回归/冒烟）　·　DRI 终检：刘润七

## 1. 证据索引

| 目录 / 文件 | 内容 | 状态 |
| --- | --- | --- |
| `regression/summary.md` | 108 项全量回归结论、环境、分测试类明细 | 已完成 |
| `regression/jdk17-mvn-test.log` | JDK 17 发布基线原始 Maven 日志 | 已完成 |
| `regression/jdk21-supplemental-mvn-test.log` | JDK 21 补充运行日志（结果一致） | 已完成 |
| `smoke/summary.md` | 56 + 4 = 60 项真实 MySQL 冒烟结论与 SM-01~04 明细 | 已完成 |
| `smoke/smoke-test.log` | 冒烟脚本原始输出（60 PASS / 0 FAIL） | 已完成 |
| `journey/collect_journey.py` | 权限/异常旅程接口证据采集脚本（只读） | 已完成 |
| `journey/permission-anomaly-journey.md` | 教师/主任/督导/未登录/越权与空态接口响应 | 已完成（接口级） |
| `release-checklist.md` | 12 项发布检查清单 | 待刘润七归档（本分支不代写） |

## 2. 复现方式

```bash
# 全量回归（JDK 17）
cd backend && mvn -B -ntp test

# 真实 MySQL 冒烟（先启动后端）
SMOKE_BASE_URL=http://127.0.0.1:8081 python docs/smoke_test.py

# 权限/异常旅程接口证据（先启动后端）
SMOKE_BASE_URL=http://127.0.0.1:8081 python docs/evidence/sprint3/journey/collect_journey.py
```

> Windows 下设置环境变量：`set SMOKE_BASE_URL=http://127.0.0.1:8081`。

## 3. 结论摘要

| 门禁项 | 结果 | 证据 |
| --- | --- | --- |
| 108 项自动化回归 | 108 / 108 通过 | `regression/` |
| 56 项既有冒烟 | 56 / 56 通过 | `smoke/smoke-test.log` |
| 新增排课总览冒烟 | 4 / 4 通过 | `smoke/smoke-test.log` |
| 权限旅程（接口级） | 通过 | `journey/permission-anomaly-journey.md` |
| 异常旅程（接口级） | 空学期、`scheduleInfo` 字段通过 | `journey/permission-anomaly-journey.md` |

## 4. 已知问题与待补证据

| 编号 | 类型 | 说明 | 处置 |
| --- | --- | --- | --- |
| BUG-S3-01 | 一般缺陷 | 合上教师（`offering_teacher`）在开课列表中可见，但打开该教学班详情返回 403；`assertOfferingAccessible` 仅比较 `offering.teacher_id` | 本轮后端只读，登记到 `docs/测试报告.md` 遗留缺陷，不影响 US-11 总览列表；建议 Sprint 4 修复 |
| E-S3-01 | 待补证据 | `/schedule` 页面截图/录屏依赖 `feature/sprint3-schedule-overview` 合入 | 分支 1 合入后由刘鹏杰补拍：主旅程、空态、后端不可用提示 |
| E-S3-02 | 待补证据 | “`scheduleInfo` 为空显示未填写”需页面构造空值场景 | 页面合入后补截图 |

## 5. 环境备注

- 本机 8080 被其他项目占用，冒烟临时使用 8081；已为 `smoke_test.py` 增加 `SMOKE_BASE_URL` 支持，默认行为仍为 8080。
- 本机 Maven 离线仓库无 `maven-jar-plugin`，无法 `mvn package`；回归与冒烟分别通过 `mvn test` 与 `spring-boot:run` 完成，不影响发布门禁结论。
- 冒烟为只读检查，未调用任何 POST/PUT/DELETE 业务接口；旅程脚本中的写请求仅用于验证 403 拦截，不落库。
