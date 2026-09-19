package com.aijiaoxue.dto;

import lombok.Data;

/**
 * 课程简介详情请求体。与 course_intro 表字段一一对应。
 */
@Data
public class CourseIntroRequest {

    /** 简介全文 */
    private String content;

    /** 教学目标 */
    private String objective;

    /** 考核方式 */
    private String assessmentMethod;

    /** 教材 */
    private String textbook;
}
