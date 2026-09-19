package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 学生人次明细。US-03「教师查看学生人次」的数据来源。
 *
 * <p>统计口径见 database/数据字典.md：教师工作台展示的学生人次按教学班汇总
 * {@code student_count.student_num}，其中口径取「按班级明细求和」。
 */
@Data
@TableName("student_count")
public class StudentCount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    /** 统计日期 */
    private LocalDate statDate;

    /** 班级名称 */
    private String className;

    /** 专业 */
    private String major;

    /** 人次 */
    private Integer studentNum;

    private String remark;
}
