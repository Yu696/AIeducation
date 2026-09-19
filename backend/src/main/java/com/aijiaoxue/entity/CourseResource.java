package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课程—资源挂接关系。associated=0 表示已解除挂接（保留历史，不物理删除）。
 */
@Data
@TableName("course_resource")
public class CourseResource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long resourceId;

    /** 是否已挂接：1 已关联，0 已解除 */
    private Integer associated;
}
