package com.aijiaoxue.service;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.Course;
import com.aijiaoxue.entity.Evaluation;
import com.aijiaoxue.entity.Offering;
import com.aijiaoxue.entity.OfferingTeacher;
import com.aijiaoxue.entity.StudentCount;
import com.aijiaoxue.entity.Teacher;
import com.aijiaoxue.entity.view.CoverageRow;
import com.aijiaoxue.entity.view.OfferingView;
import com.aijiaoxue.mapper.CourseMapper;
import com.aijiaoxue.mapper.EvaluationMapper;
import com.aijiaoxue.mapper.OfferingMapper;
import com.aijiaoxue.mapper.OfferingTeacherMapper;
import com.aijiaoxue.mapper.StudentCountMapper;
import com.aijiaoxue.mapper.SupervisionMapper;
import com.aijiaoxue.mapper.TeacherMapper;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.DataScopeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开课（教学班）服务。
 *
 * <p>覆盖 US-02「开课情况查询」、US-03「教师查看本人教学班学生人次」、
 * US-04「开课清单与督导覆盖率」。
 */
@Service
public class OfferingService {

    private final OfferingMapper offeringMapper;
    private final OfferingTeacherMapper offeringTeacherMapper;
    private final StudentCountMapper studentCountMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final SupervisionMapper supervisionMapper;
    private final EvaluationMapper evaluationMapper;
    private final DataScopeService dataScopeService;

    public OfferingService(OfferingMapper offeringMapper, OfferingTeacherMapper offeringTeacherMapper,
                           StudentCountMapper studentCountMapper, CourseMapper courseMapper,
                           TeacherMapper teacherMapper, SupervisionMapper supervisionMapper,
                           EvaluationMapper evaluationMapper, DataScopeService dataScopeService) {
        this.offeringMapper = offeringMapper;
        this.offeringTeacherMapper = offeringTeacherMapper;
        this.studentCountMapper = studentCountMapper;
        this.courseMapper = courseMapper;
        this.teacherMapper = teacherMapper;
        this.supervisionMapper = supervisionMapper;
        this.evaluationMapper = evaluationMapper;
        this.dataScopeService = dataScopeService;
    }

    /** 开课列表分页（含数据范围收窄：教师只看本人，主任只看本院系） */
    public PageResult<OfferingView> page(long page, long size, String keyword, String semester,
                                         Long teacherId, String deptCode, String offeringStatus,
                                         Integer supervised, CurrentUser user) {
        Page<OfferingView> p = new Page<>(page, size);
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        Long effTeacher = dataScopeService.effectiveTeacher(teacherId, user);
        IPage<OfferingView> result = offeringMapper.selectOfferingPage(p, keyword, semester, effTeacher,
                effDept, offeringStatus, supervised, null);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 导出用开课列表 */
    public List<OfferingView> listForExport(String keyword, String semester, Long teacherId,
                                            String deptCode, String offeringStatus, Integer supervised,
                                            CurrentUser user) {
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        Long effTeacher = dataScopeService.effectiveTeacher(teacherId, user);
        return offeringMapper.selectOfferingList(keyword, semester, effTeacher, effDept,
                offeringStatus, supervised, null);
    }

    /** 开课详情，含任课教师、学生人次明细、听评课记录、评价数 */
    public Map<String, Object> detail(Long id, CurrentUser user) {
        OfferingView view = offeringMapper.selectOfferingDetail(id);
        dataScopeService.assertOfferingAccessible(view, user);
        List<StudentCount> counts = studentCountMapper.selectList(
                Wrappers.<StudentCount>lambdaQuery().eq(StudentCount::getOfferingId, id)
                        .orderByAsc(StudentCount::getStatDate));
        List<Map<String, Object>> teachers = offeringTeacherMapper.selectTeachersOfOffering(id);
        Long evaluationCount = evaluationMapper.selectCount(
                Wrappers.<Evaluation>lambdaQuery().eq(Evaluation::getOfferingId, id));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("offering", view);
        data.put("studentCounts", counts);
        data.put("teachers", teachers);
        data.put("evaluationCount", evaluationCount);
        return data;
    }

    /** 新建开课 */
    @Transactional(rollbackFor = Exception.class)
    public Offering create(Offering offering, Long coTeacherId, String coTeacherRole) {
        validate(offering);
        if (existsByCode(offering.getOfferingCode(), null)) {
            throw BusinessException.conflict("教学班编号已存在：" + offering.getOfferingCode());
        }
        offering.setId(null);
        if (offering.getOfferingStatus() == null || offering.getOfferingStatus().isBlank()) {
            offering.setOfferingStatus("正常开课");
        }
        fillDepartment(offering);
        offering.setCreatedAt(LocalDateTime.now());
        offering.setUpdatedAt(LocalDateTime.now());
        offeringMapper.insert(offering);
        syncCoTeacher(offering.getId(), offering.getTeacherId(), coTeacherId, coTeacherRole);
        return offering;
    }

    /** 修改开课 */
    @Transactional(rollbackFor = Exception.class)
    public Offering update(Long id, Offering offering, Long coTeacherId, String coTeacherRole) {
        Offering exist = offeringMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("教学班不存在");
        }
        if (offering.getOfferingCode() != null && existsByCode(offering.getOfferingCode(), id)) {
            throw BusinessException.conflict("教学班编号已存在：" + offering.getOfferingCode());
        }
        offering.setId(id);
        offering.setUpdatedAt(LocalDateTime.now());
        fillDepartment(offering);
        offeringMapper.updateById(offering);
        syncCoTeacher(id, offering.getTeacherId() == null ? exist.getTeacherId() : offering.getTeacherId(),
                coTeacherId, coTeacherRole);
        return offeringMapper.selectById(id);
    }

