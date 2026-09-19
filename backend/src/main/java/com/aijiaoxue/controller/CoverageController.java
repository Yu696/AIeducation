package com.aijiaoxue.controller;

import com.aijiaoxue.audit.AuditLog;
import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.entity.view.CoverageRow;
import com.aijiaoxue.entity.view.OfferingView;
import com.aijiaoxue.security.CurrentUserHolder;
import com.aijiaoxue.security.PermissionCodes;
import com.aijiaoxue.service.OfferingService;
import com.aijiaoxue.service.SupervisionService;
import com.aijiaoxue.util.CsvUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 督导覆盖率接口（US-04）。
 *
 * <p>契约见 docs/API契约.md 第 7 节。返回数据中同时带出计算口径文案，
 * 前端页面直接展示，避免覆盖率数字被误读（US-04 验收标准③）。
 */
@RestController
@RequestMapping("/api/supervision")
public class CoverageController {

    private final OfferingService offeringService;
    private final SupervisionService supervisionService;

    public CoverageController(OfferingService offeringService, SupervisionService supervisionService) {
        this.offeringService = offeringService;
        this.supervisionService = supervisionService;
    }

    /**
     * 督导覆盖率统计。
     *
     * @param semester 学期过滤，空表示全部学期
     * @param groupBy  department（按院系，默认）或 semester（按学期）
     */
    @GetMapping("/coverage")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COVERAGE_READ + "')")
    public ApiResponse<Map<String, Object>> coverage(@RequestParam(required = false) String semester,
                                                     @RequestParam(required = false) String departmentCode,
                                                     @RequestParam(defaultValue = "department") String groupBy) {
        return ApiResponse.ok(offeringService.coverage(semester, departmentCode, groupBy,
                CurrentUserHolder.require()));
    }

    /** 待安排听评课的教学班清单 */
    @GetMapping("/coverage/pending")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COVERAGE_READ + "')")
    public ApiResponse<List<OfferingView>> pending(@RequestParam(required = false) String semester,
                                                   @RequestParam(required = false) String departmentCode) {
        return ApiResponse.ok(supervisionService.pendingOfferings(semester, departmentCode,
                CurrentUserHolder.require()));
    }

    /** 导出督导覆盖率达 CSV（按院系与按学期两张表合并输出） */
    @GetMapping("/coverage/export")
    @PreAuthorize("hasAuthority('" + PermissionCodes.COVERAGE_READ + "')")
    @AuditLog(action = "导出", targetType = "COVERAGE")
    public void exportCoverage(@RequestParam(required = false) String semester,
                               @RequestParam(required = false) String departmentCode,
                               HttpServletResponse response) throws IOException {
        Map<String, List<CoverageRow>> data = offeringService.coverageForExport(semester, departmentCode,
                CurrentUserHolder.require());
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("分组维度", "分组", "正常开课教学班数", "已督导教学班数", "未督导教学班数", "覆盖率(%)"));
        for (CoverageRow r : data.get("byDepartment")) {
            rows.add(row("按院系", r));
        }
        for (CoverageRow r : data.get("bySemester")) {
            rows.add(row("按学期", r));
        }
        CsvUtils.write(response, "督导覆盖率导出",
                List.of("分组维度", "分组", "正常开课教学班数", "已督导教学班数", "未督导教学班数", "覆盖率(%)"), rows);
    }

    /** 覆盖率行转 CSV 行 */
    private List<String> row(String dimension, CoverageRow r) {
        int total = r.getTotalOfferings() == null ? 0 : r.getTotalOfferings();
        int supervised = r.getSupervisedOfferings() == null ? 0 : r.getSupervisedOfferings();
        List<String> row = new ArrayList<>();
        row.add(dimension);
        row.add(r.getGroupName() == null ? r.getGroupKey() : r.getGroupName());
        row.add(String.valueOf(total));
        row.add(String.valueOf(supervised));
        row.add(String.valueOf(Math.max(0, total - supervised)));
        row.add(r.getCoverageRate() == null ? "0" : r.getCoverageRate().toPlainString());
        return row;
    }
}
