package com.aijiaoxue.entity.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 评教按教师汇总行（US-09）。
 */
@Data
public class TeacherSummaryRow {

    private Long teacherId;
    private String teacherName;
    private Long courseCount;
    private Long evaluationCount;
    private BigDecimal avgScore;
}
