package com.aijiaoxue.us;

import com.aijiaoxue.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-04 督导覆盖率与工作台测试。
 *
 * <p>对应测试报告 TC-US04-01 ~ TC-US04-10、TC-DASH-01 ~ TC-DASH-04。
 *
 * <p>覆盖率口径（写入数据字典与页面提示）：
 * 覆盖率 = 存在已完成听评课记录的「正常开课」教学班数 ÷ 「正常开课」教学班总数 × 100%。
 * 样例数据实测基线：正常开课 5 个（CS 3 个：CS101/CS102/CS103；MATH 2 个：MA201/MA202），
 * 其中存在已完成听课记录的 2 个（CS101、CS103）。
 * 因此：总体 2/5 = 40.0%；2025-2026-1 学期 2/4 = 50.0%；CS 2/3 = 66.7%；MATH 0/2 = 0.0%。
 */
@Transactional
class CoverageAndDashboardIT extends BaseIntegrationTest {
    @Test
    @DisplayName("TC-US04-01 督导覆盖率总体口径正确（2/5 = 40.0%）")
    void coverageTotal() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(get("/api/supervision/coverage").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalOfferings").value(5))
                .andExpect(jsonPath("$.data.supervisedOfferings").value(2))
                .andExpect(jsonPath("$.data.unsupervisedOfferings").value(3))
                .andExpect(jsonPath("$.data.coverageRate").value(40.0))
                // 计算口径随数据返回，供页面直接展示，避免误读
                .andExpect(jsonPath("$.data.caliber").isNotEmpty());
    }

