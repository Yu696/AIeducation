# 爱教学 · 前端（Sprint 1「查课程」）

16 组 / 软件项目管理 实验二 · Sprint 1 · 代号：蜂鸟

零外部依赖的纯静态 SPA：**不使用 CDN、不使用 npm、不使用构建工具**。
全部代码为经典脚本（非 ES module），因此直接双击 `index.html` 也不会被 CORS 拦截加载。

---

## 一、文件结构

```
frontend/
├── index.html                     登录页 + 应用外壳（单页，hash 路由），共 362 行
├── README.md                      本文件
└── assets/
    ├── css/
    │   └── style.css              全部样式（浅色管理后台 + 深色侧边栏），985 行
    └── js/
        ├── api.js                 fetch 封装：base/鉴权/统一解包/401/403/CSV 出入库，561 行
        ├── router.js              hash 路由 + 菜单权限 + 竞态守卫，358 行
        ├── ui.js                  通用组件（表格/弹窗/抽屉/Toast/进度/统计卡/远程下拉），1271 行
        ├── charts.js              纯 Canvas 图表（柱状/横向条形/环形/折线），605 行
        └── pages/
            ├── dashboard.js       工作台（按角色渲染统计卡片与图表），423 行
            ├── courses.js         课程信息管理，527 行
            ├── offerings.js       开课与授课管理，519 行
            ├── teachers.js        教师信息管理，440 行
            ├── resources.js       教学资源管理，414 行
            ├── prep.js            备课查看，323 行
            ├── evaluations.js     教学评价管理，396 行
            ├── supervision.js     督导与听评课，467 行
            ├── coverage.js        督导覆盖率，352 行
            └── admin.js           用户与权限管理（三标签页），749 行
```

脚本加载顺序（见 `index.html` 底部）：`api.js → ui.js → charts.js → router.js → pages/*.js → 引导脚本`。
每个文件顶部都有中文模块说明注释，关键函数均有中文注释（DoD 第③条）。

---

## 二、如何本地打开

### 方式 A：由后端静态托管（推荐，与接口同源）

后端启动后把 `frontend/` 作为静态资源目录即可，然后直接访问：

```
http://localhost:8080/
```

此时前端默认使用**同源** `/api`，无需任何额外配置。

### 方式 B：本地静态服务 + 单独启动后端

```bash
cd frontend
python -m http.server 5500
```

浏览器访问 `http://localhost:5500/`。此时前端运行在 5500、后端在 8080，
属于跨源请求，需要后端允许 CORS（`Access-Control-Allow-Origin`）并放行
`Authorization` 请求头；否则点击右上角 **「API 地址」** 把地址改成
`http://localhost:8080/api`（仍受后端 CORS 策略约束）。

### 方式 C：直接双击 index.html

页面可以打开，但浏览器会拦截 `file://` 发起的跨源 API 请求。此时页面顶部会显示
醒目提示条，并通过右上角 **「API 地址」** 手工指定后端地址（默认尝试
`http://localhost:8080/api`）。**仅建议用于查看界面，不建议用于联调。**

### 演示账号（密码统一 `123456`）

登录页底部列出了全部 6 个演示账号，**点击任意一个即可自动填充**用户名与密码：

| 用户名 | 角色 | 数据可见范围 |
| --- | --- | --- |
| `admin` | 系统管理员 | 全量 |
| `director` | 教研室主任 | 本教研室 |
| `teacher1` / `teacher2` | 教师 | 仅本人课程与本人资源 |
| `supervisor` | 教学督导 | 全校开课与督导数据（只读） |
| `courseadmin` | 课程管理员 | 课程库与开课（只读） |

---

## 三、后端联调方式

1. **接口地址**：默认同源 `/api`。可在顶部 **「API 地址」** 中覆盖，
   值保存在 `localStorage`（键 `aijiaoxue.apiBase`），清空即恢复默认。
2. **认证**：登录成功后把 `token` 写入 `localStorage`（键 `aijiaoxue.token`），
   后续所有请求自动携带 `Authorization: Bearer <token>`。
3. **统一响应包**：`{ code, message, data }`。前端按 `code` 判定：
   - `0` → 取 `data` 交给页面
   - `401` → 清除登录态并跳转 `#/login`（同时提示一次）
   - `403` → 统一提示「无权限访问该功能」
   - 其它 → 抛出 `ApiError`，由页面展示错误块或 Toast
4. **CSV 导出**：以 blob 下载，文件名优先取 `Content-Disposition`。
5. **CSV 导入**：以 `Content-Type: text/csv;charset=UTF-8` 直接 POST 文件原文。
6. **刷新页面**：会自动调用 `GET /api/auth/me` 校验 token 并刷新角色与权限码；
   该接口失败时不会强制登出（只有 401 才登出）。

---

## 四、各页面对应的接口清单

