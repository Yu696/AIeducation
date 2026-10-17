# US-09 评教结果分析与统计导出 · 证据索引

> 负责人：余润东（US-09 主责，产品与范围/验收）
> 代码落地：本次由刘鹏杰与 AI 辅助完成，待余润东业务验收
> 日期：2026-10-17
> 状态：**代码、自动化测试、真实 MySQL 验证已完成；页面待浏览器人工点验**

## 1. 交付范围

1. **评教分析筛选**：学期、课程、教师、评价类型；
2. **课程汇总**：平均分、参评人次、最高分、最低分；
3. **学期趋势**：按学期聚合平均分与参评人次；
4. **教师汇总**：按教师聚合课程数、参评人次、加权平均分；
5. **分析导出**：按课程/学期/教师三个维度输出 CSV，筛选口径与 summary 一致；
6. **数据范围**：教师仅本人、主任仅本教研室、督导/管理员全校。

## 2. 代码改动

| 层 | 文件 | 改动 |
| --- | --- | --- |
| Mapper | `EvaluationMapper.java` | summary/distribution 增加类型/课程/教师筛选；新增 trend、teacherSummary SQL |
| 视图 | `EvaluationTrendRow.java`、`TeacherSummaryRow.java` | 趋势与教师汇总返回对象 |
| Service | `EvaluationService.java` | 筛选与数据范围、默认学生评教/全部类型、加权口径 |
| Controller | `EvaluationController.java` | summary 参数扩展；新增 `GET /api/evaluations/summary/export` |
| 前端 | `pages/evaluations.js` | 分析筛选、趋势图、教师汇总、分析导出；修复 summary 解析与明细类型筛选参数 |

## 3. 接口

```text
GET /api/evaluations/summary?semester=&courseId=&teacherId=&evaluationType=
GET /api/evaluations/summary/export?semester=&courseId=&teacherId=&evaluationType=
```

- `evaluationType` 不传：默认 `学生评教`（保持 Sprint 1 口径）；
- `evaluationType=全部`：统计全部评价类型；
- 分析导出与 summary 使用同一筛选条件。

## 4. 自动化测试

新增 6 个用例：

| 用例 | 内容 |
| --- | --- |
| TC-US09-01 | 类型/课程/教师/学期筛选 |
| TC-US09-02 | 教师汇总按参评人次加权、趋势与课程汇总同时返回 |
| TC-US09-03 | 新增历史学期评价后趋势出现两个学期 |
| TC-US09-04 | 分析导出含课程/学期/教师三个维度 |
| TC-US09-05 | 教师评教分析只包含本人数据 |
| TC-US09-06 | 评价明细支持课程/教师下钻筛选 |

全量结果：

```text
Tests run: 108, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 5. 真实 MySQL 验证

在真实 MySQL（`aijiaoxue`，60 条评价）上启动独立实例 `:8081`，验证结果：

```text
[PASS] summary 返回课程/趋势/教师汇总
       默认(学生评教): total=42 avg=81.7 courses=23 trend=2 teachers=12
[PASS] 趋势至少两个学期
[PASS] 教师汇总非空
[PASS] 全部类型 total=60
[PASS] 督导评教可筛选
[PASS] 课程筛选生效
[PASS] 教师筛选生效
[PASS] 教师数据范围仅本人
[PASS] 主任数据范围正常
[PASS] 督导全校数据正常
[PASS] 分析导出 CSV 含三个维度
结果：通过 11 项，失败 0 项
```

> 验证使用独立端口 8081，未修改 8080 上原有进程；分析接口均为只读，未写入业务数据。

## 6. AI 互动摘要（供表5整理）

- 2026-10-17，AI 编码辅助（Claude Code）：根据 US-09 验收标准扩展评教 summary、趋势、教师汇总与分析导出，并修复前端 summary 解析和明细类型筛选参数；
- 追加 6 个回归用例，全量 108 个用例通过；
- 真实 MySQL 独立实例验证 11 项全部通过。

## 7. 待补

- 余润东业务验收（统计口径、页面交互）；
- 浏览器人工点验与截图归档到 `docs/evidence/sprint2/review/`；
- 正式表5由刘润七汇总；
- 评审/回顾会正式召开并签字。
