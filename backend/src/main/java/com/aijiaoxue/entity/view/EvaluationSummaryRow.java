package com.aijiaoxue.entity.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生评教汇总行（按课程聚合）。
 */
@Data
public class EvaluationSummaryRow {

    private Long courseId;
    private String courseCode;
    private String courseName;
    private Long teacherId;
    private String teacherName;
    private String semester;
    /** 参评人次 */
    private Integer evaluationCount;
    /** 平均分 */
    private BigDecimal avgScore;
    /** 最高分 */
    private BigDecimal maxScore;
    /** 最低分 */
    private BigDecimal minScore;
}
