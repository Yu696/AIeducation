# -*- coding: utf-8 -*-
"""
Sprint 1 端到端冒烟测试：验证接 MySQL 启动后，6 个演示账号与核心接口全部可用。

用法：
    1) 先启动后端：backend> mvn spring-boot:run
    2) 再执行：python docs/smoke_test.py
"""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8080"
PASS = 0
FAIL = 0
RESULTS = []


def call(method, path, token=None, body=None):
    # 查询串含中文时必须先做百分号编码，否则 urllib 会直接抛错
    if "?" in path:
        head, query = path.split("?", 1)
        path = head + "?" + urllib.parse.quote(query, safe="=&")
    url = BASE + path
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json;charset=UTF-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # 连接失败等
        return 0, str(e)


def check(name, ok, detail=""):
    global PASS, FAIL
    if ok:
        PASS += 1
        RESULTS.append("  [PASS] " + name)
    else:
        FAIL += 1
        RESULTS.append("  [FAIL] " + name + "  -> " + detail)


def js(text, path, default=None):
    """从响应中取字段，容错处理"""
    try:
        node = json.loads(text)
    except Exception:
        return default
    for key in path.split("."):
        if isinstance(node, dict):
            node = node.get(key)
        else:
            return default
        if node is None:
            return default
    return node


print("=" * 20 + " Sprint 1 冒烟测试 " + "=" * 20)

# ---------- 1. 健康检查与公共配置 ----------
st, body = call("GET", "/api/health")
check("健康检查 /api/health", st == 200 and "UP" in body, "status=%s" % st)

st, body = call("GET", "/api/config/public")
check("公共配置 /api/config/public", st == 200 and "爱教学" in body, "status=%s" % st)

# ---------- 2. 未登录必须 401 ----------
st, body = call("GET", "/api/courses")
check("未登录访问 /api/courses 返回 401", st == 401, "status=%s" % st)

# ---------- 3. 六个演示账号登录 ----------
ACCOUNTS = [
    ("admin", "ADMIN"),
    ("director", "DIRECTOR"),
    ("teacher1", "TEACHER"),
    ("teacher2", "TEACHER"),
    ("supervisor", "SUPERVISOR"),
    ("courseadmin", "COURSE_ADMIN"),
]
tokens = {}
for user, role in ACCOUNTS:
    st, body = call("POST", "/api/auth/login", body={"username": user, "password": "123456"})
    token = js(body, "data.token")
    check("账号登录 %s" % user, st == 200 and bool(token), "status=%s body=%s" % (st, body[:120]))
    if token:
        tokens[user] = token
        check("账号 %s 角色码 = %s" % (user, role),
              js(body, "data.user.roleCode") == role,
              "实际 roleCode=%s" % js(body, "data.user.roleCode"))

# ---------- 4. 错误口令 ----------
st, body = call("POST", "/api/auth/login", body={"username": "admin", "password": "wrong"})
check("错误口令返回 401", st == 401, "status=%s" % st)

# ---------- 5. 管理员：用户/角色/权限/日志/工作台 ----------
if "admin" in tokens:
    t = tokens["admin"]
    st, body = call("GET", "/api/admin/users?page=1&size=5", t)
    check("管理员查询用户列表", st == 200 and js(body, "data.total") is not None, "status=%s" % st)

    st, body = call("GET", "/api/admin/roles", t)
    check("管理员查询角色列表", st == 200 and "ADMIN" in body, "status=%s" % st)

    st, body = call("GET", "/api/admin/permissions", t)
    check("管理员查询权限码清单", st == 200 and "course:read" in body, "status=%s" % st)

    st, body = call("GET", "/api/admin/audit-logs?page=1&size=5", t)
    check("管理员查询操作日志", st == 200, "status=%s" % st)

    st, body = call("GET", "/api/dashboard/admin", t)
    check("管理员工作台", st == 200 and js(body, "data.role") == "ADMIN", "status=%s" % st)

    st, body = call("GET", "/api/admin/roles/TEACHER/permissions", t)
    check("读取角色权限矩阵", st == 200 and "resource:prep" in body, "status=%s" % st)

# ---------- 6. 课程 / 开课 / 教师 ----------
if "admin" in tokens:
    t = tokens["admin"]

    st, body = call("GET", "/api/courses?page=1&size=5", t)
    total = js(body, "data.total")
    check("课程列表分页（MySQL 样例数据）", st == 200 and (total or 0) > 0,
          "status=%s total=%s" % (st, total))

    st, body = call("GET", "/api/courses/1", t)
    check("课程详情含简介全文与教学班", st == 200 and js(body, "data.course.introContent") is not None,
          "status=%s" % st)

    st, body = call("GET", "/api/courses?keyword=课程&page=1&size=5", t)
    check("课程关键词筛选", st == 200, "status=%s" % st)

    st, body = call("GET", "/api/offerings?page=1&size=5", t)
    check("开课列表分页", st == 200 and js(body, "data.total") is not None, "status=%s" % st)

    st, body = call("GET", "/api/teachers?page=1&size=5", t)
    check("教师列表分页", st == 200 and (js(body, "data.total") or 0) > 0, "status=%s" % st)

    st, body = call("GET", "/api/teachers/1", t)
    check("教师详情含课程简介与开课清单",
          st == 200 and js(body, "data.courses") is not None and js(body, "data.offerings") is not None,
          "status=%s" % st)

    st, body = call("GET", "/api/courses/export", t)
    check("课程导出 CSV", st == 200 and "课程编号" in body, "status=%s" % st)

    st, body = call("GET", "/api/offerings/export", t)
    check("开课导出 CSV（含督导状态列）", st == 200 and "督导状态" in body, "status=%s" % st)

    st, body = call("GET", "/api/teachers/export", t)
    check("教师导出 CSV", st == 200 and "工号" in body, "status=%s" % st)

