package com.aijiaoxue.auth;

import com.aijiaoxue.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-01 认证与登录测试。
 *
 * <p>对应测试报告 TC-US01-01 ~ TC-US01-07。
 */
@Transactional
class AuthAndPermissionIT extends BaseIntegrationTest {

    @Test
    @DisplayName("TC-US01-01 正确口令登录成功并返回令牌、角色与权限码")
    void loginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.user.roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.data.user.dataScope").value("ALL"))
                // 管理员应拥有全部权限码
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.permissions[?(@=='user:write')]").isNotEmpty())
                .andExpect(jsonPath("$.data.permissions[?(@=='role:write')]").isNotEmpty())
                .andExpect(jsonPath("$.data.permissions[?(@=='coverage:read')]").isNotEmpty());
    }

    @Test
    @DisplayName("TC-US01-02 口令错误返回 401 且提示统一，不泄露账号是否存在")
    void loginWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("TC-US01-03 不存在的账号与错误口令提示一致")
    void loginUnknownUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no-such-user\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("TC-US01-04 未携带令牌访问受保护接口返回 401")
    void accessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("TC-US01-05 伪造令牌访问受保护接口返回 401")
    void accessWithFakeToken() throws Exception {
        mockMvc.perform(get("/api/courses").header("Authorization", bearer("fake-token-123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("TC-US01-06 携带令牌可获取当前用户信息与权限码")
    void meReturnsUserAndPermissions() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("teacher1"))
                .andExpect(jsonPath("$.data.user.roleCode").value("TEACHER"))
                .andExpect(jsonPath("$.data.user.dataScope").value("SELF"))
                .andExpect(jsonPath("$.data.user.teacherName").value("张伟"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                // 教师不含用户管理权限
                .andExpect(jsonPath("$.data.permissions[?(@=='user:write')]").isEmpty())
                // 教师含备课查看权限
                .andExpect(jsonPath("$.data.permissions[?(@=='resource:prep')]").isNotEmpty());
    }

    @Test
    @DisplayName("TC-US01-07 教师角色登录后返回 SELF 数据范围，主任返回 DEPARTMENT")
    void loginDataScopePerRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"director\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roleCode").value("DIRECTOR"))
                .andExpect(jsonPath("$.data.user.dataScope").value("DEPARTMENT"))
                .andExpect(jsonPath("$.data.user.departmentCode").value("CS"));
    }
}
