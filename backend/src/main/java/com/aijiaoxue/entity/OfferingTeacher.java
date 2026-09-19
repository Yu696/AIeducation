package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 教学班—教师关联。支持一个教学班由多位教师共同承担（主讲/辅导/合上）。
 */
@Data
@TableName("offering_teacher")
public class OfferingTeacher {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private Long teacherId;

    /** 教师角色：主讲/辅导/合上 */
    private String roleType;
}
