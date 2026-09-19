package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.StudentCount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 学生人次 Mapper。
 */
@Mapper
public interface StudentCountMapper extends BaseMapper<StudentCount> {

    /**
     * 某教学班的学生人次合计（按班级明细求和口径）。
     * 口径见 database/数据字典.md《关键统计口径》。
     */
    @Select("SELECT COALESCE(SUM(student_num), 0) FROM student_count WHERE offering_id = #{offeringId}")
    long sumByOffering(@Param("offeringId") Long offeringId);
}
