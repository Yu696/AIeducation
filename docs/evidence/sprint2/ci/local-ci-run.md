# Sprint 2 本地等价流水线运行记录

> 说明：本文件记录的是合并 `origin/main`（US-07）之前、US-08/US-10/T-01 的 89 项运行结果。
> 合并 US-07 并完成 US-09 后全量用例为 **108 项全绿**，见 `docs/测试报告.md` 与合并提交 `09d2fcd`。

- 运行日期：2026-10-17
- 运行人：刘鹏杰（DRI）
- 代码范围：US-10 听评课状态机、US-08 备课查看增强、T-01 CI 配置

## 1. 后端全量测试

命令：

```powershell
cd backend
mvn -B test
```

结果：

```text
classes: 7
tests:   89
failures: 0
errors:   0
skipped:  0
exit code: 0
```

分类：

| 测试类 | 用例数 | 失败 | 错误 |
| --- | --- | --- | --- |
| AuthAndPermissionIT | 7 | 0 | 0 |
| DataScopeIT | 6 | 0 | 0 |
| PermissionAccessControlIT | 13 | 0 | 0 |
| CourseQueryIT | 12 | 0 | 0 |
| CoverageAndDashboardIT | 20 | 0 | 0 |
| OfferingAndDataIT | 15 | 0 | 0 |
| ResourceAndEvaluationIT | 16 | 0 | 0 |

本次新增回归用例：

- US-10：督导候选教师、待督导清单状态、已安排日期校验、非法状态/日期/评分校验、完整状态流转、已完成回退保护；
- US-08：备课统计字段、无资源课程识别。

## 2. 前端语法检查

命令：

```bash
node --version
# v24.17.0
for f in frontend/assets/js/*.js frontend/assets/js/pages/*.js; do node --check "$f" || exit 1; done
```

结果：`JS_SYNTAX_OK`（全部 JS 文件通过语法检查）。

## 3. 环境说明

- 本地 Maven：3.9.13
- 本地 JDK：21.0.6
- 云端 workflow 使用 JDK 17；测试库为 H2，不依赖本机 MySQL。
- 本地首次执行需联网拉取依赖；依赖就绪后可使用 `mvn -o -B test` 离线执行。

## 4. 本地等价 CI 脚本整体执行

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ci.ps1
```

结果：

```text
==> [1/3] Backend tests (H2)
[INFO] Tests run: 89, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
==> [2/3] Frontend JS syntax
==> [3/3] Smoke test skipped (set SMOKE=1 and start backend to enable).
CI equivalent passed.
exit code: 0
```

> 冒烟测试未执行：本次未启动 MySQL 与后端；在完成真实环境启动后可用
> `$env:SMOKE="1"; powershell -File scripts/ci.ps1` 补跑。

## 5. 结论

本地等价流水线通过。合入 `main` 前仍需：

1. 人工审查 diff；
2. 确认回归矩阵 R-01—R-15 中受影响项通过；
3. DRI 确认升级版 DoD ①—⑤；
4. 合入后重跑冒烟测试。
