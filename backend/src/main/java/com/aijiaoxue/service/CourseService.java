package com.aijiaoxue.service;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.dto.ImportResult;
import com.aijiaoxue.entity.Course;
import com.aijiaoxue.entity.CourseIntro;
import com.aijiaoxue.entity.CourseResource;
import com.aijiaoxue.entity.Offering;
import com.aijiaoxue.entity.Teacher;
import com.aijiaoxue.entity.TeachingResource;
import com.aijiaoxue.entity.view.CourseDetailView;
import com.aijiaoxue.entity.view.OfferingView;
import com.aijiaoxue.entity.view.ResourceView;
import com.aijiaoxue.mapper.CourseIntroMapper;
import com.aijiaoxue.mapper.CourseMapper;
import com.aijiaoxue.mapper.CourseResourceMapper;
import com.aijiaoxue.mapper.OfferingMapper;
import com.aijiaoxue.mapper.ResourceMapper;
import com.aijiaoxue.mapper.TeacherMapper;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.DataScopeService;
import com.aijiaoxue.util.CsvUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 课程信息管理服务（史诗一「查课程」，US-02 / US-05）。
 *
 * <p>职责：
 * <ul>
 *   <li>课程库的查询、详情（含课程简介全文、授课教师、教学班数、资源数）；</li>
 *   <li>课程与教学资源的挂接/解除挂接；</li>
 *   <li>CSV 导出与批量导入（US-05 数据导入）。</li>
 * </ul>
 *
 * <p>数据权限：由 {@link DataScopeService} 注入院系范围，教师账号自动收窄为本人相关课程。
 */
@Service
public class CourseService {

    private final CourseMapper courseMapper;
    private final CourseIntroMapper courseIntroMapper;
    private final CourseResourceMapper courseResourceMapper;
    private final OfferingMapper offeringMapper;
    private final TeacherMapper teacherMapper;
    private final ResourceMapper resourceMapper;
    private final DataScopeService dataScopeService;

    public CourseService(CourseMapper courseMapper, CourseIntroMapper courseIntroMapper,
                         CourseResourceMapper courseResourceMapper, OfferingMapper offeringMapper,
                         TeacherMapper teacherMapper, ResourceMapper resourceMapper,
                         DataScopeService dataScopeService) {
        this.courseMapper = courseMapper;
        this.courseIntroMapper = courseIntroMapper;
        this.courseResourceMapper = courseResourceMapper;
        this.offeringMapper = offeringMapper;
        this.teacherMapper = teacherMapper;
        this.resourceMapper = resourceMapper;
        this.dataScopeService = dataScopeService;
    }

