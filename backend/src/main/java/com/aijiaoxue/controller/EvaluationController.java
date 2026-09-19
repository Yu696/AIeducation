package com.aijiaoxue.controller;

import com.aijiaoxue.audit.AuditLog;
import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.view.EvaluationView;
import com.aijiaoxue.security.CurrentUserHolder;
import com.aijiaoxue.security.PermissionCodes;
import com.aijiaoxue.service.EvaluationService;
import com.aijiaoxue.util.CsvUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 教学评价接口（史诗三）。
 *
 * <p>契约见 docs/API契约.md 第 6 节。Sprint 1 只做记录查看与汇总展示（实验一 Won't 清单已锁定）。
 */
@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 评价列表分页 */
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.EVALUATION_READ + "')")
    public ApiResponse<PageResult<EvaluationView>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long offeringId,
            @RequestParam(required = false) String evaluationType,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) BigDecimal scoreMin,
            @RequestParam(required = false) BigDecimal scoreMax) {
        return ApiResponse.ok(evaluationService.page(page, size, keyword, offeringId, evaluationType,
                semester, scoreMin, scoreMax, CurrentUserHolder.require()));
    }

    /** 学生评教汇总 + 分数分布（含计算口径说明） */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('" + PermissionCodes.EVALUATION_READ + "')")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(required = false) String semester) {
        return ApiResponse.ok(evaluationService.summary(semester, CurrentUserHolder.require()));
    }

    /** 评价详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.EVALUATION_READ + "')")
    public ApiResponse<EvaluationView> detail(@PathVariable Long id) {
        return ApiResponse.ok(evaluationService.detail(id));
    }

    /** 导出评价 CSV */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('" + PermissionCodes.EVALUATION_READ + "')")
    @AuditLog(action = "导出", targetType = "EVALUATION")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long offeringId,
                       @RequestParam(required = false) String evaluationType,
                       @RequestParam(required = false) String semester,
                       @RequestParam(required = false) BigDecimal scoreMin,
                       @RequestParam(required = false) BigDecimal scoreMax,
                       HttpServletResponse response) throws IOException {
        List<EvaluationView> list = evaluationService.listForExport(keyword, offeringId, evaluationType,
                semester, scoreMin, scoreMax, CurrentUserHolder.require());
        List<List<String>> rows = new ArrayList<>();
        for (EvaluationView e : list) {
            List<String> row = new ArrayList<>();
            row.add(e.getCourseCode());
            row.add(e.getCourseName());
            row.add(e.getClassName());
            row.add(e.getSemester());
            row.add(e.getTeacherName());
            row.add(e.getEvaluationType());
            row.add(e.getScore() == null ? "" : e.getScore().toPlainString());
            row.add(e.getEvaluator());
            row.add(e.getContent());
            row.add(e.getEvaluatedAt());
            rows.add(row);
        }
        CsvUtils.write(response, "教学评价导出",
                List.of("课程编号", "课程名称", "教学班名称", "学期", "授课教师", "评价类型",
                        "评分", "评价人", "评价内容", "评价时间"), rows);
    }

    /** 评价类型候选值 */
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('" + PermissionCodes.EVALUATION_READ + "')")
    public ApiResponse<List<String>> types() {
        return ApiResponse.ok(evaluationService.evaluationTypes());
    }
}
