package com.aijiaoxue.auth;

import com.aijiaoxue.BaseIntegrationTest;
import com.aijiaoxue.entity.SysConfig;
import com.aijiaoxue.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-01 数据级权限（数据可见范围）测试。
 *
 * <p>对应测试报告 TC-US01-21 ~ TC-US01-26。
 * 验证 ALL / DEPARTMENT / SELF 三重可见范围，以及 cross_department 配置的收窄效果
 * （实验一遗留问题第 3 条「排课统筹视图是否允许跨院」在此落地为可配置开关）。
 */
@Transactional
class DataScopeIT extends BaseIntegrationTest {

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Test
    @DisplayName("TC-US01-21 教师只看本人教学班（SELF 范围强制收窄，含合上教学班）")
    void teacherSeesOwnOfferingsOnly() throws Exception {
        String token = login("teacher1");
        // 故意传入 teacherId=2（李娜），数据范围应强制收窄为本人 teacherId=1。
        // teaching 范围以 offering_teacher 为准：教学班 3 主讲是李娜、张伟为「合上」，也应可见，
        // 因此记录中允许出现 teacherId=2，但集合必须与「本人参与的教学班」完全一致。
        mockMvc.perform(get("/api/offerings?teacherId=2&size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records[?(@.id == 5)]").doesNotExist())
                .andExpect(jsonPath("$.data.records[?(@.id == 7)]").doesNotExist());
    }

    @Test
    @DisplayName("TC-US01-22 教师查看他人教师档案被拦截（403）")
    void teacherCannotViewOtherTeacherProfile() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/teachers/2").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 本人档案可正常访问
        mockMvc.perform(get("/api/teachers/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.teacher.name").value("张伟"));
    }

    @Test
    @DisplayName("TC-US01-23 教研室主任默认只能看到本院系（CS）数据")
    void directorRestrictedToOwnDepartment() throws Exception {
        String token = login("director");
        // 请求全部课程，但数据范围收窄为 CS
        mockMvc.perform(get("/api/courses?size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.departmentCode != 'CS')]").doesNotExist())
                .andExpect(jsonPath("$.data.total").value(4));

        // 请求 MATH 院系课程也会被收窄为 CS，而不是返回 MATH
        mockMvc.perform(get("/api/courses?departmentCode=MATH&size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.departmentCode != 'CS')]").doesNotExist());
    }

    @Test
    @DisplayName("TC-US01-24 管理员与督导可见全量数据（ALL 范围）")
    void adminAndSupervisorSeeAll() throws Exception {
        String adminToken = login("admin");
        mockMvc.perform(get("/api/courses?size=100").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(7));

        String supervisorToken = login("supervisor");
        mockMvc.perform(get("/api/courses?size=100").header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(7));
    }

    @Test
    @DisplayName("TC-US01-25 cross_department=true 时主任可见跨院数据（配置生效）")
    void directorCrossDepartmentWhenEnabled() throws Exception {
        SysConfig cfg = sysConfigMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, "cross_department"));
        cfg.setConfigValue("true");
        sysConfigMapper.updateById(cfg);

        String token = login("director");
        mockMvc.perform(get("/api/courses?size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.records[?(@.departmentCode == 'MATH')]").isNotEmpty());
    }

    @Test
    @DisplayName("TC-US01-26 教师资源列表只返回本人有权访问的资源（含合上教学班资源）")
    void teacherSeesOwnResourcesOnly() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/resources?size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                // 教师 1 可见资源 4 条：归属本人 3 条（含 1 条失效）+ 合上教学班 CS103 的 1 条；
                // 不含教师 2 独有教学班与教师 3 教学班的资源
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.records[?(@.id == 5)]").doesNotExist());
    }
}