    /** 删除开课，同时清理任课教师关联、听评课记录与人数明细 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Offering exist = offeringMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("教学班不存在");
        }
        supervisionMapper.deleteByOfferingId(id);
        offeringTeacherMapper.delete(Wrappers.<OfferingTeacher>lambdaQuery().eq(OfferingTeacher::getOfferingId, id));
        studentCountMapper.delete(Wrappers.<StudentCount>lambdaQuery().eq(StudentCount::getOfferingId, id));
        offeringMapper.deleteById(id);
    }

    /** 某教学班的学生人次明细 */
    public List<StudentCount> studentCounts(Long offeringId, CurrentUser user) {
        OfferingView view = offeringMapper.selectOfferingDetail(offeringId);
        dataScopeService.assertOfferingAccessible(view, user);
        return studentCountMapper.selectList(Wrappers.<StudentCount>lambdaQuery()
                .eq(StudentCount::getOfferingId, offeringId)
                .orderByAsc(StudentCount::getStatDate));
    }

    /**
     * 新增/更新学生人次记录，并同步 offering.student_count 汇总值，
     * 保证列表页与明细页口径一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentCount saveStudentCount(Long offeringId, StudentCount count) {
        Offering offering = offeringMapper.selectById(offeringId);
        if (offering == null) {
            throw BusinessException.notFound("教学班不存在");
        }
        count.setOfferingId(offeringId);
        if (count.getId() == null) {
            studentCountMapper.insert(count);
        } else {
            studentCountMapper.updateById(count);
        }
        refreshStudentCount(offeringId);
        return count;
    }

    /** 重新汇总某教学班的学生人次 */
    @Transactional(rollbackFor = Exception.class)
    public void refreshStudentCount(Long offeringId) {
        long total = studentCountMapper.sumByOffering(offeringId);
        Offering update = new Offering();
        update.setId(offeringId);
        update.setStudentCount((int) total);
        update.setUpdatedAt(LocalDateTime.now());
        offeringMapper.updateById(update);
    }

    /** 全部使用中的学期 */
    public List<String> semesters() {
        return offeringMapper.selectSemesters();
    }

