package com.aijiaoxue.entity.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程详情视图对象：课程主数据 + 课程简介详情 + 负责人 + 教学班数 + 资源数。
 *
 * <p>满足 US-02 故事原文「查看课程简介与开课情况」。
 */
@Data
public class CourseDetailView {

    private Long id;
    private String courseCode;
    private String courseName;
    private String courseType;
    private BigDecimal credit;
    private Integer totalHours;
    private String departmentCode;
    private String departmentName;
    private Long ownerTeacherId;
    private String ownerTeacherName;
    private String intro;
    private String status;
    /** 课程简介详情 */
    private String introContent;
    private String objective;
    private String assessmentMethod;
    private String textbook;
    /** 教学班数量 */
    private Integer offeringCount;
    /** 已挂接资源数 */
    private Integer resourceCount;
}
