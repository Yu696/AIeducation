package com.aijiaoxue.us;

import com.aijiaoxue.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    // ---------------- US-07 教学资源挂接与维护闭环 ----------------

    @Test
    @DisplayName("TC-US07-01 课程资源接口只返回已挂接资源，并带教学班名称")
    void courseResourcesOnlyAssociated() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/courses/1/resources").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].className").value("软件2201班"));

        // 课程 2 的基线下只有资源 3 已挂接；资源 6 为软解绑（associated=0），不得返回
        mockMvc.perform(get("/api/courses/2/resources").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(3));
    }

    @Test
    @Transactional
    @DisplayName("TC-US07-02 重复挂接幂等：不新增行、列表不重复")
    void attachIsIdempotent() throws Exception {
        String token = login("admin");
        String body = "{\"resourceId\":5}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/courses/1/resources")
                            .header("Authorization", bearer(token))
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
        mockMvc.perform(get("/api/courses/1/resources").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_resource WHERE course_id = 1 AND resource_id = 5", Integer.class);
        assertEquals(1, rows, "重复挂接不应新增 course_resource 行");
    }

    @Test
    @Transactional
    @DisplayName("TC-US07-03 软解绑：课程/资源详情不再返回，挂接行保留且 associated=0")
    void detachIsSoftDelete() throws Exception {
        String token = login("admin");
        mockMvc.perform(delete("/api/courses/1/resources/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/courses/1/resources").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(2));

        Integer associated = jdbcTemplate.queryForObject(
                "SELECT associated FROM course_resource WHERE course_id = 1 AND resource_id = 1", Integer.class);
        assertEquals(0, associated, "解绑应保留挂接行并置 associated=0");
        // 资源本身不被删除，仍可在资源管理中查看
        mockMvc.perform(get("/api/resources/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    @DisplayName("TC-US07-04 解绑后可重新挂接：复用原行，不新增记录")
    void reattachAfterDetach() throws Exception {
        String token = login("admin");
        mockMvc.perform(delete("/api/courses/1/resources/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/1/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/courses/1/resources").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_resource WHERE course_id = 1 AND resource_id = 1", Integer.class);
        Integer associated = jdbcTemplate.queryForObject(
                "SELECT associated FROM course_resource WHERE course_id = 1 AND resource_id = 1", Integer.class);
        assertEquals(1, rows);
        assertEquals(1, associated);
    }

    @Test
    @Transactional
    @DisplayName("TC-US07-05 对已解绑资源再次解绑幂等返回成功")
    void detachTwiceIsIdempotent() throws Exception {
        String token = login("admin");
        // 资源 6 在基线中已是 associated=0
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/courses/2/resources/6").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Test
    @DisplayName("TC-US07-06 挂接参数与对象不存在时返回 400/404")
    void attachValidatesParams() throws Exception {
        String token = login("admin");
        mockMvc.perform(post("/api/courses/1/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("资源ID不能为空"));

        mockMvc.perform(post("/api/courses/999/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("课程不存在"));

        mockMvc.perform(post("/api/courses/1/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("教学资源不存在"));
    }

    @Test
    @Transactional
    @DisplayName("TC-US07-07 挂接/解绑写入操作日志：COURSE_RESOURCE + 资源ID")
    void attachWritesAuditLog() throws Exception {
        String token = login("admin");
        mockMvc.perform(post("/api/courses/1/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":5}"))
                .andExpect(status().isOk());

        String action = jdbcTemplate.queryForObject(
                "SELECT action FROM sys_audit_log WHERE target_type = 'COURSE_RESOURCE' "
                        + "ORDER BY id DESC LIMIT 1", String.class);
        String targetId = jdbcTemplate.queryForObject(
                "SELECT target_id FROM sys_audit_log WHERE target_type = 'COURSE_RESOURCE' "
                        + "ORDER BY id DESC LIMIT 1", String.class);
        String detail = jdbcTemplate.queryForObject(
                "SELECT detail FROM sys_audit_log WHERE target_type = 'COURSE_RESOURCE' "
                        + "ORDER BY id DESC LIMIT 1", String.class);
        assertEquals("挂接资源", action);
        assertEquals("1", targetId);
        assertTrue(detail != null && detail.contains("5"), "审计详情应包含资源ID");
    }

    @Test
    @Transactional
    @DisplayName("TC-US07-08 解绑后资源不可见、课程保留缺资源提示，重新挂接后恢复")
    void detachRemovesFromPrepView() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(delete("/api/courses/2/resources/3").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/resources/prep").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                // 资源 3 不再出现（总数由 4 降为 3）
                .andExpect(jsonPath("$.data.totalResources").value(3))
                // US-08 语义：课程分组仍保留，但标记缺资源
                .andExpect(jsonPath("$.data.groupCount").value(3))
                .andExpect(jsonPath("$.data.coursesWithoutResources").value(1))
                .andExpect(jsonPath("$.data.groups[?(@.courseId==2)].resourceCount").value(0))
                .andExpect(jsonPath("$.data.groups[?(@.courseId==2)].missingResources").value(true));

        mockMvc.perform(post("/api/courses/2/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":3}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/resources/prep").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalResources").value(4))
                .andExpect(jsonPath("$.data.groupCount").value(3))
                .andExpect(jsonPath("$.data.coursesWithoutResources").value(0))
                .andExpect(jsonPath("$.data.groups[?(@.courseId==2)].resourceCount").value(1))
                .andExpect(jsonPath("$.data.groups[?(@.courseId==2)].missingResources").value(false));
    }

    @Test
    @DisplayName("TC-US07-09 教师越权挂接返回 403")
    void teacherCannotAttachOutOfScope() throws Exception {
        String token = login("teacher1");
        // 资源 5 属于数学学院教师，teacher1 不可见
        mockMvc.perform(post("/api/courses/1/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 课程 4 不属于 teacher1
        mockMvc.perform(post("/api/courses/4/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @Transactional
    @DisplayName("TC-US07-10 教师资源可见范围与 owner-only 维护约束")
    void teacherResourceOwnership() throws Exception {
        String token = login("teacher1");
        // 资源 5 不在本人可见范围
        mockMvc.perform(get("/api/resources/5").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        // 资源 4 属合上教学班，可查看
        mockMvc.perform(get("/api/resources/4").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        // 但不可修改、不可删除他人资源
        mockMvc.perform(put("/api/resources/4")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceName\":\"越权修改\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/resources/4").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        // 本人资源可修改
        mockMvc.perform(put("/api/resources/1")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceName\":\"程序设计基础-第1章课件（更新）\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    @DisplayName("TC-US07-11 教师新建资源强制归属本人，忽略请求体 teacherId")
    void teacherCreateForcesOwnership() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(post("/api/resources")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceName\":\"教师自建资源\",\"resourceType\":\"课件\","
                                + "\"fileUrl\":\"/files/self.pdf\",\"teacherId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherId").value(1));
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

    // ---------------- Sprint 2 US-09：评教分析与统计导出 ----------------

    @Test
    @DisplayName("TC-US09-01 评教分析支持类型/课程/教师/学期筛选")
    void evaluationAnalysisFilters() throws Exception {
        String token = login("admin");

        mockMvc.perform(get("/api/evaluations/summary").param("evaluationType", "全部")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvaluations").value(7));

        mockMvc.perform(get("/api/evaluations/summary").param("evaluationType", "督导评教")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvaluations").value(1));

        mockMvc.perform(get("/api/evaluations/summary").param("courseId", "1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvaluations").value(2));

        mockMvc.perform(get("/api/evaluations/summary").param("teacherId", "1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvaluations").value(4));

        mockMvc.perform(get("/api/evaluations/summary").param("semester", "2025-2026-1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvaluations").value(5));
    }

    @Test
    @DisplayName("TC-US09-02 教师汇总按参评人次加权，趋势与课程汇总同时返回")
    void evaluationTrendAndTeacherSummary() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/evaluations/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                // 学生评教均在 2025-2026-1，当前只有一个学期
                .andExpect(jsonPath("$.data.trend.length()").value(1))
                .andExpect(jsonPath("$.data.trend[0].semester").value("2025-2026-1"))
                // 教师 1：CS101 2 条 + CS102 2 条 = 4 人次，加权平均 (92.5+88+95+78.5)/4 = 88.5
                .andExpect(jsonPath("$.data.teacherSummary[?(@.teacherId==1)].courseCount").value(2))
                .andExpect(jsonPath("$.data.teacherSummary[?(@.teacherId==1)].evaluationCount").value(4))
                .andExpect(jsonPath("$.data.teacherSummary[?(@.teacherId==1)].avgScore").value(88.5));
    }

    @Test
    @Transactional
    @DisplayName("TC-US09-03 新增历史学期评价后趋势出现两个学期")
    void evaluationTrendWithHistory() throws Exception {
        jdbcTemplate.update("INSERT INTO evaluation (id, offering_id, evaluation_type, semester, score, "
                + "evaluator, content, evaluated_at, created_at) VALUES (8, 1, '学生评教', "
                + "'2024-2025-2', 80.0, '学生(匿名)', '历史学期样例', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        String token = login("admin");
        mockMvc.perform(get("/api/evaluations/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvaluations").value(6))
                .andExpect(jsonPath("$.data.trend.length()").value(2))
                .andExpect(jsonPath("$.data.trend[0].semester").value("2024-2025-2"))
                .andExpect(jsonPath("$.data.trend[0].avgScore").value(80.0))
                .andExpect(jsonPath("$.data.trend[1].semester").value("2025-2026-1"));
    }

    @Test
    @DisplayName("TC-US09-04 评教分析导出含课程/学期/教师三个维度")
    void evaluationSummaryExport() throws Exception {
        String token = login("admin");
        byte[] bytes = mockMvc.perform(get("/api/evaluations/summary/export")
                        .param("evaluationType", "全部")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("分析维度"));
        assertTrue(csv.contains("按课程"));
        assertTrue(csv.contains("按学期"));
        assertTrue(csv.contains("按教师"));
    }

    @Test
    @DisplayName("TC-US09-05 教师评教分析只包含本人数据")
    void evaluationTeacherScope() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/evaluations/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvaluations").value(4))
                .andExpect(jsonPath("$.data.summary[?(@.teacherId != 1)]").doesNotExist())
                .andExpect(jsonPath("$.data.teacherSummary[?(@.teacherId != 1)]").doesNotExist());
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
