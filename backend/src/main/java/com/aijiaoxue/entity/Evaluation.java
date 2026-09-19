package com.aijiaoxue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教学评价记录。史诗三「教学评价管理」。
 *
 * <p>实验一范围锁定：本轮只做评价记录查看与学生评教汇总，不做个性化反馈报告。
 */
@Data
@TableName("evaluation")
public class Evaluation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    /** 评价类型：学生评教/督导评教/同行评议 */
    private String evaluationType;

    private String semester;

    /** 评分，0—100 */
    private BigDecimal score;

    /** 评价人（学生评教统一脱敏为「学生(匿名)」） */
    private String evaluator;

    /** 评价内容 */
    private String content;

    /** 评价时间 */
    private LocalDateTime evaluatedAt;

    private LocalDateTime createdAt;
}