# ---------- 7. 督导覆盖率 ----------
if "supervisor" in tokens:
    t = tokens["supervisor"]

    st, body = call("GET", "/api/supervision/coverage", t)
    rate = js(body, "data.coverageRate")
    total = js(body, "data.totalOfferings")
    check("督导覆盖率统计（含计算口径）",
          st == 200 and rate is not None and "caliber" in body,
          "status=%s rate=%s total=%s" % (st, rate, total))

    st, body = call("GET", "/api/supervision/coverage?groupBy=semester", t)
    check("覆盖率按学期分组", st == 200 and js(body, "data.bySemester") is not None, "status=%s" % st)

    st, body = call("GET", "/api/supervision/coverage?groupBy=department", t)
    check("覆盖率按院系分组", st == 200 and js(body, "data.byDepartment") is not None, "status=%s" % st)

    st, body = call("GET", "/api/supervision/coverage/export", t)
    check("覆盖率导出 CSV", st == 200 and "按院系" in body, "status=%s" % st)

    st, body = call("GET", "/api/supervisions?page=1&size=5", t)
    check("听评课记录列表", st == 200 and js(body, "data.total") is not None, "status=%s" % st)

    st, body = call("GET", "/api/supervision/coverage/pending", t)
    check("待安排听评课清单", st == 200 and js(body, "data") is not None, "status=%s" % st)

    st, body = call("GET", "/api/dashboard/supervisor", t)
    check("教学督导工作台", st == 200 and js(body, "data.role") == "SUPERVISOR", "status=%s" % st)

# ---------- 8. 教师：工作台 / 备课 / 学生人次 / 评价 ----------
if "teacher1" in tokens:
    t = tokens["teacher1"]

    st, body = call("GET", "/api/dashboard/teacher", t)
    check("教师工作台", st == 200 and js(body, "data.role") == "TEACHER", "status=%s" % st)

    st, body = call("GET", "/api/resources/prep", t)
    check("备课查看（按课程分组）", st == 200 and js(body, "data.groups") is not None, "status=%s" % st)

    st, body = call("GET", "/api/resources?page=1&size=50", t)
    check("教学资源列表（SELF 范围）", st == 200, "status=%s" % st)

    st, body = call("GET", "/api/evaluations/summary", t)
    check("学生评教汇总", st == 200 and js(body, "data.overallAvgScore") is not None, "status=%s" % st)

    st, body = call("GET", "/api/evaluations?page=1&size=50", t)
    check("教学评价列表（SELF 范围）", st == 200, "status=%s" % st)

    st, body = call("GET", "/api/offerings?page=1&size=50", t)
    check("教师查看本人教学班", st == 200, "status=%s" % st)

# ---------- 9. 教研室主任 ----------
if "director" in tokens:
    t = tokens["director"]

    st, body = call("GET", "/api/dashboard/director", t)
    check("教研室主任工作台", st == 200 and js(body, "data.role") == "DIRECTOR", "status=%s" % st)

    st, body = call("GET", "/api/courses?page=1&size=100", t)
    depts = set()
    try:
        for rec in json.loads(body)["data"]["records"]:
            depts.add(rec.get("departmentCode"))
    except Exception:
        pass
    check("主任数据范围收窄（仅本院系）", st == 200 and len(depts) <= 1,
          "status=%s depts=%s" % (st, sorted(depts)))

# ---------- 10. 越权拦截（US-01 验收标准②③） ----------
if "teacher1" in tokens:
    t = tokens["teacher1"]

    st, _ = call("GET", "/api/admin/users", t)
    check("教师访问用户管理被拦截（403）", st == 403, "status=%s" % st)

    st, _ = call("POST", "/api/courses", t, {"courseCode": "HACK01", "courseName": "越权新建"})
    check("教师新建课程被拦截（403）", st == 403, "status=%s" % st)

    st, _ = call("GET", "/api/admin/roles", t)
    check("教师访问角色权限被拦截（403）", st == 403, "status=%s" % st)

    st, _ = call("GET", "/api/teachers/2", t)
    check("教师查看他人档案被拦截（403）", st == 403, "status=%s" % st)

    st, _ = call("GET", "/api/dashboard/admin", t)
    check("教师访问管理员工作台被拦截（403）", st == 403, "status=%s" % st)

if "courseadmin" in tokens:
    st, _ = call("POST", "/api/courses", tokens["courseadmin"],
                 {"courseCode": "CA001", "courseName": "越权新建"})
    check("课程管理员越权新建被拦截（403）", st == 403, "status=%s" % st)

if "supervisor" in tokens:
    st, _ = call("GET", "/api/admin/users", tokens["supervisor"])
    check("督导访问用户管理被拦截（403）", st == 403, "status=%s" % st)

# ---------- 11. 前端静态资源 ----------
st, body = call("GET", "/index.html")
check("前端登录页可访问", st == 200 and "爱教学" in body, "status=%s" % st)

st, body = call("GET", "/assets/js/api.js")
check("前端静态 JS 可访问", st == 200 and len(body) > 100, "status=%s" % st)

st, body = call("GET", "/assets/css/style.css")
check("前端静态 CSS 可访问", st == 200 and len(body) > 100, "status=%s" % st)

# ---------- 汇总 ----------
print("\n".join(RESULTS))
print("")
print("=" * 16 + " 结果：通过 %d 项，失败 %d 项 " % (PASS, FAIL) + "=" * 16)
sys.exit(1 if FAIL else 0)
