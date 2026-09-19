package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.Supervision;
import com.aijiaoxue.entity.view.SupervisionView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 听评课（督导）Mapper。US-04「查看课程开设与督导覆盖率，以便安排听评课」。
 */
@Mapper
public interface SupervisionMapper extends BaseMapper<Supervision> {

    String VIEW_SQL = """
            SELECT s.id, s.offering_id, o.offering_code, c.id AS course_id, c.course_code, c.course_name,
                   o.class_name, s.semester, o.teacher_id, t.name AS teacher_name,
                   s.supervisor_id, sp.name AS supervisor_name,
                   s.plan_date, s.finish_date, s.status, s.score, s.comment
            FROM supervision s
            LEFT JOIN offering o ON o.id = s.offering_id
            LEFT JOIN course c ON c.id = o.course_id
            LEFT JOIN teacher t ON t.id = o.teacher_id
            LEFT JOIN teacher sp ON sp.id = s.supervisor_id
            """;

    /**
     * 听评课列表分页。
     *
     * @param deptCode 教研室主任 DEPARTMENT 数据范围过滤
     */
    @Select("""
            <script>
            """ + VIEW_SQL + """
            <where>
              <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.course_code LIKE CONCAT('%', #{keyword}, '%')
                     OR o.class_name LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="offeringId != null"> AND s.offering_id = #{offeringId} </if>
              <if test="supervisorId != null"> AND s.supervisor_id = #{supervisorId} </if>
              <if test="semester != null and semester != ''"> AND s.semester = #{semester} </if>
              <if test="status != null and status != ''"> AND s.status = #{status} </if>
              <if test="deptCode != null and deptCode != ''"> AND o.department_code = #{deptCode} </if>
            </where>
            ORDER BY s.plan_date DESC, s.id DESC
            </script>
            """)
    IPage<SupervisionView> selectSupervisionPage(Page<SupervisionView> page,
                                                 @Param("keyword") String keyword,
                                                 @Param("offeringId") Long offeringId,
                                                 @Param("supervisorId") Long supervisorId,
                                                 @Param("semester") String semester,
                                                 @Param("status") String status,
                                                 @Param("deptCode") String deptCode);

    /** 导出用全量听评课列表 */
    @Select("""
            <script>
            """ + VIEW_SQL + """
            <where>
              <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%') OR o.class_name LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="offeringId != null"> AND s.offering_id = #{offeringId} </if>
              <if test="supervisorId != null"> AND s.supervisor_id = #{supervisorId} </if>
              <if test="semester != null and semester != ''"> AND s.semester = #{semester} </if>
              <if test="status != null and status != ''"> AND s.status = #{status} </if>
              <if test="deptCode != null and deptCode != ''"> AND o.department_code = #{deptCode} </if>
            </where>
            ORDER BY s.plan_date DESC, s.id DESC
            </script>
            """)
    List<SupervisionView> selectSupervisionList(@Param("keyword") String keyword,
                                                @Param("offeringId") Long offeringId,
                                                @Param("supervisorId") Long supervisorId,
                                                @Param("semester") String semester,
                                                @Param("status") String status,
                                                @Param("deptCode") String deptCode);

    /** 听评课详情 */
    @Select(VIEW_SQL + " WHERE s.id = #{id} ")
    SupervisionView selectSupervisionDetail(@Param("id") Long id);

    /** 待安排听评课的教学班数（督导工作台卡片） */
    @Select("""
            <script>
            SELECT COUNT(1) FROM offering o
            WHERE o.offering_status = '正常开课'
              AND NOT EXISTS (SELECT 1 FROM supervision s WHERE s.offering_id = o.id AND s.status = '已完成')
            <if test="deptCode != null and deptCode != ''"> AND o.department_code = #{deptCode} </if>
            </script>
            """)
    long countPendingOfferings(@Param("deptCode") String deptCode);

    /** 解除某教学班的全部听评课安排（删除教学班时级联清理） */
    @Delete("DELETE FROM supervision WHERE offering_id = #{offeringId}")
    int deleteByOfferingId(@Param("offeringId") Long offeringId);
}
