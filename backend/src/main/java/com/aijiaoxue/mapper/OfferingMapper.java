package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.Offering;
import com.aijiaoxue.entity.view.CoverageRow;
import com.aijiaoxue.entity.view.OfferingView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 开课（教学班）Mapper。
 *
 * <p>US-02 开课情况查询、US-04 开课清单与督导覆盖率统计均以本 Mapper 为基础。
 * 覆盖率口径见 {@link #selectCoverageByDepartment} 与 {@link #selectCoverageBySemester}。
 */
@Mapper
public interface OfferingMapper extends BaseMapper<Offering> {

    /** 开课记录公共字段片段（含课程、教师、督导状态） */
    String VIEW_COLUMNS = """
            SELECT o.id, o.offering_code, o.course_id, c.course_code, c.course_name, c.course_type,
                   o.semester, o.class_name, o.teacher_id, t.name AS teacher_name,
                   o.department_code, c.department_name, o.capacity, o.student_count,
                   o.offering_status, o.schedule_info,
                   CASE WHEN EXISTS (SELECT 1 FROM supervision s2
                                     WHERE s2.offering_id = o.id AND s2.status = '已完成')
                        THEN 1 ELSE 0 END AS supervised,
                   (SELECT COUNT(1) FROM supervision s3
                     WHERE s3.offering_id = o.id AND s3.status = '已完成') AS supervision_times,
                   COALESCE((SELECT s4.status FROM supervision s4
                             WHERE s4.offering_id = o.id
                               AND s4.id = (SELECT MAX(s5.id) FROM supervision s5
                                            WHERE s5.offering_id = o.id)), '待安排') AS supervision_status
            FROM offering o
            LEFT JOIN course c ON c.id = o.course_id
            LEFT JOIN teacher t ON t.id = o.teacher_id
            """;

    /**
     * 开课列表分页查询。
     *
     * <p>teacherId 过滤同时匹配「主讲教师」与「offering_teacher 中的合上/辅导教师」，
     * 保证合上教师也能在本人教学班列表中看到该教学班。
     *
     * @param supervised 是否已督导：null 全部，1 已督导，0 未督导
     * @param onlyOffered 仅统计「正常开课」的教学班（US-04 覆盖率口径要求）
     */
    @Select("""
            <script>
            """ + VIEW_COLUMNS + """
            <where>
              <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.course_code LIKE CONCAT('%', #{keyword}, '%')
                     OR o.offering_code LIKE CONCAT('%', #{keyword}, '%')
                     OR o.class_name LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="semester != null and semester != ''"> AND o.semester = #{semester} </if>
              <if test="teacherId != null">
                AND (o.teacher_id = #{teacherId}
                     OR EXISTS (SELECT 1 FROM offering_teacher ot
                                WHERE ot.offering_id = o.id AND ot.teacher_id = #{teacherId}))
              </if>
              <if test="deptCode != null and deptCode != ''"> AND c.department_code = #{deptCode} </if>
              <if test="offeringStatus != null and offeringStatus != ''"> AND o.offering_status = #{offeringStatus} </if>
              <if test="onlyOffered != null and onlyOffered == 1"> AND o.offering_status = '正常开课' </if>
              <if test="supervised != null and supervised == 1">
                AND EXISTS (SELECT 1 FROM supervision s WHERE s.offering_id = o.id AND s.status = '已完成')
              </if>
              <if test="supervised != null and supervised == 0">
                AND NOT EXISTS (SELECT 1 FROM supervision s WHERE s.offering_id = o.id AND s.status = '已完成')
              </if>
            </where>
            ORDER BY o.semester DESC, c.course_code ASC, o.class_name ASC
            </script>
            """)
    IPage<OfferingView> selectOfferingPage(Page<OfferingView> page,
                                           @Param("keyword") String keyword,
                                           @Param("semester") String semester,
                                           @Param("teacherId") Long teacherId,
                                           @Param("deptCode") String deptCode,
                                           @Param("offeringStatus") String offeringStatus,
                                           @Param("supervised") Integer supervised,
                                           @Param("onlyOffered") Integer onlyOffered);

    /** 导出用：不分页开课列表（teacherId 过滤规则同上） */
    @Select("""
            <script>
            """ + VIEW_COLUMNS + """
            <where>
              <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.course_code LIKE CONCAT('%', #{keyword}, '%')
                     OR o.offering_code LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="semester != null and semester != ''"> AND o.semester = #{semester} </if>
              <if test="teacherId != null">
                AND (o.teacher_id = #{teacherId}
                     OR EXISTS (SELECT 1 FROM offering_teacher ot
                                WHERE ot.offering_id = o.id AND ot.teacher_id = #{teacherId}))
              </if>
              <if test="deptCode != null and deptCode != ''"> AND c.department_code = #{deptCode} </if>
              <if test="offeringStatus != null and offeringStatus != ''"> AND o.offering_status = #{offeringStatus} </if>
              <if test="onlyOffered != null and onlyOffered == 1"> AND o.offering_status = '正常开课' </if>
              <if test="supervised != null and supervised == 1">
                AND EXISTS (SELECT 1 FROM supervision s WHERE s.offering_id = o.id AND s.status = '已完成')
              </if>
              <if test="supervised != null and supervised == 0">
                AND NOT EXISTS (SELECT 1 FROM supervision s WHERE s.offering_id = o.id AND s.status = '已完成')
              </if>
            </where>
            ORDER BY o.semester DESC, c.course_code ASC
            </script>
            """)
    List<OfferingView> selectOfferingList(@Param("keyword") String keyword,
                                          @Param("semester") String semester,
                                          @Param("teacherId") Long teacherId,
                                          @Param("deptCode") String deptCode,
                                          @Param("offeringStatus") String offeringStatus,
                                          @Param("supervised") Integer supervised,
                                          @Param("onlyOffered") Integer onlyOffered);

    /** 开课详情 */
    @Select(VIEW_COLUMNS + " WHERE o.id = #{id} ")
    OfferingView selectOfferingDetail(@Param("id") Long id);

    /** 某课程的全部开课记录 */
    @Select(VIEW_COLUMNS + " WHERE o.course_id = #{courseId} ORDER BY o.semester DESC ")
    List<OfferingView> selectOfferingsByCourse(@Param("courseId") Long courseId);

    /** 某教师的全部开课记录（含合上/辅导身份） */
    @Select(VIEW_COLUMNS + """
             WHERE o.teacher_id = #{teacherId}
                OR EXISTS (SELECT 1 FROM offering_teacher ot
                           WHERE ot.offering_id = o.id AND ot.teacher_id = #{teacherId})
             ORDER BY o.semester DESC, c.course_code ASC
            """)
    List<OfferingView> selectOfferingsByTeacher(@Param("teacherId") Long teacherId);

    /**
     * 按院系统计督导覆盖率。
     *
     * <p>口径：分子 = 该院系「正常开课」且存在 status='已完成' 听课记录的教学班数（按 offering 去重）；
     * 分母 = 该院系「正常开课」的教学班总数。
     *
     * <p>院系归属统一取<b>课程所属院系</b>（course.department_code），
     * 避免 offering.department_code 与课程院系不一致时总体数字与分组数字对不上。
     * 分组键同时用于筛选条件，保证「按学期过滤的总体」与「按院系分组」可交叉核对。
     *
     * @param semester 学期过滤，null 表示全部学期
     * @param deptCode 院系过滤，null 表示全部院系
     */
    @Select("""
            <script>
            SELECT c.department_code AS group_key,
                   MAX(c.department_name) AS group_name,
                   COUNT(1) AS total_offerings,
                   SUM(CASE WHEN EXISTS (SELECT 1 FROM supervision s
                                         WHERE s.offering_id = o.id AND s.status = '已完成')
                            THEN 1 ELSE 0 END) AS supervised_offerings,
                   ROUND(SUM(CASE WHEN EXISTS (SELECT 1 FROM supervision s
                                               WHERE s.offering_id = o.id AND s.status = '已完成')
                                  THEN 1 ELSE 0 END) * 100.0 / COUNT(1), 1) AS coverage_rate
            FROM offering o
            JOIN course c ON c.id = o.course_id
            WHERE o.offering_status = '正常开课'
            <if test="semester != null and semester != ''"> AND o.semester = #{semester} </if>
            <if test="deptCode != null and deptCode != ''"> AND c.department_code = #{deptCode} </if>
            GROUP BY c.department_code
            ORDER BY coverage_rate ASC, c.department_code ASC
            </script>
            """)
    List<CoverageRow> selectCoverageByDepartment(@Param("semester") String semester,
                                                 @Param("deptCode") String deptCode);

    /** 按学期统计督导覆盖率（口径同上，院系过滤按课程院系） */
    @Select("""
            <script>
            SELECT o.semester AS group_key,
                   o.semester AS group_name,
                   COUNT(1) AS total_offerings,
                   SUM(CASE WHEN EXISTS (SELECT 1 FROM supervision s
                                         WHERE s.offering_id = o.id AND s.status = '已完成')
                            THEN 1 ELSE 0 END) AS supervised_offerings,
                   ROUND(SUM(CASE WHEN EXISTS (SELECT 1 FROM supervision s
                                               WHERE s.offering_id = o.id AND s.status = '已完成')
                                  THEN 1 ELSE 0 END) * 100.0 / COUNT(1), 1) AS coverage_rate
            FROM offering o
            JOIN course c ON c.id = o.course_id
            WHERE o.offering_status = '正常开课'
            <if test="deptCode != null and deptCode != ''"> AND c.department_code = #{deptCode} </if>
            GROUP BY o.semester
            ORDER BY o.semester ASC
            </script>
            """)
    List<CoverageRow> selectCoverageBySemester(@Param("deptCode") String deptCode);

    /**
     * 覆盖率总体汇总：{总教学班数, 已督导教学班数}。
     * 用于工作台大数字与环形图，院系过滤口径同上。
     */
    @Select("""
            <script>
            SELECT COUNT(1) AS total_offerings,
                   SUM(CASE WHEN EXISTS (SELECT 1 FROM supervision s
                                         WHERE s.offering_id = o.id AND s.status = '已完成')
                            THEN 1 ELSE 0 END) AS supervised_offerings
            FROM offering o
            JOIN course c ON c.id = o.course_id
            WHERE o.offering_status = '正常开课'
            <if test="semester != null and semester != ''"> AND o.semester = #{semester} </if>
            <if test="deptCode != null and deptCode != ''"> AND c.department_code = #{deptCode} </if>
            </script>
            """)
    CoverageRow selectCoverageTotal(@Param("semester") String semester,
                                    @Param("deptCode") String deptCode);

    /** 全部使用中的学期（供筛选下拉框） */
    @Select("SELECT DISTINCT semester FROM offering ORDER BY semester DESC")
    List<String> selectSemesters();
}
