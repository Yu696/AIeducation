package com.aijiaoxue.us;

import com.aijiaoxue.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-03 教学资源与备课查看、史诗三 教学评价测试。
 *
 * <p>对应测试报告 TC-US03-05 ~ TC-US03-12、TC-EVAL-01 ~ TC-EVAL-06。
 */
class ResourceAndEvaluationIT extends BaseIntegrationTest {

    // ---------------- 教学资源（US-03） ----------------

    @Test
    @DisplayName("TC-US03-05 资源列表分页返回资源与关联课程信息")
    void resourceList() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/resources?size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.records[0].resourceName").isNotEmpty());
    }

    @Test
    @DisplayName("TC-US03-06 资源按类型与状态筛选")
    void resourceFilters() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/resources?resourceType=课件&size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/resources?status=失效&size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("TC-US03-07 备课查看：按「我的课程 → 可用资源」分组，且只含有效资源")
    void prepViewGroupedByCourse() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/resources/prep").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 教师 1 有效资源 4 条（失效 1 条不计入）
                .andExpect(jsonPath("$.data.totalResources").value(4))
                // 覆盖 2025-2026-1 的 CS101、CS102 与 CS103 三个课程分组
                .andExpect(jsonPath("$.data.groupCount").value(3))
                .andExpect(jsonPath("$.data.groups[0].courseName").isNotEmpty())
                .andExpect(jsonPath("$.data.groups[0].resourceCount").isNumber());
    }

    @Test
    @DisplayName("TC-US03-08 备课查看仅教师角色可用（管理员被拦截 403）")
    void prepViewTeacherOnly() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/resources/prep").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-US03-09 新建教学资源自动绑定当前教师")
    @Transactional
    void createResource() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(post("/api/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceName\":\"数据结构-图论课件\",\"resourceType\":\"课件\","
                                + "\"fileUrl\":\"/files/cs102-graph.pdf\",\"offeringId\":2,\"status\":\"有效\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.teacherId").value(1));
    }

    @Test
    @DisplayName("TC-US03-10 新建资源缺少名称返回参数错误（400）")
    @Transactional
    void createResourceMissingName() throws Exception {
        String token = login("admin");
        mockMvc.perform(post("/api/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"课件\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("资源名称不能为空"));
    }

    @Test
    @DisplayName("TC-US03-11 某个教学班下的资源列表可查询")
    void resourceByOffering() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/resources?offeringId=1&size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("TC-US03-12 课程资源挂接与解除挂接")
    @Transactional
    void attachAndDetachResource() throws Exception {
        String token = login("admin");
        // 把资源 5 挂接到课程 1
        mockMvc.perform(post("/api/courses/1/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/courses/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.course.resourceCount").value(3));

        // 解除挂接
        mockMvc.perform(delete("/api/courses/1/resources/5").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/courses/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.course.resourceCount").value(2));
    }

    // ---------------- 教学评价（史诗三） ----------------

    @Test
    @DisplayName("TC-EVAL-01 评价列表分页与类型筛选")
    void evaluationList() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/evaluations?size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(7));

        mockMvc.perform(get("/api/evaluations?evaluationType=学生评教&size=100")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    @DisplayName("TC-EVAL-02 评价按分数区间筛选")
    void evaluationScoreRange() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/evaluations?scoreMin=90&size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4));
    }

    @Test
    @DisplayName("TC-EVAL-03 教师只能看到本人所授课程的评价（SELF 范围）")
    void teacherSeesOwnEvaluationsOnly() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/evaluations?size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                // 教师 1 的评价全部属于本人；过滤结果不应包含他人记录
                .andExpect(jsonPath("$.data.records[?(@.teacherId != 1)]").doesNotExist())
                // 教师 1 的教学班 1、2 共有 5 条评价（含督导评教与同行评议）
                .andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    @DisplayName("TC-EVAL-04 学生评教汇总：按课程聚合平均分与参评人次")
    void evaluationSummary() throws Exception {
        String token = login("director");
        mockMvc.perform(get("/api/evaluations/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvaluations").value(5))
                // (92.5+88.0+95.0+78.5+90.0)/5 = 88.8
                .andExpect(jsonPath("$.data.overallAvgScore").value(88.8))
                .andExpect(jsonPath("$.data.summary").isArray())
                .andExpect(jsonPath("$.data.caliber").isNotEmpty());
    }

    @Test
    @DisplayName("TC-EVAL-05 评教分数分布五个档位齐全")
    void evaluationScoreDistribution() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/evaluations/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.distribution.length()").value(5))
                .andExpect(jsonPath("$.data.distribution[0].bucket").value("90分以上"))
                // 90 分以上 3 条（95.0、92.5、90.0）
                .andExpect(jsonPath("$.data.distribution[0].count").value(3))
                // 80-89 分 1 条（88.0）
                .andExpect(jsonPath("$.data.distribution[1].count").value(1))
                // 70-79 分 1 条（78.5）
                .andExpect(jsonPath("$.data.distribution[2].count").value(1))
                .andExpect(jsonPath("$.data.distribution[4].bucket").value("60分以下"));
    }

    @Test
    @DisplayName("TC-EVAL-06 评价详情与导出")
    void evaluationDetailAndExport() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/evaluations/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationType").value("学生评教"))
                .andExpect(jsonPath("$.data.courseCode").value("CS101"));

        byte[] bytes = mockMvc.perform(get("/api/evaluations/export").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("评价类型"));
    }

    // ---------------- Sprint 2 US-08：备课查看增强 ----------------

    @Test
    @DisplayName("TC-US08-01 备课查看返回课程数、平均资源数、缺资源课程数与类型统计")
    void prepViewStatistics() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/resources/prep").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalResources").value(4))
                .andExpect(jsonPath("$.data.groupCount").value(3))
                .andExpect(jsonPath("$.data.courseCount").value(3))
                .andExpect(jsonPath("$.data.coursesWithoutResources").value(0))
                .andExpect(jsonPath("$.data.averageResourcesPerCourse").value(1.3))
                .andExpect(jsonPath("$.data.resourceTypeStats").isArray())
                .andExpect(jsonPath("$.data.resourceTypeStats.length()").value(4))
                .andExpect(jsonPath("$.data.groups[0].offeringCount").isNumber())
                .andExpect(jsonPath("$.data.groups[0].classNames").isArray());
    }

    @Test
    @DisplayName("TC-US08-02 正常开课但无资源的课程也出现在备课视图并标记缺资源")
    @Transactional
    void prepViewIncludesCourseWithoutResources() throws Exception {
        // 为教师 1 新增一个正常开课、暂无资源的教学班（课程 6）
        jdbcTemplate.update("INSERT INTO offering (id, offering_code, course_id, semester, class_name, "
                + "teacher_id, department_code, capacity, student_count, offering_status, schedule_info, "
                + "created_at, updated_at) VALUES (8, 'OFF-US08-EMPTY', 6, '2025-2026-1', '测试缺资源班', "
                + "1, 'CS', 40, 0, '正常开课', '周五 1-2 节', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        String token = login("teacher1");
        mockMvc.perform(get("/api/resources/prep").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupCount").value(4))
                .andExpect(jsonPath("$.data.courseCount").value(4))
                .andExpect(jsonPath("$.data.coursesWithoutResources").value(1))
                .andExpect(jsonPath("$.data.averageResourcesPerCourse").value(1.0))
                .andExpect(jsonPath("$.data.groups[?(@.courseId==6)].resourceCount").value(0))
                .andExpect(jsonPath("$.data.groups[?(@.courseId==6)].missingResources").value(true));
    }
}