    /** 课程列表分页（含数据范围收窄） */
    public PageResult<CourseDetailView> page(long page, long size, String keyword, String deptCode,
                                            String courseType, String status, CurrentUser user) {
        Page<CourseDetailView> p = new Page<>(page, size);
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        Long ownerTeacherId = dataScopeService.teacherScope(user);
        IPage<CourseDetailView> result = courseMapper.selectCoursePage(p, keyword, effDept, courseType, status, ownerTeacherId);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 导出用课程全量列表 */
    public List<CourseDetailView> listForExport(String keyword, String deptCode, String courseType,
                                                String status, CurrentUser user) {
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        Long ownerTeacherId = dataScopeService.teacherScope(user);
        return courseMapper.selectCourseList(keyword, effDept, courseType, status, ownerTeacherId);
    }

    /** 课程详情：含简介详情、负责人、教学班数、资源数、授课教师、已挂接资源 */
    public Map<String, Object> detail(Long id, CurrentUser user) {
        requireCourseAccess(id, user, "教师只能查看本人所授课程的详情");
        CourseDetailView detail = courseMapper.selectCourseDetail(id);
        List<OfferingView> offerings = offeringMapper.selectOfferingsByCourse(id);
        List<ResourceView> resources = resourceMapper.selectResourcesByCourse(id);
        return Map.of(
                "course", detail,
                "offerings", offerings,
                "resources", resources);
    }

    /**
     * 课程已挂接资源列表（US-07）。
     *
     * <p>仅返回 {@code associated=1} 的资源，并复用课程详情的数据范围校验，
     * 防止教师通过课程 ID 读取他人课程的资源清单。
     */
    public List<ResourceView> resourcesOf(Long courseId, CurrentUser user) {
        requireCourseAccess(courseId, user, "教师只能查看本人所授课程的资源");
        return resourceMapper.selectResourcesByCourse(courseId);
    }

    /** 课程编号是否已存在 */
    public boolean existsByCode(String courseCode, Long excludeId) {
        Long count = courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getCourseCode, courseCode)
                .ne(excludeId != null, Course::getId, excludeId));
        return count != null && count > 0;
    }

    /** 新建课程 */
    @Transactional(rollbackFor = Exception.class)
    public Course create(Course course, String introContent, String objective,
                         String assessmentMethod, String textbook) {
        if (existsByCode(course.getCourseCode(), null)) {
            throw BusinessException.conflict("课程编号已存在：" + course.getCourseCode());
        }
        course.setId(null);
        if (course.getStatus() == null || course.getStatus().isBlank()) {
            course.setStatus("已发布");
        }
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.insert(course);
        saveIntro(course.getId(), introContent, objective, assessmentMethod, textbook);
        return course;
    }

    /** 修改课程 */
    @Transactional(rollbackFor = Exception.class)
    public Course update(Long id, Course course, String introContent, String objective,
                         String assessmentMethod, String textbook) {
        Course exist = courseMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("课程不存在");
        }
        if (course.getCourseCode() != null && existsByCode(course.getCourseCode(), id)) {
            throw BusinessException.conflict("课程编号已存在：" + course.getCourseCode());
        }
        course.setId(id);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
        saveIntro(id, introContent, objective, assessmentMethod, textbook);
        return courseMapper.selectById(id);
    }

    /** 删除课程：已开课则拒绝，保证引用完整性（实验一风险对策：防止脏数据） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Course exist = courseMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("课程不存在");
        }
        Long offeringCount = offeringMapper.selectCount(
                Wrappers.<Offering>lambdaQuery().eq(Offering::getCourseId, id));
        if (offeringCount != null && offeringCount > 0) {
            throw BusinessException.conflict("该课程已存在 " + offeringCount + " 条开课记录，不允许删除");
        }
        courseResourceMapper.delete(Wrappers.<CourseResource>lambdaQuery().eq(CourseResource::getCourseId, id));
        courseIntroMapper.delete(Wrappers.<CourseIntro>lambdaQuery().eq(CourseIntro::getCourseId, id));
        courseMapper.deleteById(id);
    }

    /** 课程详情用：该课程的教学班 */
    public List<OfferingView> offeringsOf(Long courseId, CurrentUser user) {
        Course exist = courseMapper.selectById(courseId);
        if (exist == null) {
            throw BusinessException.notFound("课程不存在");
        }
        return offeringMapper.selectOfferingsByCourse(courseId);
    }

    /**
     * 挂接资源到课程（US-07）。
     *
     * <p>幂等语义：首次挂接新增行；重复挂接或解绑后重新挂接只把 {@code associated} 置 1，
     * 不新增行。并发重复提交触发唯一键冲突时，回查已有行并恢复为已挂接状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void attachResource(Long courseId, Long resourceId, CurrentUser user) {
        if (resourceId == null) {
            throw new BusinessException("资源ID不能为空");
        }
        requireCourseAccess(courseId, user, "教师只能向本人所授课程挂接资源");
        if (resourceMapper.selectById(resourceId) == null) {
            throw BusinessException.notFound("教学资源不存在");
        }
        requireResourceVisible(resourceId, user, "教师只能挂接本人可见范围内的教学资源");

        CourseResource exist = findCourseResource(courseId, resourceId);
        if (exist == null) {
            CourseResource cr = new CourseResource();
            cr.setCourseId(courseId);
            cr.setResourceId(resourceId);
            cr.setAssociated(1);
            try {
                courseResourceMapper.insert(cr);
            } catch (DuplicateKeyException e) {
                // 并发重复挂接：唯一键 uk_course_resource 拦截后，复用已存在行
                CourseResource concurrent = findCourseResource(courseId, resourceId);
                if (concurrent != null && !isAssociated(concurrent)) {
                    concurrent.setAssociated(1);
                    courseResourceMapper.updateById(concurrent);
                }
            }
        } else if (!isAssociated(exist)) {
            exist.setAssociated(1);
            courseResourceMapper.updateById(exist);
        }
    }

    /**
     * 解除课程与资源的挂接（US-07 软解绑）。
     *
     * <p>保留挂接行并将 {@code associated} 置 0，便于追溯；对已解绑记录再次解绑按幂等处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void detachResource(Long courseId, Long resourceId, CurrentUser user) {
        if (resourceId == null) {
            throw new BusinessException("资源ID不能为空");
        }
        requireCourseAccess(courseId, user, "教师只能解除本人所授课程的资源挂接");
        if (resourceMapper.selectById(resourceId) == null) {
            throw BusinessException.notFound("教学资源不存在");
        }
        requireResourceVisible(resourceId, user, "教师只能解除本人可见范围内资源的挂接");

        CourseResource exist = findCourseResource(courseId, resourceId);
        if (exist == null) {
            throw BusinessException.notFound("该课程未挂接此资源");
        }
        if (isAssociated(exist)) {
            exist.setAssociated(0);
            courseResourceMapper.updateById(exist);
        }
    }

    /** 课程存在性 + 教师课程数据范围校验 */
    private void requireCourseAccess(Long courseId, CurrentUser user, String forbiddenMessage) {
        if (courseMapper.selectById(courseId) == null) {
            throw BusinessException.notFound("课程不存在");
        }
        Long selfTeacherId = dataScopeService.teacherScope(user);
        if (selfTeacherId != null && !isCourseOfTeacher(courseId, selfTeacherId)) {
            throw BusinessException.forbidden(forbiddenMessage);
        }
    }

    /** 教师资源数据范围校验；非教师角色不受限 */
    private void requireResourceVisible(Long resourceId, CurrentUser user, String forbiddenMessage) {
        Long selfTeacherId = dataScopeService.teacherScope(user);
        if (selfTeacherId != null && resourceMapper.countVisibleToTeacher(resourceId, selfTeacherId) == 0) {
            throw BusinessException.forbidden(forbiddenMessage);
        }
    }

    /** 课程是否在教师负责或讲授范围内 */
    private boolean isCourseOfTeacher(Long courseId, Long teacherId) {
        return courseMapper.selectCoursesOfTeacher(teacherId).stream()
                .anyMatch(c -> courseId.equals(c.getId()));
    }

    /** 查询课程—资源挂接行（含已软解绑记录） */
    private CourseResource findCourseResource(Long courseId, Long resourceId) {
        return courseResourceMapper.selectOne(Wrappers.<CourseResource>lambdaQuery()
                .eq(CourseResource::getCourseId, courseId)
                .eq(CourseResource::getResourceId, resourceId));
    }

    private boolean isAssociated(CourseResource courseResource) {
        return courseResource.getAssociated() != null && courseResource.getAssociated() == 1;
    }

    /**
     * CSV 批量导入课程（US-05 数据导入）。
     *
     * <p>表头顺序：课程编号,课程名称,课程类型,学分,总学时,院系代码,院系名称,课程负责人,状态,课程简介
     * <br>编号已存在时执行更新，否则新增；必填缺失的行跳过并记录原因。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importCsv(MultipartFile file) {
        List<List<String>> rows = CsvUtils.parse(file);
        if (rows.size() <= 1) {
            throw new BusinessException("CSV 文件中没有可导入的数据行");
        }
        ImportResult result = new ImportResult();
        List<List<String>> dataRows = rows.subList(1, rows.size());
        result.setTotal(dataRows.size());

        for (int i = 0; i < dataRows.size(); i++) {
            List<String> row = dataRows.get(i);
            int lineNo = i + 2;
            String code = CsvUtils.cell(row, 0);
            String name = CsvUtils.cell(row, 1);
            if (code == null || name == null) {
                result.setSkipped(result.getSkipped() + 1);
                result.addMessage("第 " + lineNo + " 行：课程编号或课程名称为空，已跳过");
                continue;
            }
            Course exist = courseMapper.selectOne(Wrappers.<Course>lambdaQuery().eq(Course::getCourseCode, code));
            Course course = exist == null ? new Course() : exist;
            course.setCourseCode(code);
            course.setCourseName(name);
            course.setCourseType(CsvUtils.cell(row, 2));
            course.setCredit(parseDecimal(CsvUtils.cell(row, 3)));
            course.setTotalHours(parseInt(CsvUtils.cell(row, 4)));
            course.setDepartmentCode(CsvUtils.cell(row, 5));
            course.setDepartmentName(CsvUtils.cell(row, 6));
            course.setOwnerTeacherId(resolveTeacherId(CsvUtils.cell(row, 7)));
            course.setStatus(CsvUtils.cell(row, 8) == null ? "已发布" : CsvUtils.cell(row, 8));
            course.setIntro(CsvUtils.cell(row, 9));
            course.setUpdatedAt(LocalDateTime.now());

            if (exist == null) {
                course.setCreatedAt(LocalDateTime.now());
                courseMapper.insert(course);
                result.setInserted(result.getInserted() + 1);
            } else {
                courseMapper.updateById(course);
                result.setUpdated(result.getUpdated() + 1);
            }
            result.addMessage("第 " + lineNo + " 行：" + code + " " + name
                    + (exist == null ? " 新增成功" : " 更新成功"));
        }
        return result;
    }

    /** 保存或更新课程简介详情 */
    private void saveIntro(Long courseId, String content, String objective,
                           String assessmentMethod, String textbook) {
        if (content == null && objective == null && assessmentMethod == null && textbook == null) {
            return;
        }
        CourseIntro intro = courseIntroMapper.selectOne(
                Wrappers.<CourseIntro>lambdaQuery().eq(CourseIntro::getCourseId, courseId));
        if (intro == null) {
            intro = new CourseIntro();
            intro.setCourseId(courseId);
        }
        if (content != null) {
            intro.setContent(content);
        }
        if (objective != null) {
            intro.setObjective(objective);
        }
        if (assessmentMethod != null) {
            intro.setAssessmentMethod(assessmentMethod);
        }
        if (textbook != null) {
            intro.setTextbook(textbook);
        }
        intro.setUpdatedAt(LocalDateTime.now());
        if (intro.getId() == null) {
            courseIntroMapper.insert(intro);
        } else {
            courseIntroMapper.updateById(intro);
        }
    }

    /** 按教师姓名解析教师 ID；找不到返回 null（导入时允许负责人留空） */
    private Long resolveTeacherId(String teacherName) {
        if (teacherName == null || teacherName.isBlank()) {
            return null;
        }
        List<Teacher> list = teacherMapper.selectList(
                Wrappers.<Teacher>lambdaQuery().eq(Teacher::getName, teacherName.trim()).last("LIMIT 1"));
        return list == null || list.isEmpty() ? null : list.get(0).getId();
    }

    private BigDecimal parseDecimal(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 课程导出表头 */
    public static List<String> exportHeaders() {
        return new ArrayList<>(List.of("课程编号", "课程名称", "课程类型", "学分", "总学时",
                "院系代码", "院系名称", "课程负责人", "状态", "教学班数", "资源数", "课程简介"));
    }
}