| 页面 | 路由 | 可见角色 | 调用接口 |
| --- | --- | --- | --- |
| 登录页 | `#/login` | 匿名 | `POST /api/auth/login`、`GET /api/auth/me` |
| 工作台 | `#/dashboard` | 全部登录用户 | `GET /api/dashboard/admin`、`/director`、`/teacher`、`/supervisor`；失败时回退 `GET /api/dashboard/summary`；兜底图表用 `GET /api/courses` |
| 课程信息管理 | `#/courses` | ADMIN、DIRECTOR、COURSE_ADMIN、SUPERVISOR | `GET/POST /api/courses`、`GET/PUT/DELETE /api/courses/{id}`、`GET /api/courses/{id}/offerings`、`GET /api/courses/{id}/resources`、`GET /api/courses/export`、`POST /api/courses/import` |
| 开课与授课管理 | `#/offerings` | ADMIN、DIRECTOR、COURSE_ADMIN、SUPERVISOR、TEACHER | `GET/POST /api/offerings`、`GET/PUT/DELETE /api/offerings/{id}`、`GET/POST /api/offerings/{id}/student-counts`、`GET /api/offerings/export`、`GET /api/courses`、`GET /api/teachers` |
| 教师信息管理 | `#/teachers` | ADMIN、DIRECTOR、COURSE_ADMIN | `GET/POST /api/teachers`、`GET/PUT/DELETE /api/teachers/{id}`、`GET /api/teachers/{id}/offerings`、`GET /api/teachers/export` |
| 教学资源管理 | `#/resources` | 全部登录用户（TEACHER 仅本人，由后端强制） | `GET/POST /api/resources`、`GET/PUT/DELETE /api/resources/{id}`、`GET /api/resources/export`、`GET /api/offerings` |
| 备课查看 | `#/prep` | TEACHER、DIRECTOR、ADMIN | `GET /api/resources/prep` |
| 教学评价管理 | `#/evaluations` | ADMIN、DIRECTOR、SUPERVISOR、TEACHER | `GET /api/evaluations`、`GET /api/evaluations/summary`、`GET /api/evaluations/{id}`、`GET /api/evaluations/export` |
| 督导与听评课 | `#/supervision` | ADMIN、SUPERVISOR、DIRECTOR | `GET/POST /api/supervisions`、`PUT /api/supervisions/{id}`、`GET /api/supervisions/export`、`GET /api/supervisions/supervisors`（督导候选教师，不依赖 `user:read`）、`GET /api/offerings` |
| 督导覆盖率 | `#/coverage` | ADMIN、SUPERVISOR、DIRECTOR | `GET /api/supervision/coverage`、`GET /api/supervision/coverage/pending`、`GET /api/supervision/coverage/export` |
| 用户与权限管理 | `#/admin` | ADMIN | `GET/POST /api/admin/users`、`PUT/DELETE /api/admin/users/{id}`、`PUT /api/admin/users/{id}/password`、`GET /api/admin/roles`、`GET/PUT /api/admin/roles/{roleCode}/permissions`、`GET /api/admin/permissions`、`GET /api/admin/audit-logs` |

另有 `GET /api/config/public`（匿名）用于读取系统名称与当前学期。

---

## 五、与接口契约的差异（需后端确认）

1. **`/api/supervisions/export`（听评课明细导出）**
   契约 v1.1 已补齐该接口，前端调用路径与契约一致，无需另行确认。
   覆盖率导出仍使用 `/api/supervision/coverage/export`（按院系 + 按学期两个维度）。

2. **工作台响应字段名未在契约中细化**
   前端对工作台数据做了自适应渲染：标量字段自动生成统计卡片，数组型字段
   自动识别为图表，并兼容 `teacherWorkload` / `byDepartment` / `bySemester` /
   `scoreDistribution` / `roleDistribution` / `pendingList` / `recentAuditLogs`
   等常见命名。若后端字段名与上述不同，页面仍可用（不会白屏），但图表标题可能不够准确。

3. **`roleCode` 与 `permissions` 的字段名**
   前端兼容 `roleCode`/`role` 与 `permissions`/`permissionCodes` 两种写法。

---

## 六、前端权限与容错设计（重要）

1. **菜单可见性**：按角色 + 权限码双重过滤（`router.js` 的 `menuVisible`）。
   管理员默认拥有全部权限，避免后端权限码不全导致管理员菜单缺失。

2. **登录态分两步建立**：`token` 先落盘，用户信息（角色/权限码）随后由
   `/api/auth/me` 补齐。这段时间内角色为空，**此时不做越权拦截**，否则会把
   合法页面误判为「无权限」并弹回工作台。角色就绪后由 `refreshShell()`
   重建菜单，后续路由解析按真实角色重新判定。

3. **异步竞态守卫**：路由可能在短时间内被连续解析（启动时 `Router.start()`
   与引导脚本的 `fetchMe()` 各触发一次）。旧页面的异步回调若晚于新页面返回，
   会把内容写进已切换的容器。因此 `router.js` 提供 `AJ.Router.guard()`，
   页面在异步回调里包裹 `guard(fn)`，令牌过期则丢弃该次更新。

4. **页面模块约定**：

   ```js
   AJ.pages.xxx = {
     title: '页面标题',
     roles: ['ADMIN', ...],      // 可见角色
     render: function (container, params) { ... },   // 必填
     destroy: function () { ... }                    // 选填，用于销毁图表/定时器
   };
   ```

5. **加载与错误态**：所有异步请求都有 loading 态；失败时展示可重试的错误块；
   列表为空显示空状态文案；分页器始终显示总条数。

---

## 七、自检结果（jsdom 真实执行）

本仓库 `_verify/` 下提供了自检脚本（不属于交付物）：

- `mock-server.js`：按契约实现的模拟后端（含 12 门课程、5 位教师、14 条开课、29 个资源、40 条评价、13 条听评课等样例数据）
- `verify-dom.js`：用 jsdom 真实加载 `index.html` 与全部脚本，逐角色逐页断言渲染结果

运行方式：

```bash
node _verify/mock-server.js 8098     # 终端 A：启动模拟后端
node _verify/verify-dom.js           # 终端 B：执行逐页验证（会自动拉起 mock）
```

最近一次结果：**38 个用例全部通过**（2 个未登录重定向用例 + 36 个角色×页面用例），
覆盖 5 个角色的全部可见页面，包含菜单数量、表格行数、分页总数、Canvas 图表数量、
错误块数量与 JS 异常检查。

同时全部 14 个 JS 文件通过 `node --check`，且不存在任何外部 http(s) 资源引用。
