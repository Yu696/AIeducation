# US-07 自检清单与人工审查要点

> 审查对象：`feature/us07-resource-link` 工作区改动（未 commit）
> 自检人：刘润七（AI 辅助）
> 自检日期：2026-10-17
> 结论：**自测通过；DoD ④ ⑥ 与浏览器 / MySQL 验证待人工完成**

## 1. 升级版 DoD 六条逐项自检

| # | DoD 条目 | 自检结论 | 证据 / 说明 |
| --- | --- | --- | --- |
| ① | 功能可运行，核心流程跑通 | **部分通过，待浏览器点检** | 后端接口由 MockMvc 集成测试覆盖；前端两个页面通过 `node --check`，但未实际打开浏览器点击挂接 / 解绑 |
| ② | 关键路径测试通过 | **通过** | `Tests run: 94, Failures: 0, Errors: 0, Skipped: 0`；US-07 新增 13 项，Sprint 1 基线 81 项全绿 |
| ③ | 关键代码有注释 | **通过** | 新增/变更的公开方法均有中文 Javadoc；幂等、软解绑、owner-only、前端 `guardTask` 等易错点有行内注释 |
| ④ | AI 生成代码经人工审查 diff 后合入 | **未完成（待 DRI）** | 本轮仅完成 AI 自查与本章节审查要点；按要求未 commit，需刘润七与团队逐文件审 diff 后合入 |
| ⑤ | 与 Sprint 1 增量集成通过、关键回归通过 | **通过** | 81 项 Sprint 1 用例包含在 94 项全量回归中；重点回归 TC-US02-04（courseCount=2）、TC-US03-07 / TC-US02-04（备课 4 条/3 组）均通过 |
| ⑥ | CI 或本地等价流水线构建通过 | **本地通过，CI 未运行** | `mvn -B -Dmaven.compiler.proc=full test` 为 `BUILD SUCCESS`；GitHub Actions / `scripts/ci.ps1` 属 T-01，不在本次范围 |

## 2. 人工 diff 审查要点

审查命令建议：

```bash
git diff --stat
git diff -- backend/src/main/java/com/aijiaoxue/service/CourseService.java
git diff -- backend/src/main/java/com/aijiaoxue/service/ResourceService.java
git diff -- backend/src/main/java/com/aijiaoxue/mapper/ResourceMapper.java
git diff -- frontend/assets/js/pages/courses.js
git diff -- frontend/assets/js/pages/resources.js
```

| 审查维度 | 本轮结论 | 重点核对位置 |
| --- | --- | --- |
| 跨模块接口 | 课程 → 资源通过 `CourseService.resourcesOf` 复用数据范围；`ResourceService` 注入 `CourseService`，无循环依赖 | `CourseService.java`、`ResourceService.java`、`CourseController.java` |
| 权限码 | 新接口用 `course:read`，与课程详情一致；`COURSE_ADMIN` 默认有 `course:read`、无 `resource:read`，因此不会 403；挂接/解绑仍由 `course:write` 控制 | `CourseController.java`、`PermissionCodes.java` |
| 数据范围 | 教师读：本人负责/讲授课程 + 本人可见资源；教师写：owner-only（仅本人上传资源可改/删），课程须在本人授课范围 | `CourseService.requireCourseAccess`、`requireResourceVisible`；`ResourceService.update/delete` |
| 空值 | `resourceId == null` → 400「资源ID不能为空」；课程不存在 → 404「课程不存在」；资源不存在 → 404「教学资源不存在」；从未挂接 → 404「该课程未挂接此资源」 | `CourseService.attachResource/detachResource` |
| 软解绑 | 解绑只把 `associated` 置 0，不物理删除；课程详情、课程资源列表、备课视图均只认 `associated=1`；重新挂接复用原行 | `CourseService.detachResource`、`ResourceMapper.selectResourcesByCourse/selectPrepResources` |
| 审计留痕 | 挂接/解绑成功写 `sys_audit_log`，`target_type=COURSE_RESOURCE`、`target_id=课程ID`，`detail` 含资源 ID | `@AuditLog` 注解、TC-US07-07 |
| 前端一致性 | 课程页与资源页均调用真实存在的接口；`fileUrl` 字段统一；资源类型/状态枚举与后端一致；挂接/解绑后两页都会刷新 | `courses.js`、`resources.js` |
| 可读性 | 方法命名统一为 `resourcesOf / attachResource / detachResource`；错误提示统一为「对象 + 原因」；未修改 `CourseMapper.selectCoursesOfTeacher` 语义 | 全部后端 diff |
| 回归风险 | `offering_id IS NULL` 的公共资源保留原备课口径；课程详情仍返回 `{course, offerings, resources}`；教师未加入 `/courses` 路由 | `ResourceMapper.selectPrepResources`、`courses.js` 路由声明 |

## 3. 待人工确认项

### 3.1 浏览器手工点检（建议用 ADMIN 或 DIRECTOR）

1. 课程详情抽屉能正常打开，资源区显示已挂接资源；
2. 「挂接资源」内联面板可搜索/筛选，重复挂接同一资源不报错、列表不重复；
3. 「解除挂接」需二次点击确认，确认后资源从课程详情消失；
4. 资源管理页新建资源时 URL 能正确保存（字段 `fileUrl`），详情能看到链接；
5. 资源管理页「关联教学班」筛选与「关联教学班」列显示正常；
6. 解绑后进入备课查看，该资源不再出现。

### 3.2 集成与数据验证

1. 本机 MySQL 8 已初始化时，启动后端并运行 `python docs/smoke_test.py`；
2. 若评审需要并发证据，可在 MySQL 上并行调用两次挂接接口，确认唯一键兜底分支不产生重复行；
3. 核对待提交文件清单，确认未包含 `target/`、日志临时文件或其他故事改动。

### 3.3 合入前

1. 由 DRI 对照本清单人工审查 diff，确认后方可 commit / push 并合并到 main；
2. 更新表 4 的实际工作量与完成状态、表 5 的团队处置列；
3. 评审会前补充浏览器截图或 MySQL 冒烟日志到本目录（如执行）。

## 4. 已知限制

- 本次未启动真实前端与 MySQL，前端行为为静态检查 + 代码复核结论；
- 并发挂接的 `DuplicateKeyException` 分支未在真实并发事务下验证；
- CI 流水线属于 T-01，未在本次执行；
- `test-run.log` 含本机 JDK 23 的 `JAVA_TOOL_OPTIONS` 提示，不影响用例结果。
