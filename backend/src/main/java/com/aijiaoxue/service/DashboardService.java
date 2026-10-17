package com.aijiaoxue.service;

import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.common.RoleCodes;
import com.aijiaoxue.entity.Course;
import com.aijiaoxue.entity.Offering;
import com.aijiaoxue.entity.Teacher;
import com.aijiaoxue.entity.TeachingResource;
import com.aijiaoxue.entity.view.CoverageRow;
import com.aijiaoxue.entity.view.EvaluationSummaryRow;
import com.aijiaoxue.entity.view.OfferingView;
import com.aijiaoxue.entity.view.TeacherWorkloadRow;
import com.aijiaoxue.mapper.CourseMapper;
import com.aijiaoxue.mapper.EvaluationMapper;
import com.aijiaoxue.mapper.OfferingMapper;
import com.aijiaoxue.mapper.ResourceMapper;
import com.aijiaoxue.mapper.SupervisionMapper;
import com.aijiaoxue.mapper.TeacherMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.DataScopeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台服务（US-02 / US-03 / US-04）。
 *
 * <p>按角色返回不同的工作台数据，统一结构为：
 * {@code {cards:[{label,value,unit,hint}], charts:{...}, ...角色专属数据}}，
 * 前端按同一套渲染逻辑处理，避免每个工作台各写一套解析代码。
 */
@Service
public class DashboardService {

    private final CourseMapper courseMapper;
    private final OfferingMapper offeringMapper;
    private final TeacherMapper teacherMapper;
    private final ResourceMapper resourceMapper;
    private final EvaluationMapper evaluationMapper;
    private final SupervisionMapper supervisionMapper;
    private final OfferingService offeringService;
    private final TeacherService teacherService;
    private final EvaluationService evaluationService;
    private final UserService userService;
    private final DataScopeService dataScopeService;

    public DashboardService(CourseMapper courseMapper, OfferingMapper offeringMapper,
                            TeacherMapper teacherMapper, ResourceMapper resourceMapper,
                            EvaluationMapper evaluationMapper, SupervisionMapper supervisionMapper,
                            OfferingService offeringService, TeacherService teacherService,
                            EvaluationService evaluationService, UserService userService,
                            DataScopeService dataScopeService) {
        this.courseMapper = courseMapper;
        this.offeringMapper = offeringMapper;
        this.teacherMapper = teacherMapper;
        this.resourceMapper = resourceMapper;
        this.evaluationMapper = evaluationMapper;
        this.supervisionMapper = supervisionMapper;
        this.offeringService = offeringService;
        this.teacherService = teacherService;
        this.evaluationService = evaluationService;
        this.userService = userService;
        this.dataScopeService = dataScopeService;
    }

    /** 按当前角色路由到对应工作台 */
    public Map<String, Object> summary(CurrentUser user) {
        return switch (user.getRoleCode()) {
            case RoleCodes.ADMIN -> admin(user);
            case RoleCodes.DIRECTOR -> director(user);
            case RoleCodes.TEACHER -> teacher(user);
            case RoleCodes.SUPERVISOR -> supervisor(user);
            default -> courseAdmin(user);
        };
    }

    // ==================== 教研室主任工作台（US-02） ====================

    /**
     * 主任工作台：本院系课程/开课/教师/资源/覆盖率/评教概览 + 教师授课分布。
     */
    public Map<String, Object> director(CurrentUser user) {
        String dept = dataScopeService.effectiveDept(null, user);

        long courseCount = count(courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(dept != null, Course::getDepartmentCode, dept)));
        long teacherCount = count(teacherMapper.selectCount(Wrappers.<Teacher>lambdaQuery()
                .eq(dept != null, Teacher::getDepartmentCode, dept)));
        // 开课数按课程院系统计，与覆盖率口径保持一致
        long offeringCount = count(offeringMapper.selectCount(Wrappers.<Offering>lambdaQuery()
                .inSql(dept != null, Offering::getCourseId,
                        "SELECT id FROM course WHERE department_code = '" + dept + "'")
                .eq(Offering::getOfferingStatus, "正常开课")));
        long resourceCount = resourceMapper.selectResourceList(null, null, null, null, null, dept).size();

        Map<String, Object> coverage = offeringService.coverage(null, dept, "department", user);
        Map<String, Object> eval = evaluationService.summary(null, null, null, user);

