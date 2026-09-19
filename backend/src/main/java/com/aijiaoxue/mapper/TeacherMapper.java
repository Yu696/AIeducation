package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.Teacher;
import com.aijiaoxue.entity.view.TeacherWorkloadRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 教师档案 Mapper。
 */
@Mapper
public interface TeacherMapper extends BaseMapper<Teacher> {

    /** 教师列表分页（关键词/院系/职称筛选） */
    @Select("""
            <script>
            SELECT * FROM teacher
            <where>
              <if test="keyword != null and keyword != ''">
                AND (name LIKE CONCAT('%', #{keyword}, '%')
                     OR teacher_no LIKE CONCAT('%', #{keyword}, '%')
                     OR title LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="deptCode != null and deptCode != ''"> AND department_code = #{deptCode} </if>
              <if test="title != null and title != ''"> AND title = #{title} </if>
            </where>
            ORDER BY department_code ASC, teacher_no ASC
            </script>
            """)
    IPage<Teacher> selectTeacherPage(Page<Teacher> page,
                                     @Param("keyword") String keyword,
                                     @Param("deptCode") String deptCode,
                                     @Param("title") String title);

    /** 导出用全量教师列表 */
    @Select("""
            <script>
            SELECT * FROM teacher
            <where>
              <if test="keyword != null and keyword != ''">
                AND (name LIKE CONCAT('%', #{keyword}, '%') OR teacher_no LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="deptCode != null and deptCode != ''"> AND department_code = #{deptCode} </if>
              <if test="title != null and title != ''"> AND title = #{title} </if>
            </where>
            ORDER BY department_code ASC, teacher_no ASC
            </script>
            """)
    List<Teacher> selectTeacherList(@Param("keyword") String keyword,
                                    @Param("deptCode") String deptCode,
                                    @Param("title") String title);

    /** 全部院系（供筛选下拉框，取课程与教师的并集） */
    @Select("SELECT DISTINCT department_code FROM teacher WHERE department_code IS NOT NULL ORDER BY department_code")
    List<String> selectDepartments();

    /**
     * 教师授课分布统计（主任工作台柱状图）。
     *
     * <p>统计口径包含合上/辅导身份：主讲教师本人或 offering_teacher 中登记的教师均计入授课量，
     * 否则合上教师的授课量会被漏算。
     *
     * @param deptCode  院系过滤
     * @param semester  学期过滤
     * @param teacherId 仅统计某位教师（教师工作台用），null 表示全部
     */
    @Select("""
            <script>
            SELECT t.id AS teacher_id, t.name AS teacher_name, t.department_name, t.title,
                   COUNT(DISTINCT o.id) AS offering_count,
                   COALESCE(SUM(o.student_count), 0) AS student_total
            FROM teacher t
            LEFT JOIN offering o ON (o.teacher_id = t.id
                 OR EXISTS (SELECT 1 FROM offering_teacher ot
                            WHERE ot.offering_id = o.id AND ot.teacher_id = t.id))
              <if test="semester != null and semester != ''"> AND o.semester = #{semester} </if>
              <if test="onlyOffered != null and onlyOffered == 1"> AND o.offering_status = '正常开课' </if>
            <where>
              <if test="deptCode != null and deptCode != ''"> AND t.department_code = #{deptCode} </if>
              <if test="teacherId != null"> AND t.id = #{teacherId} </if>
            </where>
            GROUP BY t.id, t.name, t.department_name, t.title
            ORDER BY offering_count DESC, t.name ASC
            </script>
            """)
    List<TeacherWorkloadRow> selectTeacherWorkload(@Param("deptCode") String deptCode,
                                                   @Param("semester") String semester,
                                                   @Param("teacherId") Long teacherId,
                                                   @Param("onlyOffered") Integer onlyOffered);
}
