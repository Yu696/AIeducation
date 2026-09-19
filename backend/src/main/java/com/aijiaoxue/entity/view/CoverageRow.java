package com.aijiaoxue.entity.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 督导覆盖率统计行。按院系或按学期分组。
 *
 * <p>口径（docs/API契约.md 第 7 节）：
 * 覆盖率 = 存在已完成听课记录的开课记录数 ÷ 正常开课的开课记录数 × 100%，
 * 按开课记录去重，同一教学班多次听课只计 1。
 */
@Data
public class CoverageRow {

    /** 分组键：院系代码或学期 */
    private String groupKey;

    /** 分组名称：院系名称或学期 */
    private String groupName;

    /** 正常开课的教学班总数 */
    private Integer totalOfferings;

    /** 其中已有已完成听课记录的教学班数 */
    private Integer supervisedOfferings;

    /** 覆盖率百分比，保留一位小数 */
    private BigDecimal coverageRate;
}
