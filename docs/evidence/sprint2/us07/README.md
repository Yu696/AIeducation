# US-07 教学资源挂接与维护闭环 · 证据索引

> 负责人：刘润七（20246162）
> 分支：`feature/us07-resource-link`
> 日期：2026-10-17
> 状态：**实现与自测完成，未 commit / push，待 DRI 与团队人工审查后合入**

## 1. 交付范围

本次只落实 Sprint 2 中刘润七的两项职责：

1. **US-07 主责故事**：资源挂接幂等、软解绑生效、跨页一致、写审计日志、教师数据范围隔离。
2. **AI 协调与记录 / 证据链**：表 5 人机协作原始记录与本目录证据归档。

明确不在本次范围：US-08 备课聚合增强、US-09 评教分析导出、US-10 听评课闭环、T-01 CI，
以及刘润七下一轮 DRI 负责的排课统筹、统计导出等工作。

## 2. 证据文件

| 文件 | 内容 | 可核对信息 |
| --- | --- | --- |
| `README.md` | 证据索引、复现命令、结论边界 | 本文件 |
| `test-run.log` | 最终全量自动化测试原始输出（UTF-8） | `Tests run: 94`、`BUILD SUCCESS`、日志时间戳 |
| `ai-interaction.md` | 本次人机协作记录，对应分工规划表 5 字段 | 智能体角色、输入摘要、输出要点、处置 |
| `review-checklist.md` | DoD 六条自检 + 人工 diff 审查要点 | 逐项结论、待人工确认项 |

## 3. 复现命令

### 3.1 后端全量测试（H2，不依赖 MySQL）

```bash
cd backend
JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8" \
  mvn -B -Dmaven.compiler.proc=full test
```

说明：

- 项目源码目标为 Java 17；本机为 JDK 23。JDK 23 默认关闭注解处理，
  Lombok 不会生成 getter/setter，必须带 `-Dmaven.compiler.proc=full`。
- `JAVA_TOOL_OPTIONS` 仅用于让中文日志以 UTF-8 写入 `test-run.log`，不影响测试结果。

### 3.2 前端语法检查

```bash
node --check frontend/assets/js/pages/courses.js
node --check frontend/assets/js/pages/resources.js
```

### 3.3 仅跑 US-07 相关用例

```bash
cd backend
mvn -B -Dmaven.compiler.proc=full -Dtest='CourseQueryIT,ResourceAndEvaluationIT' test
```

## 4. 结果摘要

| 指标 | 结果 |
| --- | --- |
| 全量自动化用例 | **94** |
| 失败 / 错误 / 跳过 | **0 / 0 / 0** |
| Sprint 1 基线回归 | 81 项全部通过（含在 94 项内） |
| 本次新增 | 13 项（`ResourceAndEvaluationIT` 11 项、`CourseQueryIT` 2 项） |
| 前端语法检查 | `courses.js`、`resources.js` 均通过 |
| 构建结论 | `BUILD SUCCESS` |

测试类明细：`CourseQueryIT` 14 项、`ResourceAndEvaluationIT` 25 项，
其余测试类合计 55 项。

## 5. US-07 验收点与证据映射

| 验收点 | 自动化证据 |
| --- | --- |
| 只返回有效挂接（`associated=1`） | TC-US07-01 |
| 重复挂接幂等，不新增行 | TC-US07-02 |
| 解绑为软删除，保留挂接行 | TC-US07-03 |
| 解绑后可重新挂接 | TC-US07-04 |
| 重复解绑幂等 | TC-US07-05 |
| 参数与不存在对象校验（400/404） | TC-US07-06、TC-US07-12 |
| 挂接 / 解绑写审计日志 | TC-US07-07 |
| 解绑后备课视图不可见 | TC-US07-08 |
| 教师课程 / 资源范围隔离 | TC-US07-09、TC-US07-13 |
| 教师资源 owner-only 修改 / 删除 | TC-US07-10 |
| 教师新建资源强制归属本人 | TC-US07-11 |

## 6. 结论边界（未覆盖项）

以下项目**未在本机执行**，需由 DRI 或集成同学补充确认：

1. **MySQL 8 端到端冒烟**：本次以 H2 集成测试为准，未启动 `backend/start.bat` 后运行
   `python docs/smoke_test.py`。
2. **浏览器手工点检**：未实际打开页面点击挂接 / 解绑；前端仅通过静态语法检查与代码复核。
3. **CI 流水线**：T-01 不在本次范围，未触发 GitHub Actions。
4. **并发挂接**：代码包含唯一键冲突兜底分支，但自动化测试未构造真实并发事务。
5. **git 提交**：按要求未 commit / push；`test-run.log` 已通过 `.gitignore` 白名单可从工作区加入暂存。
