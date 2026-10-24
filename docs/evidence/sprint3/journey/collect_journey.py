# -*- coding: utf-8 -*-
"""
Sprint 3 权限旅程与异常旅程接口证据采集脚本（US-11 / T-03）。

只读：仅调用 GET /api/offerings 及其详情接口，写接口只用于验证越权拦截（预期 403，不会落库）。

用法：
    1) 启动后端（真实 MySQL）：backend> start.bat
    2) 执行：python docs/evidence/sprint3/journey/collect_journey.py
       若后端不在 8080：set SMOKE_BASE_URL=http://127.0.0.1:8081
    3) 结果写入同目录 permission-anomaly-journey.md
"""
import datetime
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("SMOKE_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
PASSWORD = "123456"
LINES = []
FAIL = 0
KNOWN = 0


def call(method, path, token=None, body=None):
    if "?" in path:
        head, query = path.split("?", 1)
        path = head + "?" + urllib.parse.quote(query, safe="=&")
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json;charset=UTF-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return 0, str(e)


def parse(text):
    try:
        return json.loads(text)
    except Exception:
        return {}


def get_data(text, key, default=None):
    data = parse(text).get("data")
    if isinstance(data, dict):
        return data.get(key, default)
    return default


def login(user):
    st, body = call("POST", "/api/auth/login", body={"username": user, "password": PASSWORD})
    return st, get_data(body, "token"), parse(body)


def redact_login(body):
    """输出证据前脱敏令牌，避免把真实 Bearer Token 写入仓库"""
    safe = json.loads(json.dumps(body))
    data = safe.get("data")
    if isinstance(data, dict) and data.get("token"):
        data["token"] = "***REDACTED***"
    return json.dumps(safe, ensure_ascii=False)


def record(tag, title, method, path, role, status, detail, excerpt="", known=False):
    global FAIL, KNOWN
    ok = detail.get("ok", False)
    if not ok and known:
        KNOWN += 1
    elif not ok:
        FAIL += 1
    verdict = "**通过**" if ok else ("**已知缺陷**" if known else "**失败**")
    LINES.append("### %s %s" % (tag, title))
    LINES.append("")
    LINES.append("| 项 | 内容 |")
    LINES.append("| --- | --- |")
    LINES.append("| 角色 | %s |" % role)
    LINES.append("| 请求 | `%s %s` |" % (method, path))
    LINES.append("| HTTP | %s |" % status)
    LINES.append("| 结论 | %s |" % verdict)
    LINES.append("| 判定依据 | %s |" % detail.get("reason", ""))
    if excerpt:
        LINES.append("")
        LINES.append("响应摘录：")
        LINES.append("")
        LINES.append("```json")
        LINES.append(excerpt)
        LINES.append("```")
    LINES.append("")


def codes(records):
    return sorted(str(r.get("offeringCode")) for r in records if r.get("offeringCode"))


def main():
    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S %z")
    LINES.append("# Sprint 3 权限 / 异常旅程接口证据")
    LINES.append("")
    LINES.append("> 执行时间：%s" % now)
    LINES.append("> 执行人：刘鹏杰（T-03 权限/异常旅程）")
    LINES.append("> 后端：%s；数据库：真实 MySQL `aijiaoxue`" % BASE)
    LINES.append("> 脚本：`docs/evidence/sprint3/journey/collect_journey.py`（只读采集）")
    LINES.append("")
    LINES.append("说明：本文件保存接口级真实响应；页面截图依赖 `feature/sprint3-schedule-overview`")
    LINES.append("合入后的 `/schedule` 页面，合入后由刘鹏杰补拍并追加到本目录。")
    LINES.append("")
    LINES.append("## 一、权限旅程")
    LINES.append("")

    # 未登录 → 401
    st, body = call("GET", "/api/courses")
    record("P-01", "未登录访问受保护接口", "GET", "/api/courses", "未登录", st,
           {"ok": st == 401, "reason": "期望 401，实际 %s" % st},
           body[:200])

    # 管理员令牌仅用于核对合上关系（不对应旅程角色）
    _, admin_token, _ = login("admin")

    # 教师登录
    st, teacher_token, login_body = login("teacher1")
    role_code = (login_body.get("data") or {}).get("user", {}).get("roleCode")
    record("P-02", "教师登录并获取令牌", "POST", "/api/auth/login", "teacher1", st,
           {"ok": st == 200 and bool(teacher_token) and role_code == "TEACHER",
            "reason": "期望 200 且 roleCode=TEACHER，实际 roleCode=%s" % role_code},
           redact_login(login_body)[:400])

    if teacher_token:
        # 教师只看本人教学班（含合上）
        st, body = call("GET", "/api/offerings?size=200", teacher_token)
        records = get_data(body, "records", [])
        expected = {"CS101-2025-2026-1-01", "CS102-2025-2026-1-01",
                    "CS202-2025-2026-1-01", "CS101-2024-2025-2-01"}
        actual = set(codes(records))
        co_teaching_ok = False
        co_detail = ""
        co_record = next((r for r in records if r.get("offeringCode") == "CS202-2025-2026-1-01"), None)
        if co_record and co_record.get("id") is not None and admin_token:
            # 用管理员身份核对 CS202 的任课教师关系，避免教师详情权限影响本项判定
            dst, dbody = call("GET", "/api/offerings/%s" % co_record["id"], admin_token)
            teachers = get_data(dbody, "teachers", [])
            co_teaching_ok = dst == 200 and any(
                t.get("teacher_name") == "张伟" for t in teachers if isinstance(t, dict))
            co_detail = json.dumps({"http": dst, "teachers": teachers}, ensure_ascii=False)[:400]
        record("P-03", "教师只看到本人教学班（含合上）", "GET",
               "/api/offerings?size=200", "teacher1", st,
               {"ok": st == 200 and actual == expected and co_teaching_ok,
                "reason": "期望 %s；实际 %s；合上记录 CS202 任课关系含张伟=%s"
                          % (sorted(expected), sorted(actual), co_teaching_ok)},
               json.dumps({"total": get_data(body, "total"),
                           "offeringCodes": sorted(actual)}, ensure_ascii=False))
        if co_detail:
            LINES.append("CS202 合上身份核验（管理员读取详情接口 `teachers`）：")
            LINES.append("")
            LINES.append("```json")
            LINES.append(co_detail)
            LINES.append("```")
            LINES.append("")

        # 合上教学班详情：列表可见但详情鉴权未覆盖合上关系（已知缺陷 BUG-S3-01）
        if co_record and co_record.get("id") is not None:
            dst, dbody = call("GET", "/api/offerings/%s" % co_record["id"], teacher_token)
            record("P-03b", "教师打开合上教学班详情", "GET",
                   "/api/offerings/%s" % co_record["id"], "teacher1", dst,
                   {"ok": dst == 200,
                    "reason": "合上教学班在列表中可见，详情应返回 200；实际 %s（BUG-S3-01）" % dst},
                   dbody[:300], known=(dst != 200))

        # 教师跨教学班详情 → 403
        st, body = call("GET", "/api/offerings/3", teacher_token)
        record("P-04", "教师按 ID 枚举他人教学班", "GET", "/api/offerings/3", "teacher1", st,
               {"ok": st == 403, "reason": "期望 403（教学班 3 主讲为李娜），实际 %s" % st},
               body[:200])

        # 教师纵向越权 → 403
        st, body = call("GET", "/api/admin/users", teacher_token)
        record("P-05", "教师访问用户管理", "GET", "/api/admin/users", "teacher1", st,
               {"ok": st == 403, "reason": "期望 403，实际 %s" % st},
               body[:200])

    # 督导：全校只读
    st, supervisor_token, _ = login("supervisor")
    if supervisor_token:
        st, body = call("GET", "/api/offerings?size=200", supervisor_token)
        records = get_data(body, "records", [])
        total = get_data(body, "total")
        record("P-06", "督导全校只读查看开课总览", "GET", "/api/offerings?size=200",
               "supervisor", st,
               {"ok": st == 200 and total == 30 and len(records) == 30,
                "reason": "期望 200 且 total=30（全校 30 条种子开课），实际 total=%s" % total},
               json.dumps({"total": total, "first": records[0] if records else None},
                          ensure_ascii=False)[:500])

        st, body = call("POST", "/api/offerings", supervisor_token,
                        {"offeringCode": "JOURNEY-RO", "courseId": 1, "semester": "2025-2026-1",
                         "className": "越权写校验", "teacherId": 1, "departmentCode": "CS"})
        record("P-07", "督导写开课被拦截（只读约束）", "POST", "/api/offerings",
               "supervisor", st,
               {"ok": st == 403, "reason": "期望 403，实际 %s；若通过则存在越权写风险" % st},
               body[:300])

    # 主任：仅本院系
    st, director_token, _ = login("director")
    if director_token:
        st, body = call("GET", "/api/offerings?size=200", director_token)
        records = get_data(body, "records", [])
        depts = sorted({r.get("departmentCode") for r in records})
        record("P-08", "主任数据范围收窄（仅本院系）", "GET", "/api/offerings?size=200",
               "director", st,
               {"ok": st == 200 and len(records) > 0 and depts == ["MATH"],
                "reason": "期望所有记录 departmentCode=MATH，实际 %s" % depts},
               json.dumps({"total": get_data(body, "total"), "departments": depts,
                           "offeringCodes": codes(records)}, ensure_ascii=False))

    LINES.append("## 二、异常旅程（接口级）")
    LINES.append("")

    st, admin_token, _ = login("admin")
    if admin_token:
        st, body = call("GET", "/api/offerings?semester=1900-1901&size=200", admin_token)
        records = get_data(body, "records", [])
        record("A-01", "学期无数据返回空列表（前端空态数据源）", "GET",
               "/api/offerings?semester=1900-1901&size=200", "admin", st,
               {"ok": st == 200 and get_data(body, "total") == 0 and records == [],
                "reason": "期望 200、total=0、records=[]"},
               json.dumps({"total": get_data(body, "total"), "records": records},
                          ensure_ascii=False))

        st, body = call("GET", "/api/offerings?size=200", admin_token)
        records = get_data(body, "records", [])
        field_ok = bool(records) and all("scheduleInfo" in r for r in records)
        non_empty = sum(1 for r in records if isinstance(r.get("scheduleInfo"), str)
                        and r["scheduleInfo"].strip())
        record("A-02", "scheduleInfo 字段完整性与空值口径", "GET",
               "/api/offerings?size=200", "admin", st,
               {"ok": st == 200 and field_ok and non_empty > 0,
                "reason": "期望记录均含 scheduleInfo 且至少 1 条非空；实际字段齐全=%s，非空=%s/%s"
                          % (field_ok, non_empty, len(records))},
               json.dumps([{"offeringCode": r.get("offeringCode"),
                            "scheduleInfo": r.get("scheduleInfo")}
                           for r in records[:3]], ensure_ascii=False))
        LINES.append("> 种子数据中 `schedule_info` 全部有值（30/30），因此“空值显示未填写”")
        LINES.append("> 分支需在页面模块 `pages/schedule.js` 合入后用构造数据或截图验证；")
        LINES.append("> 后端只读契约已由 SM-02 / A-02 锁定为字符串字段。")
        LINES.append("")

    LINES.append("### A-03 后端不可用时的前端提示（待页面合入后截图）")
    LINES.append("")
    LINES.append("| 项 | 内容 |")
    LINES.append("| --- | --- |")
    LINES.append("| 场景 | 后端停止或网络不可达 |")
    LINES.append("| 期望 | 页面显示友好错误提示，不出现白屏/未捕获异常 |")
    LINES.append("| 当前状态 | **待验证**：`/schedule` 页面依赖分支 1 合入 |")
    LINES.append("")

    LINES.append("## 三、汇总")
    LINES.append("")
    LINES.append("| 类别 | 数量 | 说明 |")
    LINES.append("| --- | --- | --- |")
    LINES.append("| 通过 | 见上表 | 权限与异常主旅程 |")
    LINES.append("| 已知缺陷 | %d | BUG-S3-01：合上教学班详情鉴权未覆盖 `offering_teacher` |" % KNOWN)
    LINES.append("| 失败 | %d | 需要立即修复或阻断发布 |" % FAIL)
    LINES.append("| 待补证据 | 3 | 页面截图/录屏：主旅程、异常空态、后端不可用提示 |")
    LINES.append("")
    LINES.append("> 页面截图类证据在 `feature/sprint3-schedule-overview` 合入后补拍；")
    LINES.append("> 接口级结论已可复现，脚本与原始响应见本目录。")
    LINES.append("")
    output = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                          "permission-anomaly-journey.md")
    with open(output, "w", encoding="utf-8") as f:
        f.write("\n".join(LINES) + "\n")
    print("written: %s" % output)
    print("failures: %d" % FAIL)
    return 1 if FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
