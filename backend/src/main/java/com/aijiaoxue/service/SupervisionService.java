package com.aijiaoxue.service;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.Offering;
import com.aijiaoxue.entity.Supervision;
import com.aijiaoxue.entity.Teacher;
import com.aijiaoxue.entity.view.OfferingView;
import com.aijiaoxue.entity.view.SupervisionView;
import com.aijiaoxue.mapper.OfferingMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 听评课（督导）服务（US-04 / Sprint 2 US-10）。
 *
 * <p>故事原文：「作为教学督导，我想查看课程开设与督导覆盖率，以便安排听评课」——
 * 因此本服务同时提供「开课清单 → 安排听评课 → 完成记录 → 覆盖率统计」闭环。
 *
 * <p>Sprint 2 增补：显式状态机「待安排 → 已安排 → 已完成」，非法流转拒绝；
 * 已安排必须填写计划日期，已完成自动补齐完成日期；只有已完成状态可填写评分；
 * 并为前端提供督导候选教师接口，避免教学督导账号因无 user:read 权限无法选择督导。
 */
@Service
public class SupervisionService {

    public static final String STATUS_PENDING = "待安排";
    public static final String STATUS_ARRANGED = "已安排";
    public static final String STATUS_FINISHED = "已完成";

    private static final List<String> STATUSES = List.of(STATUS_PENDING, STATUS_ARRANGED, STATUS_FINISHED);

    /** 允许的状态流转：只能向前，已完成是终态 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            STATUS_PENDING, Set.of(STATUS_PENDING, STATUS_ARRANGED),
            STATUS_ARRANGED, Set.of(STATUS_ARRANGED, STATUS_FINISHED),
            STATUS_FINISHED, Set.of(STATUS_FINISHED)
    );

    private final SupervisionMapper supervisionMapper;
    private final OfferingMapper offeringMapper;
    private final TeacherMapper teacherMapper;
    private final DataScopeService dataScopeService;

    public SupervisionService(SupervisionMapper supervisionMapper, OfferingMapper offeringMapper,
                              TeacherMapper teacherMapper, DataScopeService dataScopeService) {
        this.supervisionMapper = supervisionMapper;
        this.offeringMapper = offeringMapper;
        this.teacherMapper = teacherMapper;
        this.dataScopeService = dataScopeService;
    }

    /** 听评课列表分页 */
    public PageResult<SupervisionView> page(long page, long size, String keyword, Long offeringId,
                                            Long supervisorId, String semester, String status,
                                            CurrentUser user) {
        Page<SupervisionView> p = new Page<>(page, size);
        String effDept = dataScopeService.effectiveDept(null, user);
        IPage<SupervisionView> result = supervisionMapper.selectSupervisionPage(p, keyword, offeringId,
                supervisorId, semester, status, effDept);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 导出用听评课列表 */
    public List<SupervisionView> listForExport(String keyword, Long offeringId, Long supervisorId,
                                               String semester, String status, CurrentUser user) {
        String effDept = dataScopeService.effectiveDept(null, user);
        return supervisionMapper.selectSupervisionList(keyword, offeringId, supervisorId, semester, status, effDept);
    }

    /** 听评课详情 */
    public SupervisionView detail(Long id) {
        SupervisionView view = supervisionMapper.selectSupervisionDetail(id);
        if (view == null) {
            throw BusinessException.notFound("听评课记录不存在");
        }
        return view;
    }

    /**
     * 安排／登记听评课。
     *
     * <p>未显式给状态时按日期推导：有完成日期→已完成，有计划日期→已安排，否则→待安排。
     */
    @Transactional(rollbackFor = Exception.class)
    public Supervision create(Supervision supervision) {
        normalize(supervision, null);
        supervision.setId(null);
        LocalDateTime now = LocalDateTime.now();
        supervision.setCreatedAt(now);
        supervision.setUpdatedAt(now);
        supervisionMapper.insert(supervision);
        return supervision;
    }

    /**
     * 更新听评课记录／推进状态。
     *
     * <p>只允许 待安排→已安排→已完成 单向流转；已完成记录只能修改评分与评语。
     */
    @Transactional(rollbackFor = Exception.class)
    public Supervision update(Long id, Supervision supervision) {
        Supervision exist = supervisionMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("听评课记录不存在");
        }
        mergeExisting(supervision, exist);
        normalize(supervision, exist);
        supervision.setId(id);
        supervision.setUpdatedAt(LocalDateTime.now());
        supervisionMapper.updateById(supervision);
        return supervisionMapper.selectById(id);
    }

    /** 删除听评课记录 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (supervisionMapper.selectById(id) == null) {
            throw BusinessException.notFound("听评课记录不存在");
        }
        supervisionMapper.deleteById(id);
    }

    /** 待安排听评课的教学班清单（督导工作台/覆盖率页联动） */
    public List<OfferingView> pendingOfferings(String semester, String deptCode, CurrentUser user) {
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        return offeringMapper.selectOfferingList(null, semester, null, effDept, null, 0, 1);
    }

    /** 待安排数量 */
    public long pendingCount(String deptCode, CurrentUser user) {
        String effDept = dataScopeService.effectiveDept(deptCode, user);
        return supervisionMapper.countPendingOfferings(effDept);
    }

