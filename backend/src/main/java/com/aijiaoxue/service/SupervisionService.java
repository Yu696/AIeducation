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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 听评课（督导）服务（US-04）。
 *
 * <p>故事原文：「作为教学督导，我想查看课程开设与督导覆盖率，以便安排听评课」——
 * 因此本服务同时提供「开课清单 → 安排听评课 → 完成记录 → 覆盖率统计」闭环。
 */
@Service
public class SupervisionService {

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
     * <p>状态联动：填写 finishDate 或状态置为「已完成」时自动补齐完成日期，
     * 使其计入督导覆盖率分子（口径见 OfferingService#coverage）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Supervision create(Supervision supervision) {
        validate(supervision);
        supervision.setId(null);
        if (supervision.getStatus() == null || supervision.getStatus().isBlank()) {
            supervision.setStatus(supervision.getFinishDate() == null ? "已安排" : "已完成");
        }
        if ("已完成".equals(supervision.getStatus()) && supervision.getFinishDate() == null) {
            supervision.setFinishDate(LocalDate.now());
        }
        if (supervision.getSemester() == null || supervision.getSemester().isBlank()) {
            Offering offering = offeringMapper.selectById(supervision.getOfferingId());
            supervision.setSemester(offering == null ? null : offering.getSemester());
        }
        supervision.setCreatedAt(LocalDateTime.now());
        supervision.setUpdatedAt(LocalDateTime.now());
        supervisionMapper.insert(supervision);
        return supervision;
    }

    /** 更新听评课结果 */
    @Transactional(rollbackFor = Exception.class)
    public Supervision update(Long id, Supervision supervision) {
        Supervision exist = supervisionMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("听评课记录不存在");
        }
        if (supervision.getOfferingId() != null) {
            validate(supervision);
        }
        if ("已完成".equals(supervision.getStatus())
                && supervision.getFinishDate() == null && exist.getFinishDate() == null) {
            supervision.setFinishDate(LocalDate.now());
        }
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

    /** 听评课状态候选值 */
    public List<String> statuses() {
        return List.of("待安排", "已安排", "已完成");
    }

    /** 校验听评课必填项与引用完整性 */
    private void validate(Supervision supervision) {
        if (supervision.getOfferingId() == null) {
            throw new BusinessException("请选择要听课的教学班");
        }
        if (offeringMapper.selectById(supervision.getOfferingId()) == null) {
            throw BusinessException.notFound("教学班不存在");
        }
        if (supervision.getSupervisorId() == null) {
            throw new BusinessException("请选择督导教师");
        }
        Teacher supervisor = teacherMapper.selectById(supervision.getSupervisorId());
        if (supervisor == null) {
            throw BusinessException.notFound("督导教师不存在");
        }
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
                .eq(Supervision::getStatus, "已完成"));
        return count == null ? 0 : count;
    }
}