        List<TeacherWorkloadRow> workload = teacherService.workload(dept, null, null, user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", RoleCodes.DIRECTOR);
        data.put("departmentCode", dept);
        data.put("departmentName", resolveDeptName(dept));
        data.put("cards", List.of(
                card("课程总数", courseCount, "门", "本院系课程库"),
                card("教师总数", teacherCount, "人", "本院系在册教师"),
                card("正常开课教学班", offeringCount, "个", "当前统计口径为正常开课"),
                card("教学资源", resourceCount, "条", "本院系教学班挂接资源"),
                card("督导覆盖率", coverage.get("coverageRate"), "%", "已完成听课 ÷ 正常开课"),
                card("学生评教均分", eval.get("overallAvgScore"), "分", "按参评人次加权")));
        data.put("charts", Map.of(
                "teacherWorkload", workload,
                "coverageByDepartment", coverage.get("byDepartment"),
                "scoreDistribution", eval.get("distribution")));
        data.put("coverage", coverage);
        data.put("evaluation", eval);
        data.put("topOfferings", offeringService.page(1, 8, null, null, null, dept, "正常开课", null, user).getRecords());
        return data;
    }

    // ==================== 教师工作台（US-03） ====================

    /**
     * 教师工作台：我的课程数、我的教学班数、学生人次、资源数、我的评教均分。
     */
    public Map<String, Object> teacher(CurrentUser user) {
        Long teacherId = user.requireTeacherId();
        Teacher teacher = teacherMapper.selectById(teacherId);

        List<OfferingView> myOfferings = offeringMapper.selectOfferingsByTeacher(teacherId);
        long courseCount = myOfferings.stream().map(OfferingView::getCourseId).filter(java.util.Objects::nonNull).distinct().count();
        long offeringCount = myOfferings.stream()
                .filter(o -> "正常开课".equals(o.getOfferingStatus())).count();
        int studentTotal = myOfferings.stream().map(OfferingView::getStudentCount)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();

        List<com.aijiaoxue.entity.view.ResourceView> resources =
                resourceMapper.selectPrepResources(teacherId);

        Map<String, Object> eval = evaluationService.summary(null, null, null, user);
        Long evaluationCount = evaluationMapper.selectCount(Wrappers.<com.aijiaoxue.entity.Evaluation>lambdaQuery()
                .inSql(com.aijiaoxue.entity.Evaluation::getOfferingId,
                        "SELECT id FROM offering WHERE teacher_id = " + teacherId));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", RoleCodes.TEACHER);
        data.put("teacherName", teacher == null ? user.getRealName() : teacher.getName());
        data.put("departmentName", teacher == null ? null : teacher.getDepartmentName());
        data.put("cards", List.of(
                card("我的课程", courseCount, "门", "本人负责或主讲"),
                card("我的教学班", offeringCount, "个", "状态为正常开课"),
                card("学生人次", studentTotal, "人次", "按教学班汇总"),
                card("可用教学资源", resources.size(), "条", "仅统计有效资源"),
                card("我的评教均分", eval.get("overallAvgScore"), "分", "学生评教加权平均"),
                card("我的评价记录", count(evaluationCount), "条", "含学生评教与督导评教")));
        data.put("charts", Map.of(
                "scoreDistribution", eval.get("distribution"),
                "offeringStatus", buildStatusChart(myOfferings)));
        data.put("offerings", myOfferings);
        data.put("resources", resources);
        data.put("evaluation", eval);
        return data;
    }

    // ==================== 教学督导工作台（US-04） ====================

