package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程简介详情。故事原文要求「查看课程简介」，此表承载简介全文、
 * 教学目标、考核方式与教材等长文本字段。
 */
@Data
@TableName("course_intro")
public class CourseIntro {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程 ID，一课一条 */
    private Long courseId;

    /** 简介全文 */
    private String content;

    /** 教学目标 */
    private String objective;

    /** 考核方式 */
    private String assessmentMethod;

    /** 教材 */
    private String textbook;

    private LocalDateTime updatedAt;
}
