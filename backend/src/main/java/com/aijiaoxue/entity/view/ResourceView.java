package com.aijiaoxue.entity.view;

import lombok.Data;

/**
 * 教学资源视图对象：资源 + 归属教师 + 关联教学班 + 所属课程。
 *
 * <p>用于 US-03（教师查看课程资源、备课查看）。
 */
@Data
public class ResourceView {

    private Long id;
    private String resourceName;
    private String resourceType;
    private String fileUrl;
    private Long fileSize;
    private Long offeringId;
    private String offeringCode;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String semester;
    private Long teacherId;
    private String teacherName;
    private String description;
    private String status;
    private String createdAt;
}