    /**
     * 督导工作台：开课清单概览、覆盖率、待安排听评课清单、按学期覆盖率趋势。
     */
    public Map<String, Object> supervisor(CurrentUser user) {
        Map<String, Object> coverage = offeringService.coverage(null, null, "department", user);
        long pending = supervisionMapper.countPendingOfferings(null);
        long finished = count(supervisionMapper.selectCount(Wrappers.<com.aijiaoxue.entity.Supervision>lambdaQuery()
                .eq(com.aijiaoxue.entity.Supervision::getStatus, "已完成")));
        long arranged = count(supervisionMapper.selectCount(Wrappers.<com.aijiaoxue.entity.Supervision>lambdaQuery()
                .eq(com.aijiaoxue.entity.Supervision::getStatus, "已安排")));
        long totalOfferings = count(offeringMapper.selectCount(Wrappers.<Offering>lambdaQuery()
                .eq(Offering::getOfferingStatus, "正常开课")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", RoleCodes.SUPERVISOR);
        data.put("cards", List.of(
                card("正常开课教学班", totalOfferings, "个", "全校统计口径"),
                card("已完成听课", finished, "次", "status=已完成"),
                card("已安排待听课", arranged, "次", "status=已安排"),
                card("待安排听课", pending, "个", "尚无已完成听课记录的教学班"),
                card("督导覆盖率", coverage.get("coverageRate"), "%", "已完成听课 ÷ 正常开课")));
        data.put("charts", Map.of(
                "coverageByDepartment", coverage.get("byDepartment"),
                "coverageBySemester", coverage.get("bySemester")));
        data.put("coverage", coverage);
        data.put("pendingOfferings", supervisionServicePending(user));
        return data;
    }

    // ==================== 系统管理员工作台 ====================

    /** 管理员工作台：账号数、角色分布、操作日志摘要、数据总量 */
    public Map<String, Object> admin(CurrentUser user) {
        long userCount = userService.userCount();
        long enabledCount = userService.enabledUserCount();
        long courseCount = count(courseMapper.selectCount(null));
        long offeringCount = count(offeringMapper.selectCount(null));
        long teacherCount = count(teacherMapper.selectCount(null));
        long resourceCount = count(resourceMapper.selectCount(Wrappers.<TeachingResource>lambdaQuery()));

        Map<String, Object> coverage = offeringService.coverage(null, null, "department", user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", RoleCodes.ADMIN);
        data.put("cards", List.of(
                card("账号总数", userCount, "个", "含全部角色"),
                card("启用账号", enabledCount, "个", "enabled=1"),
                card("教师档案", teacherCount, "人", "全校在册"),
                card("课程总数", courseCount, "门", "全部院系"),
                card("开课记录", offeringCount, "个", "含停开与已结课"),
                card("教学资源", resourceCount, "条", "全部资源"),
                card("督导覆盖率", coverage.get("coverageRate"), "%", "已完成听课 ÷ 正常开课")));
        data.put("charts", Map.of(
                "roleDistribution", roleDistribution(),
                "coverageByDepartment", coverage.get("byDepartment")));
        return data;
    }

    // ==================== 课程管理员工作台 ====================

    /** 课程管理员工作台：课程库与开课只读概览 */
    public Map<String, Object> courseAdmin(CurrentUser user) {
        long courseCount = count(courseMapper.selectCount(null));
        long offeringCount = count(offeringMapper.selectCount(null));
        long published = count(courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getStatus, "已发布")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", RoleCodes.COURSE_ADMIN);
        data.put("cards", List.of(
                card("课程总数", courseCount, "门", "课程库全部"),
                card("已发布课程", published, "门", "status=已发布"),
                card("开课记录", offeringCount, "个", "含全部状态")));
        data.put("charts", Map.of("courseType", buildCourseTypeChart()));
        return data;
    }

    // ==================== 内部工具方法 ====================

    /** 待安排听评课清单（督导工作台表格） */
    private List<OfferingView> supervisionServicePending(CurrentUser user) {
        return offeringMapper.selectOfferingList(null, null, null, null, null, 0, 1);
    }

    /** 教学班状态分布（教师工作台图表） */
    private List<Map<String, Object>> buildStatusChart(List<OfferingView> offerings) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (OfferingView o : offerings) {
            String status = o.getOfferingStatus() == null ? "未知" : o.getOfferingStatus();
            counter.merge(status, 1, Integer::sum);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        counter.forEach((k, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", k);
            m.put("value", v);
            list.add(m);
        });
        return list;
    }

    /** 课程类型分布（课程管理员工作台图表） */
    private List<Map<String, Object>> buildCourseTypeChart() {
        List<Course> courses = courseMapper.selectList(null);
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (Course c : courses) {
            String type = c.getCourseType() == null ? "未分类" : c.getCourseType();
            counter.merge(type, 1, Integer::sum);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        counter.forEach((k, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", k);
            m.put("value", v);
            list.add(m);
        });
        return list;
    }

    /** 角色分布 */
    private List<Map<String, Object>> roleDistribution() {
        return userService.roleDistribution();
    }

    /** 院系名称解析 */
    private String resolveDeptName(String deptCode) {
        if (deptCode == null) {
            return null;
        }
        List<Teacher> list = teacherMapper.selectList(Wrappers.<Teacher>lambdaQuery()
                .eq(Teacher::getDepartmentCode, deptCode).last("LIMIT 1"));
        return list == null || list.isEmpty() ? deptCode : list.get(0).getDepartmentName();
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    /** 统一统计卡片结构 */
    private Map<String, Object> card(String label, Object value, String unit, String hint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value == null ? 0 : value);
        m.put("unit", unit);
        m.put("hint", hint);
        return m;
    }

    /** 覆盖率百分比格式化（保留 1 位小数） */
    @SuppressWarnings("unused")
    private BigDecimal formatRate(BigDecimal rate) {
        return rate == null ? BigDecimal.ZERO : rate.setScale(1, RoundingMode.HALF_UP);
    }
}
