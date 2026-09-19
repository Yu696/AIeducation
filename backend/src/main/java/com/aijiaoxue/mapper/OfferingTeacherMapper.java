package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.OfferingTeacher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 教学班—教师关联 Mapper。
 */
@Mapper
public interface OfferingTeacherMapper extends BaseMapper<OfferingTeacher> {

    /** 查询某教学班的任课教师姓名与角色 */
    @Select("""
            SELECT t.name AS teacher_name, ot.role_type
            FROM offering_teacher ot
            LEFT JOIN teacher t ON t.id = ot.teacher_id
            WHERE ot.offering_id = #{offeringId}
            ORDER BY ot.role_type ASC
            """)
    List<java.util.Map<String, Object>> selectTeachersOfOffering(@Param("offeringId") Long offeringId);
}
