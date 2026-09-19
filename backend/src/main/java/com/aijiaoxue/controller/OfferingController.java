package com.aijiaoxue.controller;

import com.aijiaoxue.audit.AuditLog;
import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.Offering;
import com.aijiaoxue.entity.StudentCount;
import com.aijiaoxue.entity.view.OfferingView;
import com.aijiaoxue.security.CurrentUserHolder;
import com.aijiaoxue.security.PermissionCodes;
import com.aijiaoxue.service.OfferingService;
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
 * 开课与授课管理接口（US-02 / US-03 / US-04）。
 *
 * <p>契约见 docs/API契约.md 第 3 节。教师角色调用时数据范围自动收窄为本人教学班。
 */
@RestController
@RequestMapping("/api/offerings")
public class OfferingController {

    private final OfferingService offeringService;

    public OfferingController(OfferingService offeringService) {
        this.offeringService = offeringService;
    }

    /** 开课列表分页：关键词/学期/教师/院系/状态/是否已督导筛选 */
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_READ + "')")
    public ApiResponse<PageResult<OfferingView>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) String offeringStatus,
            @RequestParam(required = false) Integer supervised) {
        return ApiResponse.ok(offeringService.page(page, size, keyword, semester, teacherId,
                departmentCode, offeringStatus, supervised, CurrentUserHolder.require()));
    }

    /** 开课详情：含任课教师、学生人次明细、评价数 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_READ + "')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(offeringService.detail(id, CurrentUserHolder.require()));
    }

    /** 新建开课 */
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_WRITE + "')")
    @AuditLog(action = "新增", targetType = "OFFERING")
    public ApiResponse<Offering> create(@RequestBody Offering offering,
                                        @RequestParam(required = false) Long coTeacherId,
                                        @RequestParam(required = false) String coTeacherRole) {
        return ApiResponse.ok(offeringService.create(offering, coTeacherId, coTeacherRole));
    }

    /** 修改开课 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_WRITE + "')")
    @AuditLog(action = "修改", targetType = "OFFERING", idParamIndex = 0)
    public ApiResponse<Offering> update(@PathVariable Long id, @RequestBody Offering offering,
                                        @RequestParam(required = false) Long coTeacherId,
                                        @RequestParam(required = false) String coTeacherRole) {
        return ApiResponse.ok(offeringService.update(id, offering, coTeacherId, coTeacherRole));
    }

    /** 删除开课（级联清理听评课记录、任课教师关联与人数明细） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_WRITE + "')")
    @AuditLog(action = "删除", targetType = "OFFERING", idParamIndex = 0)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        offeringService.delete(id);
        return ApiResponse.ok();
    }

    /** 该教学班的学生人次明细 */
    @GetMapping("/{id}/student-counts")
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_READ + "')")
    public ApiResponse<List<StudentCount>> studentCounts(@PathVariable Long id) {
        return ApiResponse.ok(offeringService.studentCounts(id, CurrentUserHolder.require()));
    }

    /** 新增/更新学生人次记录，并同步教学班汇总人数 */
    @PostMapping("/{id}/student-counts")
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_WRITE + "')")
    @AuditLog(action = "新增", targetType = "STUDENT_COUNT", idParamIndex = 0)
    public ApiResponse<StudentCount> saveStudentCount(@PathVariable Long id, @RequestBody StudentCount count) {
        return ApiResponse.ok(offeringService.saveStudentCount(id, count));
    }

    /** 导出开课 CSV */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_READ + "')")
    @AuditLog(action = "导出", targetType = "OFFERING")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String semester,
                       @RequestParam(required = false) Long teacherId,
                       @RequestParam(required = false) String departmentCode,
                       @RequestParam(required = false) String offeringStatus,
                       @RequestParam(required = false) Integer supervised,
                       HttpServletResponse response) throws IOException {
        List<OfferingView> list = offeringService.listForExport(keyword, semester, teacherId,
                departmentCode, offeringStatus, supervised, CurrentUserHolder.require());
        List<List<String>> rows = new ArrayList<>();
        for (OfferingView o : list) {
            List<String> row = new ArrayList<>();
            row.add(o.getOfferingCode());
            row.add(o.getCourseCode());
            row.add(o.getCourseName());
            row.add(o.getCourseType());
            row.add(o.getSemester());
            row.add(o.getClassName());
            row.add(o.getTeacherName());
            row.add(o.getDepartmentCode());
            row.add(o.getDepartmentName());
            row.add(o.getCapacity() == null ? "" : String.valueOf(o.getCapacity()));
            row.add(o.getStudentCount() == null ? "" : String.valueOf(o.getStudentCount()));
            row.add(o.getOfferingStatus());
            row.add(o.getSupervised() != null && o.getSupervised() == 1 ? "已督导" : "未督导");
            row.add(o.getSupervisionTimes() == null ? "0" : String.valueOf(o.getSupervisionTimes()));
            row.add(o.getScheduleInfo());
            rows.add(row);
        }
        List<String> headers = List.of("教学班编号", "课程编号", "课程名称", "课程类型", "学期", "教学班名称",
                "主讲教师", "院系代码", "院系名称", "容量", "学生人数", "开课状态", "督导状态", "听课次数", "上课时间地点");
        CsvUtils.write(response, "开课情况导出", headers, rows);
    }

    /** 学期下拉选项 */
    @GetMapping("/semesters")
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_READ + "')")
    public ApiResponse<List<String>> semesters() {
        return ApiResponse.ok(offeringService.semesters());
    }

    /** 开课状态候选值 */
    @GetMapping("/statuses")
    @PreAuthorize("hasAuthority('" + PermissionCodes.OFFERING_READ + "')")
    public ApiResponse<List<String>> statuses() {
        return ApiResponse.ok(List.of("正常开课", "停开", "已结课"));
    }
}
