package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.Course;
import com.aijiaoxue.entity.view.CourseDetailView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 课程库 Mapper。
 *
 * <p>US-02：主任需要「查看5位教师的课程简介与开课情况」，因此除基础 CRUD 外，
 * 还提供带简介、负责人、教学班数、资源数的详情查询与按教师（SELF 数据范围）过滤的列表查询。
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /**
     * 课程列表分页查询（含负责人姓名、教学班数、资源数）。
     *
     * @param page     分页参数
     * @param keyword  课程名称/编号模糊匹配
     * @param deptCode 院系代码过滤，null 表示不过滤
     * @param courseType 课程类型过滤
     * @param status   状态过滤
     * @param ownerTeacherId 按课程负责人过滤（教师 SELF 数据范围使用）
     */
    @Select("""
            <script>
            SELECT c.id, c.course_code, c.course_name, c.course_type, c.credit, c.total_hours,
                   c.department_code, c.department_name, c.owner_teacher_id,
                   t.name AS owner_teacher_name, c.intro, c.status,
                   (SELECT COUNT(1) FROM offering o WHERE o.course_id = c.id) AS offering_count,
                   (SELECT COUNT(1) FROM course_resource cr WHERE cr.course_id = c.id AND cr.associated = 1) AS resource_count
            FROM course c
            LEFT JOIN teacher t ON t.id = c.owner_teacher_id
            <where>
              <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%') OR c.course_code LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="deptCode != null and deptCode != ''"> AND c.department_code = #{deptCode} </if>
              <if test="courseType != null and courseType != ''"> AND c.course_type = #{courseType} </if>
              <if test="status != null and status != ''"> AND c.status = #{status} </if>
              <if test="ownerTeacherId != null">
                AND (c.owner_teacher_id = #{ownerTeacherId}
                     OR EXISTS (SELECT 1 FROM offering o2 WHERE o2.course_id = c.id AND o2.teacher_id = #{ownerTeacherId}))
              </if>
            </where>
            ORDER BY c.course_code ASC
            </script>
            """)
    IPage<CourseDetailView> selectCoursePage(Page<CourseDetailView> page,
                                              @Param("keyword") String keyword,
                                              @Param("deptCode") String deptCode,
                                              @Param("courseType") String courseType,
                                              @Param("status") String status,
                                              @Param("ownerTeacherId") Long ownerTeacherId);

    /** 课程详情（含课程简介详情表内容、负责人、教学班数、资源数） */
    @Select("""
            SELECT c.id, c.course_code, c.course_name, c.course_type, c.credit, c.total_hours,
                   c.department_code, c.department_name, c.owner_teacher_id,
                   t.name AS owner_teacher_name, c.intro, c.status,
                   ci.content AS intro_content, ci.objective, ci.assessment_method, ci.textbook,
                   (SELECT COUNT(1) FROM offering o WHERE o.course_id = c.id) AS offering_count,
                   (SELECT COUNT(1) FROM course_resource cr WHERE cr.course_id = c.id AND cr.associated = 1) AS resource_count
            FROM course c
            LEFT JOIN teacher t ON t.id = c.owner_teacher_id
            LEFT JOIN course_intro ci ON ci.course_id = c.id
            WHERE c.id = #{id}
            """)
    CourseDetailView selectCourseDetail(@Param("id") Long id);

    /** 导出用：不分页的全量课程列表 */
    @Select("""
            <script>
            SELECT c.id, c.course_code, c.course_name, c.course_type, c.credit, c.total_hours,
                   c.department_code, c.department_name, c.owner_teacher_id,
                   t.name AS owner_teacher_name, c.intro, c.status,
                   0 AS offering_count, 0 AS resource_count
            FROM course c
            LEFT JOIN teacher t ON t.id = c.owner_teacher_id
            <where>
              <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%') OR c.course_code LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="deptCode != null and deptCode != ''"> AND c.department_code = #{deptCode} </if>
              <if test="courseType != null and courseType != ''"> AND c.course_type = #{courseType} </if>
              <if test="status != null and status != ''"> AND c.status = #{status} </if>
              <if test="ownerTeacherId != null">
                AND (c.owner_teacher_id = #{ownerTeacherId}
                     OR EXISTS (SELECT 1 FROM offering o2 WHERE o2.course_id = c.id AND o2.teacher_id = #{ownerTeacherId}))
              </if>
            </where>
            ORDER BY c.course_code ASC
            </script>
            """)
    List<CourseDetailView> selectCourseList(@Param("keyword") String keyword,
                                            @Param("deptCode") String deptCode,
                                            @Param("courseType") String courseType,
                                            @Param("status") String status,
                                            @Param("ownerTeacherId") Long ownerTeacherId);

    /** 教师详情用：某教师负责或讲授的全部课程（含教学班数） */
    @Select("""
            SELECT c.id, c.course_code, c.course_name, c.course_type, c.credit, c.total_hours,
                   c.department_code, c.department_name, c.owner_teacher_id,
                   c.intro, c.status, ci.content AS intro_content,
                   (SELECT COUNT(1) FROM offering o WHERE o.course_id = c.id AND o.teacher_id = #{teacherId}) AS offering_count,
                   (SELECT COUNT(1) FROM course_resource cr WHERE cr.course_id = c.id AND cr.associated = 1) AS resource_count
            FROM course c
            LEFT JOIN course_intro ci ON ci.course_id = c.id
            WHERE c.owner_teacher_id = #{teacherId}
               OR EXISTS (SELECT 1 FROM offering o2 WHERE o2.course_id = c.id AND o2.teacher_id = #{teacherId})
            ORDER BY c.course_code ASC
            """)
    List<CourseDetailView> selectCoursesOfTeacher(@Param("teacherId") Long teacherId);
}
