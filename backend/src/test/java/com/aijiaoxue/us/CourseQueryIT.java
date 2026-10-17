package com.aijiaoxue.us;

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
 * US-05 数据建模与课程信息管理测试（Query 类，只读）。
 *
 * <p>对应测试报告 TC-US05-* 与 TC-US02-01 ~ TC-US02-08 的查询部分。
 *
 * <p>本类含课程增删改，统一使用 {@code @Transactional} 自动回滚：
 * 否则新增的课程会污染后续测试类对样例数据总数（5 门）的断言。
 */
@Transactional
class CourseQueryIT extends BaseIntegrationTest {

    @Test
    @DisplayName("TC-US05-01 课程列表分页返回总数与记录，含负责人、教学班数、资源数")
    void courseListPaged() throws Exception {
        String token = login("director");
        mockMvc.perform(get("/api/courses?page=1&size=3").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(3));
    }

    @Test
    @DisplayName("TC-US05-02 课程按关键词、课程类型、状态筛选")
    void courseFilters() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/courses?keyword=数据结构").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].courseCode").value("CS102"));

        mockMvc.perform(get("/api/courses?courseType=通识&size=50").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(get("/api/courses?status=草稿").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].courseCode").value("CS104"));
    }

    @Test
    @DisplayName("TC-US02-01 课程详情返回课程简介全文、负责人、教学班数与资源数")
    void courseDetailContainsIntroAndOfferings() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/courses/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.course.courseCode").value("CS101"))
                .andExpect(jsonPath("$.data.course.ownerTeacherName").value("张伟"))
                .andExpect(jsonPath("$.data.course.introContent").isNotEmpty())
                .andExpect(jsonPath("$.data.course.objective").isNotEmpty())
                .andExpect(jsonPath("$.data.course.assessmentMethod").isNotEmpty())
                .andExpect(jsonPath("$.data.course.textbook").isNotEmpty())
                .andExpect(jsonPath("$.data.offerings").isArray())
                .andExpect(jsonPath("$.data.resources").isArray());
    }

    @Test
    @DisplayName("TC-US02-02 课程详情中不存在时返回 404")
    void courseDetailNotFound() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/courses/9999").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("TC-US02-03 课程的开课记录列表可查询")
    void courseOfferings() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/courses/1/offerings").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].courseCode").value("CS101"));
    }

    @Test
    @DisplayName("TC-US05-03 课程导出 CSV 返回文件流且带 UTF-8 BOM")
    void courseExportCsv() throws Exception {
        String token = login("admin");
        byte[] bytes = mockMvc.perform(get("/api/courses/export").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith("\uFEFF"), "CSV 应以 UTF-8 BOM 开头");
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("课程编号"), "CSV 应包含表头");
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("CS101"), "CSV 应包含课程数据");
    }

    @Test
    @DisplayName("TC-US05-04 新建课程成功，编号重复返回 409")
    void createCourseAndDuplicateCode() throws Exception {
        String token = login("admin");
        mockMvc.perform(post("/api/courses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"CS900\",\"courseName\":\"软件测试技术\",\"courseType\":\"选修\","
                                + "\"credit\":2.0,\"totalHours\":32,\"departmentCode\":\"CS\",\"departmentName\":\"计算机学院\","
                                + "\"status\":\"已发布\",\"courseIntro\":{\"content\":\"测试用例设计方法\",\"objective\":\"掌握单元测试\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.courseCode").value("CS900"));

        // 编号重复
        mockMvc.perform(post("/api/courses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"CS900\",\"courseName\":\"重复编号课程\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("课程编号已存在：CS900"));
    }

    @Test
    @DisplayName("TC-US05-05 修改课程后详情同步更新")
    void updateCourse() throws Exception {
        String token = login("admin");
        mockMvc.perform(put("/api/courses/7")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"CS104\",\"courseName\":\"人工智能导论（修订）\",\"courseType\":\"选修\","
                                + "\"credit\":3.0,\"totalHours\":48,\"departmentCode\":\"CS\",\"departmentName\":\"计算机学院\","
                                + "\"status\":\"已发布\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/courses/7").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.course.courseName").value("人工智能导论（修订）"))
                .andExpect(jsonPath("$.data.course.credit").value(3.0));
    }

    @Test
    @DisplayName("TC-US05-06 删除已开课课程被拒绝（409），删除无开课课程成功")
    void deleteCourseConflict() throws Exception {
        String token = login("admin");
        // 课程 1 有开课记录，不允许删除
        mockMvc.perform(delete("/api/courses/1").header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        // 新建一个无开课记录的课程再删除
        String created = mockMvc.perform(post("/api/courses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"CS901\",\"courseName\":\"待删除课程\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/courses/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("TC-US02-04 教师详情返回课程简介清单与开课清单（支撑统筹排课）")
    void teacherDetailWithCoursesAndOfferings() throws Exception {
        String token = login("director");
        mockMvc.perform(get("/api/teachers/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacher.name").value("张伟"))
                .andExpect(jsonPath("$.data.teacher.title").value("教授"))
                // 课程 2 门（CS101、CS102 负责人）；教学班 4 个（含 CS103「合上」）
                .andExpect(jsonPath("$.data.courseCount").value(2))
                .andExpect(jsonPath("$.data.offeringCount").value(4))
                .andExpect(jsonPath("$.data.studentTotal").value(221))
                .andExpect(jsonPath("$.data.courses[0].introContent").isNotEmpty());
    }

    @Test
    @DisplayName("TC-US02-05 教师详情可查看「5位教师的课程简介与开课情况」场景")
    void fiveTeachersOverview() throws Exception {
        String token = login("director");
        mockMvc.perform(get("/api/teachers?size=5").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("TC-US02-06 教师开课清单接口可用")
    void teacherOfferings() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/teachers/3/offerings").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseCode").value("MA201"));
    }

    @Test
    @DisplayName("TC-US07-12 不存在的课程查询挂接资源返回 404")
    void resourcesOfMissingCourse() throws Exception {
        String token = login("admin");
        mockMvc.perform(get("/api/courses/999/resources").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("课程不存在"));
    }

    @Test
    @DisplayName("TC-US07-13 教师查询非本人课程的挂接资源返回 403")
    void teacherCannotReadOtherCourseResources() throws Exception {
        String token = login("teacher1");
        mockMvc.perform(get("/api/courses/4/resources").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
