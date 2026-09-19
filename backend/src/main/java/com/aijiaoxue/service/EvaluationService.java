package com.aijiaoxue.service;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.view.EvaluationSummaryRow;
import com.aijiaoxue.entity.view.EvaluationView;
import com.aijiaoxue.mapper.EvaluationMapper;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.DataScopeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 学生评教汇总 + 分数分布。
     *
     * <p>口径：仅统计 evaluation_type='学生评教' 的记录；平均分保留 1 位小数（四舍五入）。
     *
     * @return summary 列表、distribution 分档、总体平均分
     */
    public Map<String, Object> summary(String semester, CurrentUser user) {
        Long effTeacher = dataScopeService.teacherScope(user);
        String effDept = dataScopeService.effectiveDept(null, user);

        List<EvaluationSummaryRow> summary = evaluationMapper.selectEvaluationSummary(semester, effTeacher, effDept);
        List<Map<String, Object>> distribution = evaluationMapper.selectScoreDistribution(semester, effTeacher, effDept);

        // 总体平均分按参评人次加权，避免小样本课程拉偏整体判断
        long totalCount = 0L;
        BigDecimal weighted = BigDecimal.ZERO;
        for (EvaluationSummaryRow row : summary) {
            if (row.getEvaluationCount() == null || row.getAvgScore() == null) {
                continue;
            }
            totalCount += row.getEvaluationCount();
            weighted = weighted.add(row.getAvgScore().multiply(BigDecimal.valueOf(row.getEvaluationCount())));
        }
        BigDecimal overallAvg = totalCount == 0 ? BigDecimal.ZERO
                : weighted.divide(BigDecimal.valueOf(totalCount), 1, java.math.RoundingMode.HALF_UP);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("totalEvaluations", totalCount);
        data.put("overallAvgScore", overallAvg);
        data.put("distribution", normalizeDistribution(distribution));
        data.put("caliber", "学生评教汇总口径：仅统计 evaluation_type='学生评教' 的记录；"
                + "课程平均分 = 该课程全部学生评教分数平均值（保留 1 位小数）；"
                + "总体平均分按参评人次加权计算。");
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
