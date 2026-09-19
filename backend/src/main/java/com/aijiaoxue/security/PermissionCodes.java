package com.aijiaoxue.security;

import com.aijiaoxue.common.RoleCodes;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限码常量与「角色—功能」权限矩阵。
 *
 * <p>US-01 验收标准①：存在管理员、教研室主任、教师、教学督导四类角色。
 * 本类同时给出五类角色的默认权限矩阵（代码内可审阅、可测试、可版本管理），
 * 系统管理员可在「用户与权限管理」页通过 sys_role_permission 表做覆写，
 * 覆写优先于此处默认值（见 PermissionService#effectivePermissions）。
 */
public final class PermissionCodes {

    // ---------------- 用户与权限（US-01） ----------------
    public static final String USER_READ = "user:read";
    public static final String USER_WRITE = "user:write";
    public static final String ROLE_READ = "role:read";
    public static final String ROLE_WRITE = "role:write";
    public static final String AUDIT_READ = "audit:read";

    // ---------------- 课程库与开课（史诗一：查课程） ----------------
    public static final String COURSE_READ = "course:read";
    public static final String COURSE_WRITE = "course:write";
    public static final String OFFERING_READ = "offering:read";
    public static final String OFFERING_WRITE = "offering:write";
    public static final String TEACHER_READ = "teacher:read";
    public static final String TEACHER_WRITE = "teacher:write";

    // ---------------- 教学资源（史诗二） ----------------
    public static final String RESOURCE_READ = "resource:read";
    public static final String RESOURCE_WRITE = "resource:write";
    public static final String RESOURCE_PREP = "resource:prep";

    // ---------------- 教学评价（史诗三） ----------------
    public static final String EVALUATION_READ = "evaluation:read";

    // ---------------- 督导与听评课（US-04） ----------------
    public static final String SUPERVISION_READ = "supervision:read";
    public static final String SUPERVISION_WRITE = "supervision:write";
    public static final String COVERAGE_READ = "coverage:read";

    // ---------------- 工作台 ----------------
    public static final String DASHBOARD_READ = "dashboard:read";
    public static final String DASHBOARD_ADMIN = "dashboard:admin";
    public static final String DASHBOARD_DIRECTOR = "dashboard:director";
    public static final String DASHBOARD_TEACHER = "dashboard:teacher";
    public static final String DASHBOARD_SUPERVISOR = "dashboard:supervisor";

    /** 全部权限码 -> 中文名。用于「用户与权限管理」页渲染权限矩阵勾选框。 */
    private static final Map<String, String> ALL = new LinkedHashMap<>();

    static {
        ALL.put(USER_READ, "查看用户");
        ALL.put(USER_WRITE, "维护用户");
        ALL.put(ROLE_READ, "查看角色与权限");
        ALL.put(ROLE_WRITE, "维护角色权限");
        ALL.put(AUDIT_READ, "查看操作日志");

        ALL.put(COURSE_READ, "查看课程信息");
        ALL.put(COURSE_WRITE, "维护课程信息");
        ALL.put(OFFERING_READ, "查看开课情况");
        ALL.put(OFFERING_WRITE, "维护开课情况");
        ALL.put(TEACHER_READ, "查看教师信息");
        ALL.put(TEACHER_WRITE, "维护教师信息");

        ALL.put(RESOURCE_READ, "查看教学资源");
        ALL.put(RESOURCE_WRITE, "维护教学资源");
        ALL.put(RESOURCE_PREP, "备课查看");

        ALL.put(EVALUATION_READ, "查看教学评价");

        ALL.put(SUPERVISION_READ, "查看听评课记录");
        ALL.put(SUPERVISION_WRITE, "维护听评课安排");
        ALL.put(COVERAGE_READ, "查看督导覆盖率");

        ALL.put(DASHBOARD_READ, "查看工作台");
        ALL.put(DASHBOARD_ADMIN, "管理员工作台");
        ALL.put(DASHBOARD_DIRECTOR, "教研室主任工作台");
        ALL.put(DASHBOARD_TEACHER, "教师工作台");
        ALL.put(DASHBOARD_SUPERVISOR, "教学督导工作台");
    }

    /** 仅查看类权限集合，供各角色做只读授权。 */
    private static final List<String> READ_ONLY = Arrays.asList(
            COURSE_READ, OFFERING_READ, TEACHER_READ,
            RESOURCE_READ, RESOURCE_PREP, EVALUATION_READ,
            SUPERVISION_READ, COVERAGE_READ, DASHBOARD_READ);

    /**
     * 角色默认权限矩阵。
     * <p>注意：教师角色不含 course:write / offering:write，但保留 course:read 与 offering:read，
     * 业务层会按 SELF 数据范围强制追加 teacher_id 过滤，保证教师只能看到自己的课程与教学班。
     */
    private static final Map<String, Set<String>> ROLE_MATRIX = new LinkedHashMap<>();

    static {
        // 系统管理员：全部权限
        ROLE_MATRIX.put(RoleCodes.ADMIN, new LinkedHashSet<>(ALL.keySet()));

        // 教研室主任：本教研室数据的查看 + 课程/开课维护 + 资源维护 + 工作台
        ROLE_MATRIX.put(RoleCodes.DIRECTOR, new LinkedHashSet<>(Arrays.asList(
                COURSE_READ, COURSE_WRITE, OFFERING_READ, OFFERING_WRITE,
                TEACHER_READ, TEACHER_WRITE, RESOURCE_READ, RESOURCE_WRITE, RESOURCE_PREP,
                EVALUATION_READ, SUPERVISION_READ, COVERAGE_READ,
                DASHBOARD_READ, DASHBOARD_DIRECTOR)));

        // 教师：只读，数据范围 SELF
        ROLE_MATRIX.put(RoleCodes.TEACHER, new LinkedHashSet<>(Arrays.asList(
                COURSE_READ, OFFERING_READ, TEACHER_READ, RESOURCE_READ, RESOURCE_WRITE,
                RESOURCE_PREP, EVALUATION_READ, DASHBOARD_READ, DASHBOARD_TEACHER)));

        // 教学督导：全校只读 + 听评课维护 + 覆盖率
        ROLE_MATRIX.put(RoleCodes.SUPERVISOR, new LinkedHashSet<>(Arrays.asList(
                COURSE_READ, OFFERING_READ, TEACHER_READ, RESOURCE_READ,
                EVALUATION_READ, SUPERVISION_READ, SUPERVISION_WRITE, COVERAGE_READ,
                DASHBOARD_READ, DASHBOARD_SUPERVISOR)));

        // 课程管理员：课程库与开课只读
        ROLE_MATRIX.put(RoleCodes.COURSE_ADMIN, new LinkedHashSet<>(Arrays.asList(
                COURSE_READ, OFFERING_READ, TEACHER_READ, DASHBOARD_READ)));
    }

    private PermissionCodes() {
    }

    /** 全部权限码（有序） */
    public static Set<String> allCodes() {
        return Collections.unmodifiableSet(ALL.keySet());
    }

    /** 权限码中文名 */
    public static Map<String, String> allWithName() {
        return Collections.unmodifiableMap(ALL);
    }

    /** 某角色的默认权限集合；未知角色返回空集 */
    public static Set<String> defaultsOf(String roleCode) {
        Set<String> s = ROLE_MATRIX.get(roleCode);
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }

    /** 是否为已登记的权限码 */
    public static boolean isValid(String code) {
        return ALL.containsKey(code);
    }

    /** 只读类权限码，测试用 */
    public static List<String> readOnlyCodes() {
        return Collections.unmodifiableList(READ_ONLY);
    }
}
