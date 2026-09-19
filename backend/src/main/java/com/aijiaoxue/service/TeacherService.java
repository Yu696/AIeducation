package com.aijiaoxue.service;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.Offering;
import com.aijiaoxue.entity.Teacher;
import com.aijiaoxue.entity.view.CourseDetailView;
import com.aijiaoxue.entity.view.OfferingView;
import com.aijiaoxue.entity.view.TeacherWorkloadRow;
import com.aijiaoxue.mapper.CourseMapper;
import com.aijiaoxue.mapper.OfferingMapper;
import com.aijiaoxue.mapper.TeacherMapper;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.DataScopeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师信息服务（US-02）。
 *
 * <p>故事原文：「作为教研室主任，我想查看5位教师的课程简介与开课情况，以便统筹排课」，
 * 因此 {@link #detail(Long, CurrentUser)} 必须一次性返回教师的课程简介清单与开课清单。
 */
@Service
public class TeacherService {

    private final TeacherMapper teacherMapper;
    private final CourseMapper courseMapper;
    private final OfferingMapper offeringMapper;
    private final DataScopeService dataScopeService;

    public TeacherService(TeacherMapper teacherMapper, CourseMapper courseMapper,
                          OfferingMapper offeringMapper, DataScopeService dataScopeService) {
        this.teacherMapper = teacherMapper;
        this.courseMapper = courseMapper;
        this.offeringMapper = offeringMapper;
        this.dataScopeService = dataScopeService;
    }

    /** 教师列表分页 */
    public PageResult<Teacher> page(long page, long size, String keyword, String deptCode,
                                    String title, CurrentUser user) {
        Page<Teacher> p = new Page<>(page, size);
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        IPage<Teacher> result = teacherMapper.selectTeacherPage(p, keyword, effDept, title);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 导出用教师列表 */
    public List<Teacher> listForExport(String keyword, String deptCode, String title, CurrentUser user) {
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        return teacherMapper.selectTeacherList(keyword, effDept, title);
    }

    /**
     * 教师详情 = 基本信息 + 课程简介清单 + 开课清单。
     * 这三部分正是故事原文要求「查看课程简介与开课情况」的落地。
     */
    public Map<String, Object> detail(Long id, CurrentUser user) {
        Teacher teacher = teacherMapper.selectById(id);
        if (teacher == null) {
            throw BusinessException.notFound("教师不存在");
        }
        dataScopeService.assertTeacherAccessible(id, user);

        List<CourseDetailView> courses = courseMapper.selectCoursesOfTeacher(id);
        List<OfferingView> offerings = offeringMapper.selectOfferingsByTeacher(id);

        // 汇总指标：课程数、教学班数、覆盖学生人次
        int studentTotal = offerings.stream()
                .map(OfferingView::getStudentCount)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("teacher", teacher);
        data.put("courses", courses);
        data.put("offerings", offerings);
        data.put("courseCount", courses.size());
        data.put("offeringCount", offerings.size());
        data.put("studentTotal", studentTotal);
        return data;
    }

    /** 教师的开课清单 */
    public List<OfferingView> offeringsOf(Long teacherId, CurrentUser user) {
        if (teacherMapper.selectById(teacherId) == null) {
            throw BusinessException.notFound("教师不存在");
        }
        dataScopeService.assertTeacherAccessible(teacherId, user);
        return offeringMapper.selectOfferingsByTeacher(teacherId);
    }

    /** 教师授课分布（工作台图表） */
    public List<TeacherWorkloadRow> workload(String deptCode, String semester, Long teacherId, CurrentUser user) {
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        Long effTeacher = dataScopeService.effectiveTeacher(teacherId, user);
        return teacherMapper.selectTeacherWorkload(effDept, semester, effTeacher, null);
    }

    /** 新建教师 */
    @Transactional(rollbackFor = Exception.class)
    public Teacher create(Teacher teacher) {
        if (existsByNo(teacher.getTeacherNo(), null)) {
            throw BusinessException.conflict("教师工号已存在：" + teacher.getTeacherNo());
        }
        teacher.setId(null);
        teacher.setCreatedAt(LocalDateTime.now());
        teacher.setUpdatedAt(LocalDateTime.now());
        teacherMapper.insert(teacher);
        return teacher;
    }

    /** 修改教师 */
    @Transactional(rollbackFor = Exception.class)
    public Teacher update(Long id, Teacher teacher) {
        Teacher exist = teacherMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("教师不存在");
        }
        if (teacher.getTeacherNo() != null && existsByNo(teacher.getTeacherNo(), id)) {
            throw BusinessException.conflict("教师工号已存在：" + teacher.getTeacherNo());
        }
        teacher.setId(id);
        teacher.setUpdatedAt(LocalDateTime.now());
        teacherMapper.updateById(teacher);
        return teacherMapper.selectById(id);
    }

    /** 删除教师：仍承担教学班时拒绝删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (teacherMapper.selectById(id) == null) {
            throw BusinessException.notFound("教师不存在");
        }
        Long count = offeringMapper.selectCount(Wrappers.<Offering>lambdaQuery().eq(Offering::getTeacherId, id));
        if (count != null && count > 0) {
            throw BusinessException.conflict("该教师仍有 " + count + " 个教学班，请先调整开课安排");
        }
        Long owned = courseMapper.selectCount(Wrappers.<com.aijiaoxue.entity.Course>lambdaQuery()
                .eq(com.aijiaoxue.entity.Course::getOwnerTeacherId, id));
        if (owned != null && owned > 0) {
            throw BusinessException.conflict("该教师仍是 " + owned + " 门课程的负责人，请先变更负责人");
        }
        teacherMapper.deleteById(id);
    }

    /** 全部院系代码 */
    public List<String> departments() {
        return teacherMapper.selectDepartments();
    }

    /** 职称候选值 */
    public List<String> titles() {
        return List.of("教授", "副教授", "讲师", "助教");
    }

    private boolean existsByNo(String teacherNo, Long excludeId) {
        if (teacherNo == null || teacherNo.isBlank()) {
            return false;
        }
        Long count = teacherMapper.selectCount(Wrappers.<Teacher>lambdaQuery()
                .eq(Teacher::getTeacherNo, teacherNo)
                .ne(excludeId != null, Teacher::getId, excludeId));
        return count != null && count > 0;
    }
}