    @Test
    @DisplayName("TC-US04-02 按学期过滤覆盖率（2025-2026-1 为 2/4 = 50.0%）")
    void coverageBySemesterFilter() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(get("/api/supervision/coverage?semester=2025-2026-1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOfferings").value(4))
                .andExpect(jsonPath("$.data.supervisedOfferings").value(2))
                .andExpect(jsonPath("$.data.coverageRate").value(50.0));
    }

    @Test
    @DisplayName("TC-US04-03 按院系分组覆盖率：CS 66.7%，MATH 0.0%")
    void coverageByDepartmentRows() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(get("/api/supervision/coverage?groupBy=department")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows").isArray())
                // CS：2/3 = 66.7%
                .andExpect(jsonPath("$.data.byDepartment[?(@.groupKey=='CS')].coverageRate").value(66.7))
                // MATH：0/2 = 0.0%
                .andExpect(jsonPath("$.data.byDepartment[?(@.groupKey=='MATH')].coverageRate").value(0.0));
    }

    @Test
    @DisplayName("TC-US04-04 按学期分组覆盖率：两个学期各一行")
    void coverageBySemesterRows() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(get("/api/supervision/coverage?groupBy=semester")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bySemester.length()").value(2))
                .andExpect(jsonPath("$.data.bySemester[0].groupKey").value("2024-2025-2"))
                .andExpect(jsonPath("$.data.bySemester[1].groupKey").value("2025-2026-1"));
    }

    @Test
    @DisplayName("TC-US04-05 覆盖率导出的 CSV 含两个分组维度")
    void coverageExport() throws Exception {
        String token = login("supervisor");
        byte[] bytes = mockMvc.perform(get("/api/supervision/coverage/export")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("分组维度"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("按院系"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("按学期"));
    }

    @Test
    @DisplayName("TC-US04-06 听评课列表分页与状态筛选")
    void supervisionList() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(get("/api/supervisions?size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5));

        mockMvc.perform(get("/api/supervisions?status=已完成&size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(get("/api/supervisions?status=已安排&size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("TC-US04-07 待安排听评课清单只返回未督导的教学班")
    void pendingOfferings() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(get("/api/supervision/coverage/pending").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.supervised == 1)]").doesNotExist())
                // 正常开课中未督导的：教学班 2、5（2025-2026-1）与 7（2024-2025-2）
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @DisplayName("TC-US04-08 登记完成的听评课后覆盖率提升")
    void createFinishedSupervisionIncreasesCoverage() throws Exception {
        String token = login("supervisor");
        // 为教学班 5（MATH，未督导）登记一条已完成听课
        mockMvc.perform(post("/api/supervisions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":5,\"supervisorId\":5,\"status\":\"已完成\","
                                + "\"score\":87.0,\"comment\":\"课堂讲授清晰，建议增加随堂练习。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 状态为已完成时应自动补齐完成日期
                .andExpect(jsonPath("$.data.finishDate").isNotEmpty());

        // 覆盖率从 2/5 提升到 3/5 = 60.0%
        mockMvc.perform(get("/api/supervision/coverage").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supervisedOfferings").value(3))
                .andExpect(jsonPath("$.data.coverageRate").value(60.0));    }

    @Test
    @DisplayName("TC-US04-09 听评课必填校验：缺少教学班或督导教师返回 400")
    void supervisionValidation() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(post("/api/supervisions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请选择要听课的教学班"));

        mockMvc.perform(post("/api/supervisions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请选择督导教师"));
    }

    @Test
    @DisplayName("TC-US04-10 修改听评课为已完成后同步补齐完成日期")
    void updateSupervisionToFinished() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(put("/api/supervisions/4")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"已完成\",\"score\":85.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/supervisions/4").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已完成"))
                .andExpect(jsonPath("$.data.finishDate").isNotEmpty());
    }

    @Test
    @DisplayName("TC-DASH-01 教研室主任工作台返回卡片、教师授课分布与覆盖率")
    void directorDashboard() throws Exception {
        String token = login("director");
        mockMvc.perform(get("/api/dashboard/director").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("DIRECTOR"))
                .andExpect(jsonPath("$.data.departmentCode").value("CS"))
                .andExpect(jsonPath("$.data.cards").isArray())
                .andExpect(jsonPath("$.data.charts.teacherWorkload").isArray())
                .andExpect(jsonPath("$.data.coverage.coverageRate").value(66.7));
    }

    @Test
    @DisplayName("TC-DASH-02 教师工作台返回我的课程数、教学班数、学生人次与评教均分")
    void teacherDashboard() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/dashboard/teacher").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TEACHER"))
                .andExpect(jsonPath("$.data.teacherName").value("张伟"))
                // 卡片：我的课程 3（含合上课程 CS103）、我的教学班 3（正常开课，含合上 CS103）、
                // 学生人次 221（58+55+48+60）、可用教学资源 4（归属本人 3 条 + 合上教学班 1 条；失效 1 条不计）、
                // 评教均分 88.6（本人 CS101、CS102 的学生评教按人次加权：90.3×2 + 86.8×2）
                .andExpect(jsonPath("$.data.cards[0].value").value(3))
                .andExpect(jsonPath("$.data.cards[1].value").value(3))
                .andExpect(jsonPath("$.data.cards[2].value").value(221))
                .andExpect(jsonPath("$.data.cards[3].value").value(4))
                .andExpect(jsonPath("$.data.cards[4].value").value(88.6));
    }

    @Test
    @DisplayName("TC-DASH-03 教学督导工作台返回覆盖率与待安排清单")
    void supervisorDashboard() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(get("/api/dashboard/supervisor").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("SUPERVISOR"))
                .andExpect(jsonPath("$.data.cards").isArray())
                .andExpect(jsonPath("$.data.charts.coverageByDepartment").isArray())
                .andExpect(jsonPath("$.data.pendingOfferings").isArray());
    }

    @Test
    @DisplayName("TC-DASH-04 管理员工作台与 summary 按角色路由正确")
    void adminDashboardAndSummaryRouting() throws Exception {
        String adminToken = login("admin");
        mockMvc.perform(get("/api/dashboard/admin").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.charts.roleDistribution").isArray());

        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        String teacherToken = login("teacher1");
        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TEACHER"));
    }

    // ==================== Sprint 2 US-10：听评课状态机 ====================

    @Test
    @DisplayName("TC-US10-01 督导可加载候选教师，教师访问被拦截")
    void supervisorCandidates() throws Exception {
        String supervisorToken = login("supervisor");
        mockMvc.perform(get("/api/supervisions/supervisors")
                        .header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(5));

        String teacherToken = login("teacher1");
        mockMvc.perform(get("/api/supervisions/supervisors")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-US10-02 待督导清单带出安排状态（已安排/待安排）")
    void pendingListWithSupervisionStatus() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(get("/api/supervision/coverage/pending")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.supervisionStatus=='已安排')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.supervisionStatus=='待安排')]").isNotEmpty());
    }

    @Test
    @DisplayName("TC-US10-03 已安排状态必须填写计划听课日期")
    void arrangedRequiresPlanDate() throws Exception {
        String token = login("supervisor");
        mockMvc.perform(post("/api/supervisions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":5,\"supervisorId\":5,\"status\":\"已安排\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("已安排状态必须填写计划听课日期"));
    }

    @Test
    @DisplayName("TC-US10-04 非法状态、待安排携带日期、非完成状态填评分均被拒绝")
    void invalidStatusAndDateRules() throws Exception {
        String token = login("supervisor");

        mockMvc.perform(post("/api/supervisions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":5,\"supervisorId\":5,\"status\":\"已取消\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("听评课状态不合法：已取消"));

        mockMvc.perform(post("/api/supervisions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":5,\"supervisorId\":5,\"status\":\"待安排\","
                                + "\"planDate\":\"2026-10-20\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("待安排状态不应填写计划听课日期"));

        mockMvc.perform(post("/api/supervisions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":5,\"supervisorId\":5,\"status\":\"已安排\","
                                + "\"planDate\":\"2026-10-20\",\"score\":90.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有已完成状态可以填写听课评分"));
    }

    @Test
    @DisplayName("TC-US10-05 完整状态流转：待安排→已安排→已完成，覆盖率提升")
    void fullStateFlowIncreasesCoverage() throws Exception {
        String token = login("supervisor");

        // 教学班 7 是正常开课且尚无已完成听课记录；先建一条待安排记录
        String created = mockMvc.perform(post("/api/supervisions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":7,\"supervisorId\":5,\"status\":\"待安排\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("待安排"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").path("id").asLong();

        // 待安排 → 已安排：补齐计划日期
        mockMvc.perform(put("/api/supervisions/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"已安排\",\"planDate\":\"2026-10-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已安排"))
                .andExpect(jsonPath("$.data.planDate").isNotEmpty());

        // 已安排 → 已完成：回填完成日期、评分、评语
        mockMvc.perform(put("/api/supervisions/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"已完成\",\"finishDate\":\"2026-10-21\","
                                + "\"score\":90.0,\"comment\":\"状态机闭环验证\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已完成"))
                .andExpect(jsonPath("$.data.finishDate").isNotEmpty());

        // 覆盖率从 2/5 = 40.0% 提升到 3/5 = 60.0%
        mockMvc.perform(get("/api/supervision/coverage")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supervisedOfferings").value(3))
                .andExpect(jsonPath("$.data.coverageRate").value(60.0));
    }

    @Test
    @DisplayName("TC-US10-06 已完成不可回退、完成日期不得早于计划、评分不得越界")
    void transitionAndDateGuards() throws Exception {
        String token = login("supervisor");

        // 已完成（id=1）不能回退为已安排
        mockMvc.perform(put("/api/supervisions/1")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"已安排\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不允许从「已完成」流转到「已安排」"));

        // 已安排（id=2）完成日期早于计划日期（2025-10-09）被拒绝
        mockMvc.perform(put("/api/supervisions/2")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"已完成\",\"finishDate\":\"2020-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("完成日期不能早于计划听课日期"));

        // 评分超过 100 被拒绝
        mockMvc.perform(post("/api/supervisions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":5,\"supervisorId\":5,\"status\":\"已完成\","
                                + "\"score\":101.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("听课评分需在 0—100 之间"));
    }
}
