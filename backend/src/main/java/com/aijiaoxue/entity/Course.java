package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程（课程库主数据）。史诗一「课程信息管理」的核心实体。
 */
@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程编号，唯一，重复导入需返回冲突 */
    private String courseCode;

    /** 课程名称 */
    private String courseName;

    /** 课程类型：必修/选修/通识 */
    private String courseType;

    /** 学分 */
    private BigDecimal credit;

    /** 总学时 */
    private Integer totalHours;

    /** 开课教研室/院系代码 */
    private String departmentCode;

    /** 开课教研室/院系名称 */
    private String departmentName;

    /** 课程负责人（教师 ID） */
    private Long ownerTeacherId;

    /** 课程简介（短） */
    private String intro;

    /** 状态：草稿/已发布/已归档 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
