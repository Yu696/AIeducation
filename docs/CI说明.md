# Sprint 2 CI / 回归门禁说明

> 对应实验三升级版 DoD 第⑥条：CI 或本地等价流水线构建通过后方可合入。
> 责任人：杨继鑫（CI 与回归基线）· DRI：刘鹏杰（合入决定）。

## 1. 云端流水线

文件：`.github/workflows/sprint2-ci.yml`

触发条件：

- push 到 `main` 或 `feature/**`；
- 向 `main` 发起 Pull Request。

包含两个 job：

| Job | 内容 | 失败处理 |
| --- | --- | --- |
| `backend-test` | JDK 17 + `mvn -B -ntp test`（H2 测试库，不依赖 MySQL） | 阻断合入；上传 Surefire 报告 |
| `frontend-syntax` | Node 20 + `node --check` 检查全部前端 JS | 阻断合入 |

测试报告在 Actions 页面的 `surefire-reports` artifact 中下载，归档到
`docs/evidence/sprint2/ci/`。

## 2. 本地等价流水线

文件：`scripts/ci.ps1`

```powershell
# 仅后端测试 + 前端语法检查
powershell -ExecutionPolicy Bypass -File scripts/ci.ps1

# 后端已在 localhost:8080 启动，同时跑 56 项冒烟
$env:SMOKE = "1"
powershell -ExecutionPolicy Bypass -File scripts/ci.ps1
```

脚本先尝试 `mvn -o -B test`（离线），失败后自动改为在线 `mvn -B test`；
任一步失败即以非 0 退出码结束。

## 3. 合入门禁

1. 人工审查 diff（重点：跨模块接口、权限码、数据范围、空值与状态机）；
2. `backend-test` 与 `frontend-syntax` 通过，或本地 `scripts/ci.ps1` 通过；
3. 回归矩阵 R-01—R-15 中受影响项通过；
4. DRI 确认升级版 DoD ①—⑤ 后合入 `main`；
5. 合入后立即重跑冒烟，main 必须始终可运行。

> 若云端 Actions 不可用，必须保留本地等价流水线日志
> `docs/evidence/sprint2/ci/offline-run.log`，并标注“本地等价流水线通过”，
> 不得声称云端 CI 已通过。
