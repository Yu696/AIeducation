package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师档案。
 *
 * <p>US-02：教研室主任需要「查看5位教师的课程简介与开课情况，以便统筹排课」，
 * 因此教师详情必须能关联出课程简介与开课清单。
 */
@Data
@TableName("teacher")
public class Teacher {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工号，唯一 */
    private String teacherNo;

    /** 姓名 */
    private String name;

    /** 性别 */
    private String gender;

    /** 职称：教授/副教授/讲师/助教 */
    private String title;

    /** 教研室/院系代码 */
    private String departmentCode;

    /** 教研室/院系名称 */
    private String departmentName;

    private String email;

    private String phone;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
