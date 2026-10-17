package com.aijiaoxue.controller;

import com.aijiaoxue.audit.AuditLog;
import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.view.EvaluationSummaryRow;
import com.aijiaoxue.entity.view.EvaluationTrendRow;
import com.aijiaoxue.entity.view.EvaluationView;
import com.aijiaoxue.entity.view.TeacherSummaryRow;
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
import java.math.RoundingMode;
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
            @RequestParam(required = false) BigDecimal scoreMax,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long teacherId) {
        return ApiResponse.ok(evaluationService.page(page, size, keyword, offeringId, evaluationType,
                semester, scoreMin, scoreMax, courseId, teacherId, CurrentUserHolder.require()));
    }

    /**
     * 评教分析：课程汇总 + 学期趋势 + 教师汇总 + 分数分布。
     *
     * <p>不传 evaluationType 时默认学生评教，保持 Sprint 1 口径；
     * evaluationType=全部 表示全部评价类型。
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('" + PermissionCodes.EVALUATION_READ + "')")
    public ApiResponse<Map<String, Object>> summary(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String evaluationType) {
        return ApiResponse.ok(evaluationService.summary(semester, courseId, teacherId,
                evaluationType, CurrentUserHolder.require()));
    }

    /**
     * 评教分析导出：按课程 / 学期 / 教师三个维度输出，筛选口径与 summary 接口一致。
     */
    @GetMapping("/summary/export")
    @PreAuthorize("hasAuthority('" + PermissionCodes.EVALUATION_READ + "')")
    @AuditLog(action = "导出", targetType = "EVALUATION")
    public void exportSummary(@RequestParam(required = false) String semester,
                              @RequestParam(required = false) Long courseId,
                              @RequestParam(required = false) Long teacherId,
                              @RequestParam(required = false) String evaluationType,
                              HttpServletResponse response) throws IOException {
        Map<String, Object> data = evaluationService.summary(semester, courseId, teacherId,
                evaluationType, CurrentUserHolder.require());
        @SuppressWarnings("unchecked")
        List<EvaluationSummaryRow> summary = (List<EvaluationSummaryRow>) data.get("summary");
        @SuppressWarnings("unchecked")
        List<EvaluationTrendRow> trend = (List<EvaluationTrendRow>) data.get("trend");
        @SuppressWarnings("unchecked")
        List<TeacherSummaryRow> teacherSummary = (List<TeacherSummaryRow>) data.get("teacherSummary");

        List<List<String>> rows = new ArrayList<>();
        for (EvaluationSummaryRow r : summary) {
            rows.add(List.of("按课程", str(r.getSemester()), str(r.getCourseCode()),
                    str(r.getCourseName()), str(r.getTeacherName()),
                    score(r.getAvgScore()), count(r.getEvaluationCount()),
                    score(r.getMaxScore()), score(r.getMinScore()), ""));
        }
        for (EvaluationTrendRow r : trend) {
            rows.add(List.of("按学期", str(r.getSemester()), "", "", "",
                    score(r.getAvgScore()), count(r.getEvaluationCount()), "", "", ""));
        }
        for (TeacherSummaryRow r : teacherSummary) {
            rows.add(List.of("按教师", "", "", "", str(r.getTeacherName()),
                    score(r.getAvgScore()), count(r.getEvaluationCount()), "", "",
                    count(r.getCourseCount())));
        }
        CsvUtils.write(response, "评教分析导出",
                List.of("分析维度", "学期", "课程编号", "课程名称", "授课教师",
                        "平均分", "参评人次", "最高分", "最低分", "课程数"), rows);
    }

    /** CSV 单元格：空值安全 */
    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** CSV 单元格：分数保留 1 位小数 */
    private String score(Object value) {
        if (value == null) {
            return "";
        }
        return ((BigDecimal) value).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /** CSV 单元格：计数 */
    private String count(Object value) {
        return value == null ? "" : String.valueOf(((Number) value).longValue());
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
                       @RequestParam(required = false) Long courseId,
                       @RequestParam(required = false) Long teacherId,
                       HttpServletResponse response) throws IOException {
        List<EvaluationView> list = evaluationService.listForExport(keyword, offeringId, evaluationType,
                semester, scoreMin, scoreMax, courseId, teacherId, CurrentUserHolder.require());
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
