package com.aijiaoxue.auth;

import com.aijiaoxue.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-01 验收标准②③：越权访问拦截测试。
 *
 * <p>对应测试报告 TC-US01-08 ~ TC-US01-20。
 * 覆盖「功能级权限（权限码）」与「角色间互不越权」两个层面。
 *
 * <p>本类含权限覆写与账号停用等写操作，统一使用 {@code @Transactional} 自动回滚，
 * 避免污染样例数据与其他测试类。
 */
@Transactional
class PermissionAccessControlIT extends BaseIntegrationTest {

    // ---------------- 教师角色：只读，不含管理与维护权限 ----------------

    @Test
    @DisplayName("TC-US01-08 教师访问用户管理接口被拦截（403）")
    void teacherCannotListUsers() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限访问该功能（越权访问已拦截）"));
    }

    @Test
    @DisplayName("TC-US01-09 教师新建课程被拦截（403）")
    void teacherCannotCreateCourse() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(post("/api/courses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"HACK01\",\"courseName\":\"越权新建课程\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-US01-10 教师删除课程被拦截（403）")
    void teacherCannotDeleteCourse() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(delete("/api/courses/1").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-US01-11 教师访问角色权限矩阵被拦截（403）")
    void teacherCannotReadRolePermissions() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/admin/roles").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-US01-12 教师访问操作日志被拦截（403）")
    void teacherCannotReadAuditLogs() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-US01-13 教师访问管理员工作台被拦截（403）")
    void teacherCannotAccessAdminDashboard() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/dashboard/admin").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ---------------- 课程管理员：只有只读权限 ----------------

    @Test
    @DisplayName("TC-US01-14 课程管理员可只读课程库，但新建课程被拦截（403）")
    void courseAdminReadOnly() throws Exception {
        String token = login("courseadmin");
        mockMvc.perform(get("/api/courses").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"CA001\",\"courseName\":\"课程管理员越权新建\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ---------------- 教学督导：可维护听评课，但不含用户管理 ----------------

    @Test
    @DisplayName("TC-US01-15 教学督导可查看覆盖率，但访问用户管理被拦截（403）")
    void supervisorCoverageAllowedUserBlocked() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(get("/api/supervision/coverage").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ---------------- 管理员：全部放行 ----------------

    @Test
    @DisplayName("TC-US01-16 管理员可访问用户管理、角色权限与操作日志")
    void adminCanAccessAll() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/roles").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/admin/permissions").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---------------- 角色权限矩阵可读可改 ----------------

    @Test
    @DisplayName("TC-US01-17 读取角色权限：教师默认权限与覆盖标记正确")
    void readRolePermissions() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/admin/roles/TEACHER/permissions").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("TEACHER"))
                .andExpect(jsonPath("$.data.overridden").value(false))
                .andExpect(jsonPath("$.data.effective[?(@=='resource:prep')]").isNotEmpty())
                .andExpect(jsonPath("$.data.effective[?(@=='user:write')]").isEmpty());
    }

    @Test
    @DisplayName("TC-US01-18 保存角色权限后立即生效：撤销教师备课权限后其调用被拦截")
    void saveRolePermissionTakesEffect() throws Exception {
        String adminToken = login("admin");
        String teacherToken = login("teacher1");

        // 撤销前可正常访问备课查看
        mockMvc.perform(get("/api/resources/prep").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 管理员把教师角色权限整体改为只读课程
        mockMvc.perform(put("/api/admin/roles/TEACHER/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"course:read\",\"dashboard:read\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effective[?(@=='resource:prep')]").isEmpty());

        // 权限变更后教师访问备课查看被拦截
        mockMvc.perform(get("/api/resources/prep").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-US01-19 保存未登记的权限码被拒绝（400）")
    void saveInvalidPermissionRejected() throws Exception {
        String token = login("admin");
        mockMvc.perform(put("/api/admin/roles/TEACHER/permissions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"not-exist:perm\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("未登记的权限码：not-exist:perm"));
    }

    @Test
    @DisplayName("TC-US01-20 账号停用后原令牌立即失效（401）")
    void disabledUserTokenRevoked() throws Exception {
        String teacherToken = login("teacher1");
        // 停用前可访问
        mockMvc.perform(get("/api/courses").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        String adminToken = login("admin");
        mockMvc.perform(put("/api/admin/users/3")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher1\",\"realName\":\"张伟\",\"roleCode\":\"TEACHER\","
                                + "\"teacherId\":1,\"departmentCode\":\"CS\",\"enabled\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 停用后原令牌失效
        mockMvc.perform(get("/api/courses").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        // 恢复启用，避免影响其他测试
        mockMvc.perform(put("/api/admin/users/3")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher1\",\"realName\":\"张伟\",\"roleCode\":\"TEACHER\","
                                + "\"teacherId\":1,\"departmentCode\":\"CS\",\"enabled\":1}"))
                .andExpect(status().isOk());
    }
}
