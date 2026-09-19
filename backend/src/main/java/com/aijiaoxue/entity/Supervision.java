package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 听评课（督导）记录。US-04「查看课程开设与督导覆盖率，以便安排听评课」。
 *
 * <p>覆盖率口径：仅 status='已完成' 的记录计入「已督导」。
 */
@Data
@TableName("supervision")
public class Supervision {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    /** 督导教师 ID */
    private Long supervisorId;

    private String semester;

    /** 计划听课日期 */
    private LocalDate planDate;

    /** 实际完成日期 */
    private LocalDate finishDate;

    /** 状态：待安排/已安排/已完成 */
    private String status;

    /** 听课评分 */
    private BigDecimal score;

    /** 评语 */
    private String comment;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
