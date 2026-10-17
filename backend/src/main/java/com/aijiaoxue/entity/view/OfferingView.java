package com.aijiaoxue.entity.view;

import lombok.Data;

/**
 * 开课列表视图对象：开课记录 + 所属课程 + 主讲教师 + 督导状态 + 学生人次。
 *
 * <p>用于 US-02（主任查看开课情况）、US-03（教师看本人教学班）、US-04（督导看开课清单）。
 */
@Data
public class OfferingView {

    private Long id;
    private String offeringCode;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String courseType;
    private String semester;
    private String className;
    private Long teacherId;
    private String teacherName;
    private String departmentCode;
    private String departmentName;
    private Integer capacity;
    private Integer studentCount;
    private String offeringStatus;
    private String scheduleInfo;
    /** 是否存在已完成的听课记录：1 有，0 无 */
    private Integer supervised;
    /** 该教学班已完成听课次数 */
    private Integer supervisionTimes;
    /** 最近一条听评课记录的状态：待安排/已安排/已完成；无记录时为待安排 */
    private String supervisionStatus;
}
