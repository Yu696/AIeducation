package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.Evaluation;
import com.aijiaoxue.entity.view.EvaluationSummaryRow;
import com.aijiaoxue.entity.view.EvaluationView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 教学评价 Mapper。史诗三「教学评价管理」。
 *
 * <p>Sprint 1 范围：评价记录查看 + 学生评教汇总展示；不做个性化反馈报告（实验一 Won't 清单）。
 */
@Mapper
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    String VIEW_SQL = """
            SELECT e.id, e.offering_id, o.offering_code, c.id AS course_id, c.course_code, c.course_name,
                   o.class_name, e.semester, o.teacher_id, t.name AS teacher_name,
                   e.evaluation_type, e.score, e.evaluator, e.content, e.evaluated_at
            FROM evaluation e
            LEFT JOIN offering o ON o.id = e.offering_id
            LEFT JOIN course c ON c.id = o.course_id
            LEFT JOIN teacher t ON t.id = o.teacher_id
            """;

    /**
     * 评价列表分页。
     *
     * @param teacherId    教师 SELF 数据范围过滤（只看本人所授课程的评价）
     * @param deptCode     教研室主任 DEPARTMENT 数据范围过滤
     * @param evaluationType 评价类型：学生评教/督导评教/同行评议
     */
    @Select("""
            <script>
            """ + VIEW_SQL + """
            <where>
              <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.course_code LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="offeringId != null"> AND e.offering_id = #{offeringId} </if>
              <if test="evaluationType != null and evaluationType != ''"> AND e.evaluation_type = #{evaluationType} </if>
              <if test="semester != null and semester != ''"> AND e.semester = #{semester} </if>
              <if test="scoreMin != null"> AND e.score &gt;= #{scoreMin} </if>
              <if test="scoreMax != null"> AND e.score &lt;= #{scoreMax} </if>
              <if test="teacherId != null"> AND o.teacher_id = #{teacherId} </if>
              <if test="deptCode != null and deptCode != ''"> AND o.department_code = #{deptCode} </if>
            </where>
            ORDER BY e.evaluated_at DESC, e.id DESC
            </script>
            """)
    IPage<EvaluationView> selectEvaluationPage(Page<EvaluationView> page,
                                               @Param("keyword") String keyword,
                                               @Param("offeringId") Long offeringId,
                                               @Param("evaluationType") String evaluationType,
                                               @Param("semester") String semester,
                                               @Param("scoreMin") java.math.BigDecimal scoreMin,
                                               @Param("scoreMax") java.math.BigDecimal scoreMax,
                                               @Param("teacherId") Long teacherId,
                                               @Param("deptCode") String deptCode);

    /** 导出用全量评价列表 */
    @Select("""
            <script>
            """ + VIEW_SQL + """
            <where>
              <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%') OR c.course_code LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="offeringId != null"> AND e.offering_id = #{offeringId} </if>
              <if test="evaluationType != null and evaluationType != ''"> AND e.evaluation_type = #{evaluationType} </if>
              <if test="semester != null and semester != ''"> AND e.semester = #{semester} </if>
              <if test="scoreMin != null"> AND e.score &gt;= #{scoreMin} </if>
              <if test="scoreMax != null"> AND e.score &lt;= #{scoreMax} </if>
              <if test="teacherId != null"> AND o.teacher_id = #{teacherId} </if>
              <if test="deptCode != null and deptCode != ''"> AND o.department_code = #{deptCode} </if>
            </where>
            ORDER BY e.evaluated_at DESC, e.id DESC
            </script>
            """)
    List<EvaluationView> selectEvaluationList(@Param("keyword") String keyword,
                                              @Param("offeringId") Long offeringId,
                                              @Param("evaluationType") String evaluationType,
                                              @Param("semester") String semester,
                                              @Param("scoreMin") java.math.BigDecimal scoreMin,
                                              @Param("scoreMax") java.math.BigDecimal scoreMax,
                                              @Param("teacherId") Long teacherId,
                                              @Param("deptCode") String deptCode);

    /** 评价详情 */
    @Select(VIEW_SQL + " WHERE e.id = #{id} ")
    EvaluationView selectEvaluationDetail(@Param("id") Long id);

    /**
     * 学生评教汇总：按课程聚合平均分、参评人次、最高/最低分。
     * 口径：仅统计 evaluation_type='学生评教' 的记录，平均分保留 1 位小数。
     *
     * @param semester  学期过滤
     * @param teacherId 教师 SELF 数据范围过滤
     * @param deptCode  教研室主任 DEPARTMENT 数据范围过滤
     */
    @Select("""
            <script>
            SELECT c.id AS course_id, c.course_code, c.course_name, o.teacher_id, t.name AS teacher_name,
                   e.semester, COUNT(1) AS evaluation_count, ROUND(AVG(e.score), 1) AS avg_score,
                   MAX(e.score) AS max_score, MIN(e.score) AS min_score
            FROM evaluation e
            LEFT JOIN offering o ON o.id = e.offering_id
            LEFT JOIN course c ON c.id = o.course_id
            LEFT JOIN teacher t ON t.id = o.teacher_id
            WHERE e.evaluation_type = '学生评教'
            <if test="semester != null and semester != ''"> AND e.semester = #{semester} </if>
            <if test="teacherId != null"> AND o.teacher_id = #{teacherId} </if>
            <if test="deptCode != null and deptCode != ''"> AND o.department_code = #{deptCode} </if>
            GROUP BY c.id, c.course_code, c.course_name, o.teacher_id, t.name, e.semester
            ORDER BY avg_score DESC
            </script>
            """)
    List<EvaluationSummaryRow> selectEvaluationSummary(@Param("semester") String semester,
                                                       @Param("teacherId") Long teacherId,
                                                       @Param("deptCode") String deptCode);

    /**
     * 评教分数分布（前端柱状图数据）。
     * 分档：90 分以上 / 80—89 / 70—79 / 60—69 / 60 分以下。
     *
     * @return 每条记录的 group_key 为分档名称，cnt 为该档人次
     */
    @Select("""
            <script>
            SELECT bucket AS group_key, SUM(cnt) AS cnt FROM (
              SELECT CASE
                       WHEN e.score &gt;= 90 THEN '90分以上'
                       WHEN e.score &gt;= 80 THEN '80-89分'
                       WHEN e.score &gt;= 70 THEN '70-79分'
                       WHEN e.score &gt;= 60 THEN '60-69分'
                       ELSE '60分以下' END AS bucket,
                     COUNT(1) AS cnt
              FROM evaluation e
              LEFT JOIN offering o ON o.id = e.offering_id
              WHERE e.evaluation_type = '学生评教'
              <if test="semester != null and semester != ''"> AND e.semester = #{semester} </if>
              <if test="teacherId != null"> AND o.teacher_id = #{teacherId} </if>
              <if test="deptCode != null and deptCode != ''"> AND o.department_code = #{deptCode} </if>
              GROUP BY bucket
            ) t
            GROUP BY bucket
            </script>
            """)
    List<Map<String, Object>> selectScoreDistribution(@Param("semester") String semester,
                                                      @Param("teacherId") Long teacherId,
                                                      @Param("deptCode") String deptCode);
}
