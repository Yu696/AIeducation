package com.aijiaoxue.entity.view;

import lombok.Data;

/**
 * 教学评价视图对象：评价 + 所属课程与教学班 + 授课教师。
 */
@Data
public class EvaluationView {

    private Long id;
    private Long offeringId;
    private String offeringCode;
    private String courseId;
    private String courseCode;
    private String courseName;
    private String className;
    private String semester;
    private Long teacherId;
    private String teacherName;
    private String evaluationType;
    private java.math.BigDecimal score;
    private String evaluator;
    private String content;
    private String evaluatedAt;
}
