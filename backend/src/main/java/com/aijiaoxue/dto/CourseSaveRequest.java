package com.aijiaoxue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程新建/修改请求 = 课程主数据 + 课程简介详情。
 *
 * <p>拆成两个嵌套结构的原因：课程简介是长文本（含教学目标、考核方式、教材），
 * 与课程主数据分表存储（course / course_intro），请求体保持同构便于前端一次提交。
 */
@Data
public class CourseSaveRequest {

    @NotBlank(message = "课程编号不能为空")
    private String courseCode;

    @NotBlank(message = "课程名称不能为空")
    private String courseName;

    /** 课程类型：必修/选修/通识 */
    private String courseType;

    private BigDecimal credit;

    private Integer totalHours;

    private String departmentCode;

    private String departmentName;

    /** 课程负责人（教师 ID） */
    private Long ownerTeacherId;

    /** 课程简介（短） */
    private String intro;

    /** 状态：草稿/已发布/已归档 */
    private String status;

    /** 课程简介详情（内容/目标/考核方式/教材） */
    private CourseIntroRequest courseIntro;
}

