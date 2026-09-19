package com.aijiaoxue.entity.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 听评课视图对象：听评课记录 + 教学班 + 督导教师姓名。
 */
@Data
public class SupervisionView {

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
    private Long supervisorId;
    private String supervisorName;
    private String planDate;
    private String finishDate;
    private String status;
    private BigDecimal score;
    private String comment;
}
