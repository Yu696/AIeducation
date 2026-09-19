package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开课记录（教学班）。一门课程在一个学期可以开多个教学班。
 *
 * <p>US-02「开课情况」、US-04「课程开设与督导覆盖率」都以本表为统计基础。
 */
@Data
@TableName("offering")
public class Offering {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 教学班编号，唯一 */
    private String offeringCode;

    private Long courseId;

    /** 学期，如 2025-2026-1 */
    private String semester;

    /** 教学班名称，如 软件2201班 */
    private String className;

    /** 主讲教师 ID */
    private Long teacherId;

    /** 开课教研室/院系代码 */
    private String departmentCode;

    /** 容量 */
    private Integer capacity;

    /** 学生人数（汇总值，明细见 student_count） */
    private Integer studentCount;

    /** 开课状态：正常开课/停开/已结课 */
    private String offeringStatus;

    /** 上课时间地点 */
    private String scheduleInfo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
