package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.TeachingResource;
import com.aijiaoxue.entity.view.ResourceView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 教学资源 Mapper。
 *
 * <p>US-03：教师「查看学生人次与课程资源，以便备课」；备课查看按教师聚合「我的课程 → 可用资源」。
 */
@Mapper
public interface ResourceMapper extends BaseMapper<TeachingResource> {

    /** 资源公共查询片段：资源 + 归属教师 + 关联教学班 + 所属课程 */
    String VIEW_SQL = """
            SELECT r.id, r.resource_name, r.resource_type, r.file_url, r.file_size,
                   r.offering_id, o.offering_code, o.course_id, c.course_code, c.course_name, o.semester,
                   r.teacher_id, t.name AS teacher_name, r.description, r.status, r.created_at
            FROM teaching_resource r
            LEFT JOIN offering o ON o.id = r.offering_id
            LEFT JOIN course c ON c.id = o.course_id
            LEFT JOIN teacher t ON t.id = r.teacher_id
            """;

    /**
     * 资源列表分页。
     *
     * @param teacherId 教师 SELF 数据范围过滤；同时匹配「归属教师」与「该教师所授教学班的资源」
     */
    @Select("""
            <script>
            """ + VIEW_SQL + """
            <where>
              <if test="keyword != null and keyword != ''">
                AND (r.resource_name LIKE CONCAT('%', #{keyword}, '%')
                     OR r.description LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="resourceType != null and resourceType != ''"> AND r.resource_type = #{resourceType} </if>
              <if test="status != null and status != ''"> AND r.status = #{status} </if>
              <if test="offeringId != null"> AND r.offering_id = #{offeringId} </if>
              <if test="teacherId != null">
                AND (r.teacher_id = #{teacherId}
                     OR EXISTS (SELECT 1 FROM offering o2
                                WHERE o2.id = r.offering_id
                                  AND (o2.teacher_id = #{teacherId}
                                       OR EXISTS (SELECT 1 FROM offering_teacher ot
                                                  WHERE ot.offering_id = o2.id AND ot.teacher_id = #{teacherId}))))
              </if>
              <if test="deptCode != null and deptCode != ''">
                AND EXISTS (SELECT 1 FROM offering o3 WHERE o3.id = r.offering_id AND o3.department_code = #{deptCode})
              </if>
            </where>
            ORDER BY r.created_at DESC, r.id DESC
            </script>
            """)
    IPage<ResourceView> selectResourcePage(Page<ResourceView> page,
                                           @Param("keyword") String keyword,
                                           @Param("resourceType") String resourceType,
                                           @Param("status") String status,
                                           @Param("offeringId") Long offeringId,
                                           @Param("teacherId") Long teacherId,
                                           @Param("deptCode") String deptCode);

    /** 导出用全量资源列表 */
    @Select("""
            <script>
            """ + VIEW_SQL + """
            <where>
              <if test="keyword != null and keyword != ''">
                AND (r.resource_name LIKE CONCAT('%', #{keyword}, '%')
                     OR r.description LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="resourceType != null and resourceType != ''"> AND r.resource_type = #{resourceType} </if>
              <if test="status != null and status != ''"> AND r.status = #{status} </if>
              <if test="offeringId != null"> AND r.offering_id = #{offeringId} </if>
              <if test="teacherId != null">
                AND (r.teacher_id = #{teacherId}
                     OR EXISTS (SELECT 1 FROM offering o2
                                WHERE o2.id = r.offering_id
                                  AND (o2.teacher_id = #{teacherId}
                                       OR EXISTS (SELECT 1 FROM offering_teacher ot
                                                  WHERE ot.offering_id = o2.id AND ot.teacher_id = #{teacherId}))))
              </if>
              <if test="deptCode != null and deptCode != ''">
                AND EXISTS (SELECT 1 FROM offering o3 WHERE o3.id = r.offering_id AND o3.department_code = #{deptCode})
              </if>
            </where>
            ORDER BY r.created_at DESC, r.id DESC
            </script>
            """)
    List<ResourceView> selectResourceList(@Param("keyword") String keyword,
                                          @Param("resourceType") String resourceType,
                                          @Param("status") String status,
                                          @Param("offeringId") Long offeringId,
                                          @Param("teacherId") Long teacherId,
                                          @Param("deptCode") String deptCode);

    /** 资源详情 */
    @Select(VIEW_SQL + " WHERE r.id = #{id} ")
    ResourceView selectResourceDetail(@Param("id") Long id);

    /** 课程已挂接的资源列表（课程详情页展示） */
    @Select(VIEW_SQL + """
             WHERE r.id IN (SELECT cr.resource_id FROM course_resource cr
                            WHERE cr.course_id = #{courseId} AND cr.associated = 1)
             ORDER BY r.resource_type ASC, r.id ASC
            """)
    List<ResourceView> selectResourcesByCourse(@Param("courseId") Long courseId);

    /**
     * 备课查看：教师本人所授课程下的全部可用资源。
     *
     * <p>命中条件包含两种身份：资源归属教师本人，或资源所属教学班的任课教师包含本人
     * （含 offering_teacher 中的「合上/辅导」教师）。仅按 offering.teacher_id 匹配会漏掉
     * 合上教师能看到的备课资源。
     */
    @Select("""
            """ + VIEW_SQL + """
            WHERE r.status = '有效'
              AND (r.teacher_id = #{teacherId}
                   OR EXISTS (SELECT 1 FROM offering o4
                              WHERE o4.id = r.offering_id
                                AND (o4.teacher_id = #{teacherId}
                                     OR EXISTS (SELECT 1 FROM offering_teacher ot
                                                WHERE ot.offering_id = o4.id AND ot.teacher_id = #{teacherId}))))
            ORDER BY o.semester DESC, c.course_code ASC, r.resource_type ASC
            """)
    List<ResourceView> selectPrepResources(@Param("teacherId") Long teacherId);
}
