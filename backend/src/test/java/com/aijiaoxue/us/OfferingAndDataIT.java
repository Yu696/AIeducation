package com.aijiaoxue.us;

import com.aijiaoxue.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-02 / US-03 / US-05 开课、学生人次与数据导入测试。
 *
 * <p>对应测试报告 TC-US02-09 ~ TC-US02-16、TC-US03-01 ~ TC-US03-04、TC-US05-07 ~ TC-US05-09。
 */
class OfferingAndDataIT extends BaseIntegrationTest {

    // ---------------- 开课查询（US-02） ----------------

    @Test
    @DisplayName("TC-US02-09 开课列表分页返回总数，含课程名、教师名与督导状态")
    void offeringList() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/offerings?size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.records[0].courseName").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].teacherName").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].supervised").isNumber());
    }

    @Test
    @DisplayName("TC-US02-10 开课按学期、状态、是否已督导筛选")
    void offeringFilters() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/offerings?semester=2025-2026-1&size=100")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5));

        mockMvc.perform(get("/api/offerings?offeringStatus=停开").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].offeringCode").value("OFF-2025-1-CS104-01"));

        // 已督导（存在已完成听课记录）：教学班 1、3、6
        mockMvc.perform(get("/api/offerings?supervised=1&size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        // 未督导：教学班 2、4、5、7
        mockMvc.perform(get("/api/offerings?supervised=0&size=100").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4));    }

    @Test
    @DisplayName("TC-US02-11 开课详情返回学生人次明细与任课教师")
    void offeringDetail() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/offerings/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offering.offeringCode").value("OFF-2025-1-CS101-01"))
                .andExpect(jsonPath("$.data.offering.courseName").value("程序设计基础"))
                .andExpect(jsonPath("$.data.studentCounts.length()").value(2))
                .andExpect(jsonPath("$.data.teachers.length()").value(1));
    }

    @Test
    @DisplayName("TC-US03-01 教师查看本人教学班的学生人次（按教学班汇总）")
    void teacherStudentCounts() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/offerings/1/student-counts").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].className").isNotEmpty())
                .andExpect(jsonPath("$.data[0].studentNum").isNumber());
    }

    @Test
    @DisplayName("TC-US03-02 教师查看他人教学班详情被拦截（403）")
    void teacherCannotViewOthersOffering() throws Exception {
        String token = login("teacher1");
        // 教学班 5 属于教师 3（王强）
        mockMvc.perform(get("/api/offerings/5").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ---------------- 开课维护（US-02） ----------------

    @Test
    @DisplayName("TC-US02-12 新建开课成功并自动继承课程院系")
    @Transactional
    void createOffering() throws Exception {
        String token = login("admin");
        mockMvc.perform(post("/api/offerings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringCode\":\"OFF-2025-1-CS102-02\",\"courseId\":2,\"semester\":\"2025-2026-1\","
                                + "\"className\":\"软件2203班\",\"teacherId\":2,\"capacity\":55,\"offeringStatus\":\"正常开课\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.offeringCode").value("OFF-2025-1-CS102-02"))
                // 未显式传 departmentCode，应由课程院系自动补齐
                .andExpect(jsonPath("$.data.departmentCode").value("CS"));
    }

    @Test
    @DisplayName("TC-US02-13 教学班编号重复返回 409")
    @Transactional
    void createOfferingDuplicateCode() throws Exception {
        String token = login("admin");
        mockMvc.perform(post("/api/offerings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringCode\":\"OFF-2025-1-CS101-01\",\"courseId\":1,\"semester\":\"2025-2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("TC-US02-14 新建开课缺少课程 ID 返回参数错误（400）")
    @Transactional
    void createOfferingMissingCourse() throws Exception {
        String token = login("admin");
        mockMvc.perform(post("/api/offerings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringCode\":\"OFF-X-001\",\"semester\":\"2025-2026-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请选择开课课程"));
    }

    @Test
    @DisplayName("TC-US02-15 修改开课状态后详情同步")
    @Transactional
    void updateOffering() throws Exception {
        String token = login("admin");
        mockMvc.perform(put("/api/offerings/4")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringCode\":\"OFF-2025-1-CS104-01\",\"courseId\":7,\"semester\":\"2025-2026-1\","
                                + "\"className\":\"软件2202班\",\"teacherId\":2,\"capacity\":45,\"offeringStatus\":\"正常开课\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/offerings/4").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offering.offeringStatus").value("正常开课"));
    }

    @Test
    @DisplayName("TC-US02-16 删除开课同时清理听评课记录")
    @Transactional
    void deleteOffering() throws Exception {
        String token = login("admin");
        mockMvc.perform(delete("/api/offerings/3").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/offerings/3").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        // 关联的听评课记录应一并清理
        mockMvc.perform(get("/api/supervisions?offeringId=3").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ---------------- 学生人次录入（US-03） ----------------

    @Test
    @DisplayName("TC-US03-03 录入学生人次后教学班汇总人数同步刷新")
    @Transactional
    void saveStudentCountRefreshesTotal() throws Exception {
        String token = login("admin");
        // 教学班 5 原有人数明细 76，追加 4 后应为 80
        mockMvc.perform(post("/api/offerings/5/student-counts")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statDate\":\"2025-09-20\",\"className\":\"计算机2202班\",\"major\":\"计算机科学\",\"studentNum\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/offerings/5").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offering.studentCount").value(80));
    }

    @Test
    @DisplayName("TC-US03-04 开课导出 CSV 包含督导状态列")
    void offeringExportCsv() throws Exception {
        String token = login("admin");
        byte[] bytes = mockMvc.perform(get("/api/offerings/export").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(bytes, StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("督导状态"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("OFF-2025-1-CS101-01"));
    }

    // ---------------- 数据导入（US-05） ----------------

    @Test
    @DisplayName("TC-US05-07 CSV 批量导入课程：新增与更新分别计数")
    @Transactional
    void importCoursesCsv() throws Exception {
        String token = login("admin");
        String csv = "课程编号,课程名称,课程类型,学分,总学时,院系代码,院系名称,课程负责人,状态,课程简介\n"
                + "CS950,编译原理,必修,3.0,48,CS,计算机学院,张伟,已发布,词法与语法分析\n"
                + "CS101,程序设计基础,必修,4.0,64,CS,计算机学院,张伟,已发布,导入更新已有课程\n"
                + ",缺编号课程,选修,2.0,32,CS,计算机学院,李娜,草稿,应被跳过\n";
        MockMultipartFile file = new MockMultipartFile("file", "courses.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/courses/import").file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.inserted").value(1))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1));

        // 新增的课程可查询到
        mockMvc.perform(get("/api/courses?keyword=编译原理").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("TC-US05-08 空 CSV 文件导入被拒绝（400）")
    @Transactional
    void importEmptyCsvRejected() throws Exception {
        String token = login("admin");
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv",
                "课程编号,课程名称\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/courses/import").file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("CSV 文件中没有可导入的数据行"));
    }

    @Test
    @DisplayName("TC-US05-09 课程导入模板可下载")
    void importTemplateDownloadable() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/courses/import-template").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
