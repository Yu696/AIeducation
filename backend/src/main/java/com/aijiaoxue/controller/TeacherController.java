package com.aijiaoxue.controller;

import com.aijiaoxue.audit.AuditLog;
import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.Teacher;
import com.aijiaoxue.entity.view.OfferingView;
import com.aijiaoxue.security.CurrentUserHolder;
import com.aijiaoxue.security.PermissionCodes;
import com.aijiaoxue.service.TeacherService;
import com.aijiaoxue.util.CsvUtils;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 教师信息管理接口（US-02）。
 *
 * <p>契约见 docs/API契约.md 第 4 节。教师详情一次性返回课程简介与开课情况，
 * 直接支撑故事原文「查看5位教师的课程简介与开课情况，以便统筹排课」。
 */
@RestController
@RequestMapping("/api/teachers")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    /** 教师列表分页 */
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.TEACHER_READ + "')")
    public ApiResponse<PageResult<Teacher>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) String title) {
        return ApiResponse.ok(teacherService.page(page, size, keyword, departmentCode, title,
                CurrentUserHolder.require()));
    }

    /** 教师详情：基本信息 + 课程简介清单 + 开课清单 + 汇总指标 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TEACHER_READ + "')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(teacherService.detail(id, CurrentUserHolder.require()));
    }

    /** 该教师的开课清单 */
    @GetMapping("/{id}/offerings")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TEACHER_READ + "')")
    public ApiResponse<List<OfferingView>> offerings(@PathVariable Long id) {
        return ApiResponse.ok(teacherService.offeringsOf(id, CurrentUserHolder.require()));
    }

    /** 新建教师 */
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.TEACHER_WRITE + "')")
    @AuditLog(action = "新增", targetType = "TEACHER")
    public ApiResponse<Teacher> create(@RequestBody Teacher teacher) {
        return ApiResponse.ok(teacherService.create(teacher));
    }

    /** 修改教师 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TEACHER_WRITE + "')")
    @AuditLog(action = "修改", targetType = "TEACHER", idParamIndex = 0)
    public ApiResponse<Teacher> update(@PathVariable Long id, @RequestBody Teacher teacher) {
        return ApiResponse.ok(teacherService.update(id, teacher));
    }

    /** 删除教师：仍有教学班或负责课程时返回 409 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TEACHER_WRITE + "')")
    @AuditLog(action = "删除", targetType = "TEACHER", idParamIndex = 0)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        teacherService.delete(id);
        return ApiResponse.ok();
    }

    /** 导出教师 CSV */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TEACHER_READ + "')")
    @AuditLog(action = "导出", targetType = "TEACHER")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String departmentCode,
                       @RequestParam(required = false) String title,
                       HttpServletResponse response) throws IOException {
        List<Teacher> list = teacherService.listForExport(keyword, departmentCode, title,
                CurrentUserHolder.require());
        List<List<String>> rows = new ArrayList<>();
        for (Teacher t : list) {
            List<String> row = new ArrayList<>();
            row.add(t.getTeacherNo());
            row.add(t.getName());
            row.add(t.getGender());
            row.add(t.getTitle());
            row.add(t.getDepartmentCode());
            row.add(t.getDepartmentName());
            row.add(t.getEmail());
            row.add(t.getPhone());
            rows.add(row);
        }
        CsvUtils.write(response, "教师信息导出",
                List.of("工号", "姓名", "性别", "职称", "院系代码", "院系名称", "邮箱", "电话"), rows);
    }

    /** 院系与职称下拉选项 */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('" + PermissionCodes.TEACHER_READ + "')")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(Map.of(
                "departments", teacherService.departments(),
                "titles", teacherService.titles()));
    }
}
