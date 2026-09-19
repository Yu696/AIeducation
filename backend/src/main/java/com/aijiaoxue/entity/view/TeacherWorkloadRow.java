package com.aijiaoxue.entity.view;

import lombok.Data;

/**
 * 教师授课分布统计行（主任工作台图表数据）。
 */
@Data
public class TeacherWorkloadRow {

    private Long teacherId;
    private String teacherName;
    private String departmentName;
    private String title;
    /** 主讲教学班数 */
    private Integer offeringCount;
    /** 覆盖学生人次 */
    private Integer studentTotal;
}
