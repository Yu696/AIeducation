package com.aijiaoxue.controller;

import com.aijiaoxue.audit.AuditLog;
import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.dto.CourseSaveRequest;
import com.aijiaoxue.dto.ImportResult;
import com.aijiaoxue.entity.Course;
import com.aijiaoxue.entity.view.CourseDetailView;
import com.aijiaoxue.entity.view.OfferingView;
import com.aijiaoxue.entity.view.ResourceView;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.CurrentUserHolder;
import com.aijiaoxue.security.PermissionCodes;
import com.aijiaoxue.service.CourseService;
import com.aijiaoxue.service.OfferingService;
import com.aijiaoxue.service.TeacherService;
import com.aijiaoxue.util.CsvUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程信息管理接口（史诗一「查课程」，US-02 / US-05）。
 *
 * <p>契约见 docs/API契约.md 第 2 节。
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final OfferingService offeringService;
    private final TeacherService teacherService;

    public CourseController(CourseService courseService, OfferingService offeringService,
                            TeacherService teacherService) {
        this.courseService = courseService;
        this.offeringService = offeringService;
        this.teacherService = teacherService;
    }

    /** 课程列表分页：关键词/院系/类型/状态筛选 */
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.COURSE_READ + "')")
    public ApiResponse<PageResult<CourseDetailView>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) String courseType,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(courseService.page(page, size, keyword, departmentCode, courseType, status,
                CurrentUserHolder.require()));
    }

    /** 课程详情：课程 + 简介全文 + 负责人 + 教学班 + 已挂接资源 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COURSE_READ + "')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(courseService.detail(id, CurrentUserHolder.require()));
    }

    /** 新建课程 */
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.COURSE_WRITE + "')")
    @AuditLog(action = "新增", targetType = "COURSE")
    public ApiResponse<Course> create(@Valid @RequestBody CourseSaveRequest request) {
        return ApiResponse.ok(courseService.create(toEntity(request),
                introOf(request, "content"), introOf(request, "objective"),
                introOf(request, "assessmentMethod"), introOf(request, "textbook")));
    }

    /** 修改课程 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COURSE_WRITE + "')")
    @AuditLog(action = "修改", targetType = "COURSE", idParamIndex = 0)
    public ApiResponse<Course> update(@PathVariable Long id, @Valid @RequestBody CourseSaveRequest request) {
        return ApiResponse.ok(courseService.update(id, toEntity(request),
                introOf(request, "content"), introOf(request, "objective"),
                introOf(request, "assessmentMethod"), introOf(request, "textbook")));
    }

    /** 删除课程：已开课则返回 409 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COURSE_WRITE + "')")
    @AuditLog(action = "删除", targetType = "COURSE", idParamIndex = 0)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return ApiResponse.ok();
    }

    /** 该课程的全部开课记录 */
    @GetMapping("/{id}/offerings")
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_READ + "')")
    public ApiResponse<List<OfferingView>> offerings(@PathVariable Long id) {
        return ApiResponse.ok(courseService.offeringsOf(id, CurrentUserHolder.require()));
    }

    /** 该课程已挂接的资源列表（US-07）；与课程详情同权限码，COURSE_ADMIN 也可读取 */
    @GetMapping("/{id}/resources")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COURSE_READ + "')")
    public ApiResponse<List<ResourceView>> resources(@PathVariable Long id) {
        return ApiResponse.ok(courseService.resourcesOf(id, CurrentUserHolder.require()));
    }

    /** 挂接教学资源到课程 */
    @PostMapping("/{id}/resources")
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_WRITE + "')")
    @AuditLog(action = "挂接资源", targetType = "COURSE_RESOURCE", idParamIndex = 0)
    public ApiResponse<Void> attachResource(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        courseService.attachResource(id, body.get("resourceId"), CurrentUserHolder.require());
        return ApiResponse.ok();
    }

    /** 解除课程与资源的挂接 */
    @DeleteMapping("/{id}/resources/{resourceId}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_WRITE + "')")
    @AuditLog(action = "解除挂接", targetType = "COURSE_RESOURCE", idParamIndex = 0)
    public ApiResponse<Void> detachResource(@PathVariable Long id, @PathVariable Long resourceId) {
        courseService.detachResource(id, resourceId, CurrentUserHolder.require());
        return ApiResponse.ok();
    }

    /** 导出课程 CSV */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COURSE_READ + "')")
    @AuditLog(action = "导出", targetType = "COURSE")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String departmentCode,
                       @RequestParam(required = false) String courseType,
                       @RequestParam(required = false) String status,
                       HttpServletResponse response) throws IOException {
        CurrentUser user = CurrentUserHolder.require();
        List<CourseDetailView> list = courseService.listForExport(keyword, departmentCode, courseType, status, user);
        List<List<String>> rows = new ArrayList<>();
        for (CourseDetailView c : list) {
            List<String> row = new ArrayList<>();
            row.add(c.getCourseCode());
            row.add(c.getCourseName());
            row.add(c.getCourseType());
            row.add(c.getCredit() == null ? "" : c.getCredit().toPlainString());
            row.add(c.getTotalHours() == null ? "" : String.valueOf(c.getTotalHours()));
            row.add(c.getDepartmentCode());
            row.add(c.getDepartmentName());
            row.add(c.getOwnerTeacherName());
            row.add(c.getStatus());
            row.add(c.getOfferingCount() == null ? "0" : String.valueOf(c.getOfferingCount()));
            row.add(c.getResourceCount() == null ? "0" : String.valueOf(c.getResourceCount()));
            row.add(c.getIntro());
            rows.add(row);
        }
        CsvUtils.write(response, "课程信息导出", CourseService.exportHeaders(), rows);
    }

    /** CSV 批量导入课程（US-05 数据导入） */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COURSE_WRITE + "')")
    @AuditLog(action = "导入", targetType = "COURSE")
    public ApiResponse<ImportResult> importCsv(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(courseService.importCsv(file));
    }

    /** 课程导入模板 CSV 下载，降低填写出错率 */
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COURSE_READ + "')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("CS9001", "示例课程", "选修", "2.0", "32", "CS", "计算机学院",
                "张伟", "已发布", "这是一门用于演示导入功能的示例课程"));
        CsvUtils.write(response, "课程导入模板", CourseService.exportHeaders().subList(0, 10), rows);
    }

    /** 课程表单下拉选项：院系、课程类型、状态、负责人候选教师 */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COURSE_READ + "')")
    public ApiResponse<Map<String, Object>> options() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("departments", teacherService.departments());
        data.put("courseTypes", List.of("必修", "选修", "通识"));
        data.put("statuses", List.of("草稿", "已发布", "已归档"));
        data.put("semesters", offeringService.semesters());
        // 负责人候选：教师列表（仅用于下拉选择，数量有限）
        data.put("teachers", teacherService.listForExport(null, null, null, CurrentUserHolder.require())
                .stream().map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("departmentName", t.getDepartmentName());
                    return m;
                }).toList());
        return ApiResponse.ok(data);
    }

    /** 请求体转课程实体 */
    private Course toEntity(CourseSaveRequest request) {
        Course course = new Course();
        course.setCourseCode(request.getCourseCode());
        course.setCourseName(request.getCourseName());
        course.setCourseType(request.getCourseType());
        course.setCredit(request.getCredit());
        course.setTotalHours(request.getTotalHours());
        course.setDepartmentCode(request.getDepartmentCode());
        course.setDepartmentName(request.getDepartmentName());
        course.setOwnerTeacherId(request.getOwnerTeacherId());
        course.setIntro(request.getIntro());
        course.setStatus(request.getStatus());
        return course;
    }

    /** 安全取课程简介子对象字段 */
    private String introOf(CourseSaveRequest request, String field) {
        if (request.getCourseIntro() == null) {
            return null;
        }
        return switch (field) {
            case "content" -> request.getCourseIntro().getContent();
            case "objective" -> request.getCourseIntro().getObjective();
            case "assessmentMethod" -> request.getCourseIntro().getAssessmentMethod();
            case "textbook" -> request.getCourseIntro().getTextbook();
            default -> null;
        };
    }
}
