# Sprint 3 全量回归证据（108 → 109 项）

> 执行人：刘鹏杰（质量与集成 / 发布门禁）
> 执行日期：2026-10-24
> 基线：US-11 合入前 `main@83aca2d`（108 项）；US-11 合入后 `main@1f129b6`（109 项）
> 证据类型：可复现命令 + 原始 Maven 日志

## 1. 环境

| 项 | 值 |
| --- | --- |
| 操作系统 | Windows 11 Home China 10.0.22621 |
| JDK | 17.0.19（发布基线） |
| Maven | 3.9.13 |
| 测试库 | H2 内存库（MySQL 兼容模式，`src/test/resources/application-test.yml`） |
| 测试框架 | JUnit 5 + Spring Boot Test + MockMvc |

## 2. 执行命令

```bash
cd backend
mvn -B -ntp test
```

> 本机另有 JDK 21 环境时需追加 `-Dmaven.compiler.proc=full` 以启用 Lombok 注解处理；
> 本次发布基线使用 JDK 17，无需该参数。JDK 21 的补充运行记录见
> `jdk21-supplemental-mvn-test.log`，结果同为 108/108。

## 3. 结果

```text
Tests run: 108, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 21.311 s
```

| 测试类 | 覆盖内容 | 用例数 | 失败 | 错误 |
| --- | --- | --- | --- | --- |
| `AuthAndPermissionIT` | 登录、口令错误、令牌失效、当前用户与权限码 | 7 | 0 | 0 |
| `PermissionAccessControlIT` | 功能级越权、角色权限矩阵读写、停用令牌失效 | 13 | 0 | 0 |
| `DataScopeIT` | ALL / DEPARTMENT / SELF 数据范围、跨院配置 | 6 | 0 | 0 |
| `CourseQueryIT` | 课程查询、筛选、分页、详情、增删改、CSV 导出 | 14 | 0 | 0 |
| `OfferingAndDataIT` | 开课查询与维护、学生人次、CSV 导入 | 15 | 0 | 0 |
| `ResourceAndEvaluationIT` | 资源、备课、评教、US-07/08/09 增量 | 33 | 0 | 0 |
| `CoverageAndDashboardIT` | 督导覆盖率、四类工作台、US-10 状态机 | 20 | 0 | 0 |
| **合计** | | **108** | **0** | **0** |

## 4. 复核结论

- `DataScopeIT`（6 项）复核通过：教师仅见本人教学班，主任仅见本教研室数据；
- `PermissionAccessControlIT`（13 项）复核通过：未登录 401、越权 403，符合契约；
- 其余 6 个测试类全量回归通过，无失败、无跳过；
- 本轮后端业务代码零改动，108 项结果与 Sprint 2 基线一致。

## 5. 证据文件

| 文件 | 说明 |
| --- | --- |
| `jdk17-mvn-test.log` | JDK 17 全量回归原始日志（US-11 合入前，108 项） |
| `jdk21-supplemental-mvn-test.log` | JDK 21 + `-Dmaven.compiler.proc=full` 补充日志（US-11 合入前，108 项） |
| `jdk17-mvn-test-after-us11.log` | **US-11 合入后 JDK 17 全量回归（109 项）** |

## 6. US-11 合入后复跑（109 项）

- 代码基线：`main@1f129b6`（US-11 与回归证据均已合入）
- 执行命令：`cd backend && mvn -B -ntp test`（JDK 17.0.19）
- 结果：`Tests run: 109, Failures: 0, Errors: 0, Skipped: 0` → `BUILD SUCCESS`
- 增量：`OfferingAndDataIT` 由 15 项增至 **16 项**，新增 `offeringScheduleOverview` 契约测试
- 原始日志：`jdk17-mvn-test-after-us11.log`

| 测试类 | 用例数 | 失败 |
| --- | --- | --- |
| `AuthAndPermissionIT` | 7 | 0 |
| `PermissionAccessControlIT` | 13 | 0 |
| `DataScopeIT` | 6 | 0 |
| `CourseQueryIT` | 14 | 0 |
| `OfferingAndDataIT` | 16 | 0 |
| `ResourceAndEvaluationIT` | 33 | 0 |
| `CoverageAndDashboardIT` | 20 | 0 |
| **合计** | **109** | **0** |
