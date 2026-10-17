package com.aijiaoxue.service;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.CourseResource;
import com.aijiaoxue.entity.Offering;
import com.aijiaoxue.entity.TeachingResource;
import com.aijiaoxue.entity.view.ResourceView;
import com.aijiaoxue.mapper.CourseResourceMapper;
import com.aijiaoxue.mapper.OfferingMapper;
import com.aijiaoxue.mapper.ResourceMapper;
import com.aijiaoxue.mapper.TeacherMapper;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.DataScopeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教学资源服务（史诗二，US-03）。
 *
 * <p>故事原文：「作为教师，我想查看学生人次与课程资源，以便备课」——
 * 因此除常规资源管理外，专门提供 {@link #prepView(CurrentUser)} 备课视图：
 * 按「我的课程 → 该课程可用资源」聚合，且只返回有效资源。
 */
@Service
public class ResourceService {

    private final ResourceMapper resourceMapper;
    private final CourseResourceMapper courseResourceMapper;
    private final CourseService courseService;
    private final OfferingMapper offeringMapper;
    private final TeacherMapper teacherMapper;
    private final DataScopeService dataScopeService;

    public ResourceService(ResourceMapper resourceMapper, CourseResourceMapper courseResourceMapper,
                           CourseService courseService, OfferingMapper offeringMapper,
                           TeacherMapper teacherMapper, DataScopeService dataScopeService) {
        this.resourceMapper = resourceMapper;
        this.courseResourceMapper = courseResourceMapper;
        this.courseService = courseService;
        this.offeringMapper = offeringMapper;
        this.teacherMapper = teacherMapper;
        this.dataScopeService = dataScopeService;
    }

    /** 资源列表分页（教师账号自动收窄为本人相关资源） */
    public PageResult<ResourceView> page(long page, long size, String keyword, String resourceType,
                                         String status, Long offeringId, CurrentUser user) {
        Page<ResourceView> p = new Page<>(page, size);
        Long effTeacher = dataScopeService.teacherScope(user);
        String effDept = dataScopeService.effectiveDept(null, user);
        IPage<ResourceView> result = resourceMapper.selectResourcePage(p, keyword, resourceType, status,
                offeringId, effTeacher, effDept);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 导出用资源列表 */
    public List<ResourceView> listForExport(String keyword, String resourceType, String status,
                                            Long offeringId, CurrentUser user) {
        Long effTeacher = dataScopeService.teacherScope(user);
        String effDept = dataScopeService.effectiveDept(null, user);
        return resourceMapper.selectResourceList(keyword, resourceType, status, offeringId, effTeacher, effDept);
    }

    /**
     * 资源详情（US-07）。
     *
     * <p>TEACHER 仅能查看本人上传的资源，或本人任课/合上教学班下的资源；
     * 修改与删除另按「仅本人上传」收紧，见 {@link #update} / {@link #delete}。
     */
    public ResourceView detail(Long id, CurrentUser user) {
        ResourceView view = resourceMapper.selectResourceDetail(id);
        if (view == null) {
            throw BusinessException.notFound("教学资源不存在");
        }
        Long selfTeacherId = dataScopeService.teacherScope(user);
        if (selfTeacherId != null && resourceMapper.countVisibleToTeacher(id, selfTeacherId) == 0) {
            throw BusinessException.forbidden("教师只能查看本人可见范围内的教学资源");
        }
        return view;
    }

    /**
     * 备课查看：按「我的课程/教学班 → 可用资源」分组聚合。
     *
     * <p>返回结构：{@code {groups:[{courseId,courseCode,courseName,semester,resourceCount,resources:[...]}], totalResources}}
     */
    public Map<String, Object> prepView(CurrentUser user) {
        Long teacherId = user.requireTeacherId();
        List<ResourceView> resources = resourceMapper.selectPrepResources(teacherId);

        // 用 LinkedHashMap 保持顺序，key 为「学期 + 课程」组合，避免不同学期的同名课程被合并
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        for (ResourceView r : resources) {
            String semester = r.getSemester() == null ? "未指定学期" : r.getSemester();
            String courseName = r.getCourseName() == null ? "未关联课程" : r.getCourseName();
            String key = semester + "|" + (r.getCourseId() == null ? "_" : r.getCourseId());
            Map<String, Object> group = groups.computeIfAbsent(key, k -> {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("courseId", r.getCourseId());
                g.put("courseCode", r.getCourseCode());
                g.put("courseName", courseName);
                g.put("semester", semester);
                g.put("resources", new ArrayList<ResourceView>());
                return g;
            });
            @SuppressWarnings("unchecked")
            List<ResourceView> list = (List<ResourceView>) group.get("resources");
            list.add(r);
        }
        groups.values().forEach(g -> {
            @SuppressWarnings("unchecked")
            List<ResourceView> list = (List<ResourceView>) g.get("resources");
            g.put("resourceCount", list.size());
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groups", new ArrayList<>(groups.values()));
        data.put("totalResources", resources.size());
        data.put("groupCount", groups.size());
        return data;
    }

    /** 某课程已挂接的资源（复用课程数据范围校验） */
    public List<ResourceView> byCourse(Long courseId, CurrentUser user) {
        return courseService.resourcesOf(courseId, user);
    }

    /**
     * 新建资源。
     *
     * <p>TEACHER 的 {@code teacherId} 一律强制为本人，忽略请求体，防止伪造归属。
     */
    @Transactional(rollbackFor = Exception.class)
    public TeachingResource create(TeachingResource resource, CurrentUser user) {
        // 先强制归属，再校验：教师请求体中的 teacherId 一律不生效
        if (user.isTeacher()) {
            resource.setTeacherId(user.requireTeacherId());
        }
        validate(resource);
        resource.setId(null);
        if (resource.getStatus() == null || resource.getStatus().isBlank()) {
            resource.setStatus("有效");
        }
        resource.setCreatedAt(LocalDateTime.now());
        resource.setUpdatedAt(LocalDateTime.now());
        resourceMapper.insert(resource);
        return resource;
    }

    /**
     * 修改资源。
     *
     * <p>TEACHER 仅能修改本人上传的资源（owner-only），并冻结 {@code teacherId}，
     * 禁止通过请求体把资源转移给他人。
     */
    @Transactional(rollbackFor = Exception.class)
    public TeachingResource update(Long id, TeachingResource resource, CurrentUser user) {
        TeachingResource exist = resourceMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("教学资源不存在");
        }
        if (user.isTeacher()) {
            if (!user.requireTeacherId().equals(exist.getTeacherId())) {
                throw BusinessException.forbidden("教师只能修改本人上传的教学资源");
            }
            resource.setTeacherId(exist.getTeacherId());
        }
        resource.setId(id);
        resource.setUpdatedAt(LocalDateTime.now());
        resourceMapper.updateById(resource);
        return resourceMapper.selectById(id);
    }

    /**
     * 删除资源，同时清理课程挂接关系。
     *
     * <p>TEACHER 仅能删除本人上传的资源（owner-only）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, CurrentUser user) {
        TeachingResource exist = resourceMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("教学资源不存在");
        }
        if (user.isTeacher() && !user.requireTeacherId().equals(exist.getTeacherId())) {
            throw BusinessException.forbidden("教师只能删除本人上传的教学资源");
        }
        courseResourceMapper.delete(Wrappers.<CourseResource>lambdaQuery()
                .eq(CourseResource::getResourceId, id));
        resourceMapper.deleteById(id);
    }

    /** 资源类型候选值 */
    public List<String> resourceTypes() {
        return List.of("课件", "教案", "视频", "习题", "参考文献");
    }

    /** 校验资源必填项与关联对象存在性 */
    private void validate(TeachingResource resource) {
        if (resource.getResourceName() == null || resource.getResourceName().isBlank()) {
            throw new BusinessException("资源名称不能为空");
        }
        if (resource.getResourceType() == null || resource.getResourceType().isBlank()) {
            throw new BusinessException("资源类型不能为空");
        }
        if (resource.getOfferingId() != null) {
            Offering offering = offeringMapper.selectById(resource.getOfferingId());
            if (offering == null) {
                throw BusinessException.notFound("关联的教学班不存在");
            }
        }
        if (resource.getTeacherId() != null
                && teacherMapper.selectById(resource.getTeacherId()) == null) {
            throw BusinessException.notFound("归属教师不存在");
        }
    }
}
