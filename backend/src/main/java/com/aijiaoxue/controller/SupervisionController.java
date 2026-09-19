package com.aijiaoxue.controller;

import com.aijiaoxue.audit.AuditLog;
import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.Supervision;
import com.aijiaoxue.entity.view.SupervisionView;
import com.aijiaoxue.security.CurrentUserHolder;
import com.aijiaoxue.security.PermissionCodes;
import com.aijiaoxue.service.SupervisionService;
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

/**
 * 听评课（督导）接口（US-04）。
 *
 * <p>契约见 docs/API契约.md 第 7 节。
 */
@RestController
@RequestMapping("/api/supervisions")
public class SupervisionController {

    private final SupervisionService supervisionService;

    public SupervisionController(SupervisionService supervisionService) {
        this.supervisionService = supervisionService;
    }

    /** 听评课列表分页 */
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.SUPERVISION_READ + "')")
    public ApiResponse<PageResult<SupervisionView>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long offeringId,
            @RequestParam(required = false) Long supervisorId,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(supervisionService.page(page, size, keyword, offeringId, supervisorId,
                semester, status, CurrentUserHolder.require()));
    }

    /** 听评课详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.SUPERVISION_READ + "')")
    public ApiResponse<SupervisionView> detail(@PathVariable Long id) {
        return ApiResponse.ok(supervisionService.detail(id));
    }

    /** 安排/登记听评课 */
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.SUPERVISION_WRITE + "')")
    @AuditLog(action = "新增", targetType = "SUPERVISION")
    public ApiResponse<Supervision> create(@RequestBody Supervision supervision) {
        return ApiResponse.ok(supervisionService.create(supervision));
    }

    /** 更新听评课结果（填写完成日期后计入督导覆盖率分子） */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.SUPERVISION_WRITE + "')")
    @AuditLog(action = "修改", targetType = "SUPERVISION", idParamIndex = 0)
    public ApiResponse<Supervision> update(@PathVariable Long id, @RequestBody Supervision supervision) {
        return ApiResponse.ok(supervisionService.update(id, supervision));
    }

    /** 删除听评课记录 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.SUPERVISION_WRITE + "')")
    @AuditLog(action = "删除", targetType = "SUPERVISION", idParamIndex = 0)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supervisionService.delete(id);
        return ApiResponse.ok();
    }

    /** 导出听评课 CSV */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('" + PermissionCodes.SUPERVISION_READ + "')")
    @AuditLog(action = "导出", targetType = "SUPERVISION")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long offeringId,
                       @RequestParam(required = false) Long supervisorId,
                       @RequestParam(required = false) String semester,
                       @RequestParam(required = false) String status,
                       HttpServletResponse response) throws IOException {
        List<SupervisionView> list = supervisionService.listForExport(keyword, offeringId, supervisorId,
                semester, status, CurrentUserHolder.require());
        List<List<String>> rows = new ArrayList<>();
        for (SupervisionView s : list) {
            List<String> row = new ArrayList<>();
            row.add(s.getCourseCode());
            row.add(s.getCourseName());
            row.add(s.getClassName());
            row.add(s.getSemester());
            row.add(s.getTeacherName());
            row.add(s.getSupervisorName());
            row.add(s.getPlanDate());
            row.add(s.getFinishDate());
            row.add(s.getStatus());
            row.add(s.getScore() == null ? "" : s.getScore().toPlainString());
            row.add(s.getComment());
            rows.add(row);
        }
        CsvUtils.write(response, "听评课记录导出",
                List.of("课程编号", "课程名称", "教学班名称", "学期", "授课教师", "督导教师",
                        "计划听课日期", "完成日期", "状态", "评分", "评语"), rows);
    }

    /** 听评课状态候选值 */
    @GetMapping("/statuses")
    @PreAuthorize("hasAuthority('" + PermissionCodes.SUPERVISION_READ + "')")
    public ApiResponse<List<String>> statuses() {
        return ApiResponse.ok(supervisionService.statuses());
    }
}