    /**
     * 督导候选教师。
     *
     * <p>教学督导账号默认没有 user:read 权限，不能调用用户管理接口；
     * 本接口仅需 supervision:read，返回教师档案供安排听评课时选择。
     */
    public List<Map<String, Object>> supervisorCandidates(String keyword) {
        List<Teacher> teachers = teacherMapper.selectList(Wrappers.<Teacher>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), Teacher::getName, keyword)
                .orderByAsc(Teacher::getDepartmentCode)
                .orderByAsc(Teacher::getName));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Teacher t : teachers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getId());
            item.put("name", t.getName());
            item.put("departmentCode", t.getDepartmentCode());
            item.put("departmentName", t.getDepartmentName());
            result.add(item);
        }
        return result;
    }

    /** 听评课状态候选值 */
    public List<String> statuses() {
        return STATUSES;
    }

    /** 某教学班的听评课记录 */
    public List<SupervisionView> byOffering(Long offeringId) {
        if (offeringMapper.selectById(offeringId) == null) {
            throw BusinessException.notFound("教学班不存在");
        }
        return supervisionMapper.selectSupervisionList(null, offeringId, null, null, null, null);
    }

    /** 统计某督导已完成的听评课次数 */
    public long finishedCountOfSupervisor(Long supervisorId) {
        Long count = supervisionMapper.selectCount(Wrappers.<Supervision>lambdaQuery()
                .eq(Supervision::getSupervisorId, supervisorId)
                .eq(Supervision::getStatus, STATUS_FINISHED));
        return count == null ? 0 : count;
    }

    /**
     * 更新场景下合并未提交字段，避免只改状态时把教学班、督导、日期清空。
     */
    private void mergeExisting(Supervision target, Supervision exist) {
        if (target.getOfferingId() == null) {
            target.setOfferingId(exist.getOfferingId());
        }
        if (target.getSupervisorId() == null) {
            target.setSupervisorId(exist.getSupervisorId());
        }
        if (target.getSemester() == null || target.getSemester().isBlank()) {
            target.setSemester(exist.getSemester());
        }
        if (target.getPlanDate() == null) {
            target.setPlanDate(exist.getPlanDate());
        }
        if (target.getFinishDate() == null) {
            target.setFinishDate(exist.getFinishDate());
        }
        if (target.getStatus() == null || target.getStatus().isBlank()) {
            target.setStatus(exist.getStatus());
        }
        if (target.getScore() == null) {
            target.setScore(exist.getScore());
        }
        if (target.getComment() == null) {
            target.setComment(exist.getComment());
        }
    }

    /**
     * 统一校验：引用完整性、状态合法性、状态流转、日期与评分规则。
     */
    private void normalize(Supervision s, Supervision existing) {
        if (s.getOfferingId() == null) {
            throw new BusinessException("请选择要听课的教学班");
        }
        Offering offering = offeringMapper.selectById(s.getOfferingId());
        if (offering == null) {
            throw BusinessException.notFound("教学班不存在");
        }
        if (s.getSupervisorId() == null) {
            throw new BusinessException("请选择督导教师");
        }
        Teacher supervisor = teacherMapper.selectById(s.getSupervisorId());
        if (supervisor == null) {
            throw BusinessException.notFound("督导教师不存在");
        }

        // 学期始终以教学班为准，避免同一记录出现两个学期口径
        if (offering.getSemester() != null) {
            s.setSemester(offering.getSemester());
        }
        if (s.getSemester() == null || s.getSemester().isBlank()) {
            throw new BusinessException("学期不能为空");
        }

        String status = s.getStatus();
        if (status == null || status.isBlank()) {
            status = deriveStatus(s);
        } else {
            status = status.trim();
        }
        s.setStatus(status);
        if (!STATUSES.contains(status)) {
            throw new BusinessException("听评课状态不合法：" + status);
        }

        if (existing != null && existing.getStatus() != null && !existing.getStatus().equals(status)) {
            Set<String> allowed = TRANSITIONS.getOrDefault(existing.getStatus(), Set.of());
            if (!allowed.contains(status)) {
                throw new BusinessException("不允许从「" + existing.getStatus() + "」流转到「" + status + "」");
            }
        }

        switch (status) {
            case STATUS_PENDING:
                if (s.getPlanDate() != null) {
                    throw new BusinessException("待安排状态不应填写计划听课日期");
                }
                if (s.getFinishDate() != null) {
                    throw new BusinessException("待安排状态不应填写完成日期");
                }
                break;
            case STATUS_ARRANGED:
                if (s.getPlanDate() == null) {
                    throw new BusinessException("已安排状态必须填写计划听课日期");
                }
                if (s.getFinishDate() != null) {
                    throw new BusinessException("已安排状态不应填写完成日期");
                }
                break;
            case STATUS_FINISHED:
                if (s.getFinishDate() == null) {
                    s.setFinishDate(LocalDate.now());
                }
                if (s.getPlanDate() == null) {
                    s.setPlanDate(s.getFinishDate());
                }
                if (s.getFinishDate().isBefore(s.getPlanDate())) {
                    throw new BusinessException("完成日期不能早于计划听课日期");
                }
                break;
            default:
                break;
        }

        if (s.getScore() != null) {
            if (!STATUS_FINISHED.equals(status)) {
                throw new BusinessException("只有已完成状态可以填写听课评分");
            }
            if (s.getScore().compareTo(BigDecimal.ZERO) < 0
                    || s.getScore().compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException("听课评分需在 0—100 之间");
            }
        }
        if (s.getComment() != null && s.getComment().length() > 500) {
            throw new BusinessException("听课评语不能超过500字");
        }
    }

    /** 未显式给状态时，按日期推导状态 */
    private String deriveStatus(Supervision s) {
        if (s.getFinishDate() != null) {
            return STATUS_FINISHED;
        }
        if (s.getPlanDate() != null) {
            return STATUS_ARRANGED;
        }
        return STATUS_PENDING;
    }
}
