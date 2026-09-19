package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教学资源。史诗二「教学资源管理」。
 *
 * <p>US-03 教师「查看学生人次与课程资源，以便备课」依赖本表；
 * 资源与课程的挂接关系见 course_resource。
 */
@Data
@TableName("teaching_resource")
public class TeachingResource {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资源名称 */
    private String resourceName;

    /** 资源类型：课件/教案/视频/习题/参考文献 */
    private String resourceType;

    /** 资源访问地址 */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 关联教学班，可为空表示课程级资源 */
    private Long offeringId;

    /** 上传/归属教师 */
    private Long teacherId;

    /** 资源说明 */
    private String description;

    /** 状态：有效/失效/待审核 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
