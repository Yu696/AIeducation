package com.aijiaoxue.service;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.view.EvaluationSummaryRow;
import com.aijiaoxue.entity.view.EvaluationTrendRow;
import com.aijiaoxue.entity.view.EvaluationView;
import com.aijiaoxue.entity.view.TeacherSummaryRow;
import com.aijiaoxue.mapper.EvaluationMapper;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.DataScopeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教学评价服务（史诗三）。
 *
 * <p>Sprint 1 范围（实验一 Won't 清单已锁定）：评价记录查看 + 学生评教汇总 + 分数分布，
 * 不做个性化反馈报告、教师成长指数等进阶能力。
 */
@Service
public class EvaluationService {

    private final EvaluationMapper evaluationMapper;
    private final DataScopeService dataScopeService;

    public EvaluationService(EvaluationMapper evaluationMapper, DataScopeService dataScopeService) {
        this.evaluationMapper = evaluationMapper;
        this.dataScopeService = dataScopeService;
    }

    /** 评价列表分页（教师只看本人所授课程，主任只看本院系） */
    public PageResult<EvaluationView> page(long page, long size, String keyword, Long offeringId,
                                           String evaluationType, String semester, BigDecimal scoreMin,
                                           BigDecimal scoreMax, CurrentUser user) {
        Page<EvaluationView> p = new Page<>(page, size);
        Long effTeacher = dataScopeService.teacherScope(user);
        String effDept = dataScopeService.effectiveDept(null, user);
        IPage<EvaluationView> result = evaluationMapper.selectEvaluationPage(p, keyword, offeringId,
                evaluationType, semester, scoreMin, scoreMax, effTeacher, effDept);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 导出用评价列表 */
    public List<EvaluationView> listForExport(String keyword, Long offeringId, String evaluationType,
                                              String semester, BigDecimal scoreMin, BigDecimal scoreMax,
                                              CurrentUser user) {
        Long effTeacher = dataScopeService.teacherScope(user);
        String effDept = dataScopeService.effectiveDept(null, user);
        return evaluationMapper.selectEvaluationList(keyword, offeringId, evaluationType, semester,
                scoreMin, scoreMax, effTeacher, effDept);
    }

    /** 评价详情 */
    public EvaluationView detail(Long id) {
        EvaluationView view = evaluationMapper.selectEvaluationDetail(id);
        if (view == null) {
            throw BusinessException.notFound("评价记录不存在");
        }
        return view;
    }

    /** 兼容 Sprint 1 调用：不传筛选条件时等价于默认“学生评教”汇总 */
    public Map<String, Object> summary(String semester, CurrentUser user) {
        return summary(semester, null, null, null, user);
    }

    /**
     * 评教分析：按课程/学期/教师/评价类型筛选，返回课程汇总、学期趋势、教师汇总与分数分布。
     *
     * <p>兼容 Sprint 1 行为：不传 evaluationType 时默认只统计“学生评教”；
     * 传 evaluationType=全部（或 ALL）表示统计全部评价类型。
     * 教师角色由数据范围强制为本人，主任强制为本院系。
     */
    public Map<String, Object> summary(String semester, Long courseId, Long teacherId,
                                       String evaluationType, CurrentUser user) {
        Long effTeacher = dataScopeService.effectiveTeacher(teacherId, user);
        String effDept = dataScopeService.effectiveDept(null, user);

        String typeFilter;
        if (evaluationType == null || evaluationType.isBlank()) {
            typeFilter = "学生评教";
        } else if ("全部".equals(evaluationType.trim()) || "ALL".equalsIgnoreCase(evaluationType.trim())) {
            typeFilter = null;
        } else {
            typeFilter = evaluationType.trim();
        }

        List<EvaluationSummaryRow> summary = evaluationMapper.selectEvaluationSummary(
                semester, courseId, effTeacher, typeFilter, effDept);
        List<Map<String, Object>> distribution = evaluationMapper.selectScoreDistribution(
                semester, courseId, effTeacher, typeFilter, effDept);
        List<EvaluationTrendRow> trend = evaluationMapper.selectEvaluationTrend(
                semester, courseId, effTeacher, typeFilter, effDept);
        List<TeacherSummaryRow> teacherSummary = evaluationMapper.selectTeacherSummary(
                semester, courseId, effTeacher, typeFilter, effDept);

        // 总体平均分按参评人次加权，避免小样本课程拉偏整体判断
        long totalCount = 0L;
        BigDecimal weighted = BigDecimal.ZERO;
        Set<Long> courseIds = new HashSet<>();
        for (EvaluationSummaryRow row : summary) {
            if (row.getCourseId() != null) {
                courseIds.add(row.getCourseId());
            }
            if (row.getEvaluationCount() == null || row.getAvgScore() == null) {
                continue;
            }
            totalCount += row.getEvaluationCount();
            weighted = weighted.add(row.getAvgScore().multiply(BigDecimal.valueOf(row.getEvaluationCount())));
        }
        BigDecimal overallAvg = totalCount == 0 ? BigDecimal.ZERO
                : weighted.divide(BigDecimal.valueOf(totalCount), 1, RoundingMode.HALF_UP);

        String typeLabel = typeFilter == null ? "全部评价类型" : typeFilter;
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("semester", semester);
        filters.put("courseId", courseId);
        filters.put("teacherId", effTeacher);
        filters.put("evaluationType", typeFilter == null ? "全部" : typeFilter);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("totalEvaluations", totalCount);
        data.put("overallAvgScore", overallAvg);
        data.put("courseCount", courseIds.size());
        data.put("distribution", normalizeDistribution(distribution));
        data.put("trend", trend);
        data.put("teacherSummary", teacherSummary);
        data.put("evaluationType", typeFilter == null ? "全部" : typeFilter);
        data.put("filters", filters);
        data.put("caliber", "评教分析口径：统计类型=" + typeLabel + "；"
                + "课程平均分 = 该课程/学期/教师维度的评价平均分（保留 1 位小数）；"
                + "总体平均分按参评人次加权；学期趋势与教师汇总同样按参评人次加权。");
        return data;
    }

    /** 补齐缺失的分数分档，保证前端柱状图档位固定 */
    private List<Map<String, Object>> normalizeDistribution(List<Map<String, Object>> raw) {
        String[] buckets = {"90分以上", "80-89分", "70-79分", "60-69分", "60分以下"};
        Map<String, Object> counts = new LinkedHashMap<>();
        if (raw != null) {
            for (Map<String, Object> row : raw) {
                Object key = row.get("group_key");
                Object cnt = row.get("cnt");
                if (key != null) {
                    counts.put(String.valueOf(key), cnt);
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String bucket : buckets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bucket", bucket);
            Object cnt = counts.get(bucket);
            item.put("count", cnt == null ? 0 : ((Number) cnt).intValue());
            result.add(item);
        }
        return result;
    }

    /** 评价类型候选值 */
    public List<String> evaluationTypes() {
        return List.of("学生评教", "督导评教", "同行评议");
    }
}
