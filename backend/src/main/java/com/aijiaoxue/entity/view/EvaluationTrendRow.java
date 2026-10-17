package com.aijiaoxue.entity.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 评教按学期趋势行（US-09）。
 *
 * <p>平均分直接基于原始评价记录聚合，避免“先按课程四舍五入再加权”的误差。
 */
@Data
public class EvaluationTrendRow {

    private String semester;
    private BigDecimal avgScore;
    private Long evaluationCount;
}