    /**
     * 督导覆盖率统计（US-04）。
     *
     * @param semester 学期过滤，null 表示全部
     * @param groupBy  by=department 按院系，by=semester 按学期
     * @return total / supervised / coverageRate / byDepartment / bySemester
     */
    public Map<String, Object> coverage(String semester, String deptCode, String groupBy, CurrentUser user) {
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        CoverageRow total = offeringMapper.selectCoverageTotal(semester, effDept);
        List<CoverageRow> byDept = offeringMapper.selectCoverageByDepartment(semester, effDept);
        List<CoverageRow> bySemester = offeringMapper.selectCoverageBySemester(effDept);

        int totalOfferings = total == null || total.getTotalOfferings() == null ? 0 : total.getTotalOfferings();
        int supervised = total == null || total.getSupervisedOfferings() == null ? 0
                : total.getSupervisedOfferings();
        BigDecimal rate = totalOfferings == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(supervised * 100.0 / totalOfferings).setScale(1, RoundingMode.HALF_UP);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("semester", semester);
        data.put("totalOfferings", totalOfferings);
        data.put("supervisedOfferings", supervised);
        data.put("unsupervisedOfferings", Math.max(0, totalOfferings - supervised));
        data.put("coverageRate", rate);
        data.put("groupBy", groupBy == null ? "department" : groupBy);
        data.put("rows", "semester".equalsIgnoreCase(groupBy) ? bySemester : byDept);
        data.put("byDepartment", byDept);
        data.put("bySemester", bySemester);
        // 覆盖率计算口径随数据一起返回，前端页面直接展示，避免口径被误读
        data.put("caliber", "覆盖率 = 统计周期内存在已完成的听评课记录（supervision.status='已完成'）的"
                + "「正常开课」教学班数 ÷ 统计周期内「正常开课」的教学班总数 × 100%；"
                + "按教学班（offering）去重计数，同一教学班多次听课只计 1 次。");
        return data;
    }

    /** 覆盖率导出数据 */
    public Map<String, List<CoverageRow>> coverageForExport(String semester, String deptCode, CurrentUser user) {
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        Map<String, List<CoverageRow>> map = new LinkedHashMap<>();
        map.put("byDepartment", offeringMapper.selectCoverageByDepartment(semester, effDept));
        map.put("bySemester", offeringMapper.selectCoverageBySemester(effDept));
        return map;
    }

    /** 校验开课必填项与引用完整性 */
    private void validate(Offering offering) {
        if (offering.getCourseId() == null) {
            throw new BusinessException("请选择开课课程");
        }
        if (courseMapper.selectById(offering.getCourseId()) == null) {
            throw BusinessException.notFound("所选课程不存在");
        }
        if (offering.getOfferingCode() == null || offering.getOfferingCode().isBlank()) {
            throw new BusinessException("教学班编号不能为空");
        }
        if (offering.getSemester() == null || offering.getSemester().isBlank()) {
            throw new BusinessException("学期不能为空");
        }
        if (offering.getTeacherId() != null && teacherMapper.selectById(offering.getTeacherId()) == null) {
            throw BusinessException.notFound("所选教师不存在");
        }
    }

    private boolean existsByCode(String code, Long excludeId) {
        Long count = offeringMapper.selectCount(Wrappers.<Offering>lambdaQuery()
                .eq(Offering::getOfferingCode, code)
                .ne(excludeId != null, Offering::getId, excludeId));
        return count != null && count > 0;
    }

    /** 未填院系时按课程院系与教师院系自动补齐 */
    private void fillDepartment(Offering offering) {
        if (offering.getDepartmentCode() != null && !offering.getDepartmentCode().isBlank()) {
            return;
        }
        Course course = offering.getCourseId() == null ? null : courseMapper.selectById(offering.getCourseId());
        if (course != null && course.getDepartmentCode() != null) {
            offering.setDepartmentCode(course.getDepartmentCode());
            return;
        }
        if (offering.getTeacherId() != null) {
            Teacher t = teacherMapper.selectById(offering.getTeacherId());
            if (t != null) {
                offering.setDepartmentCode(t.getDepartmentCode());
            }
        }
    }

    /** 同步「合上/辅导」教师关联记录 */
    private void syncCoTeacher(Long offeringId, Long mainTeacherId, Long coTeacherId, String roleType) {
        offeringTeacherMapper.delete(Wrappers.<OfferingTeacher>lambdaQuery()
                .eq(OfferingTeacher::getOfferingId, offeringId));
        List<OfferingTeacher> list = new ArrayList<>();
        if (mainTeacherId != null) {
            OfferingTeacher main = new OfferingTeacher();
            main.setOfferingId(offeringId);
            main.setTeacherId(mainTeacherId);
            main.setRoleType("主讲");
            list.add(main);
        }
        if (coTeacherId != null && !coTeacherId.equals(mainTeacherId)) {
            OfferingTeacher co = new OfferingTeacher();
            co.setOfferingId(offeringId);
            co.setTeacherId(coTeacherId);
            co.setRoleType(roleType == null || roleType.isBlank() ? "合上" : roleType);
            list.add(co);
        }
        list.forEach(offeringTeacherMapper::insert);
    }
}
