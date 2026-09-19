package com.aijiaoxue.service;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.common.RoleCodes;
import com.aijiaoxue.dto.UserSaveRequest;
import com.aijiaoxue.entity.SysAuditLog;
import com.aijiaoxue.entity.SysRole;
import com.aijiaoxue.entity.SysUser;
import com.aijiaoxue.entity.Teacher;
import com.aijiaoxue.mapper.SysAuditLogMapper;
import com.aijiaoxue.mapper.SysRoleMapper;
import com.aijiaoxue.mapper.SysUserMapper;
import com.aijiaoxue.mapper.TeacherMapper;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.TokenService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户与权限管理服务（US-01）。
 *
 * <p>US-01 验收标准①：存在管理员、教研室主任、教师、教学督导四类角色（另含课程管理员）。
 * 本服务负责账号维护、启停用、重置口令，并保证关键变更后令牌立即失效。
 */
@Service
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final TeacherMapper teacherMapper;
    private final SysAuditLogMapper auditLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public UserService(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper, TeacherMapper teacherMapper,
                       SysAuditLogMapper auditLogMapper, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.teacherMapper = teacherMapper;
        this.auditLogMapper = auditLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** 用户列表分页 */
    public PageResult<Map<String, Object>> page(long page, long size, String keyword, String roleCode, Integer enabled) {
        Page<SysUser> p = new Page<>(page, size);
        IPage<SysUser> result = sysUserMapper.selectPage(p, Wrappers.<SysUser>lambdaQuery()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword))
                .eq(roleCode != null && !roleCode.isBlank(), SysUser::getRoleCode, roleCode)
                .eq(enabled != null, SysUser::getEnabled, enabled)
                .orderByAsc(SysUser::getId));

        // 组装角色中文名与绑定教师姓名，避免前端二次查询
        List<Map<String, Object>> records = result.getRecords().stream().map(this::toView).toList();
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /** 新建用户 */
    @Transactional(rollbackFor = Exception.class)
    public SysUser create(UserSaveRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException("新建用户必须设置初始密码");
        }
        if (sysUserMapper.selectByUsername(request.getUsername()) != null) {
            throw BusinessException.conflict("登录名已存在：" + request.getUsername());
        }
        checkRole(request.getRoleCode());
        checkTeacherBinding(request);
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setRoleCode(request.getRoleCode());
        user.setTeacherId(request.getTeacherId());
        user.setDepartmentCode(resolveDepartment(request));
        user.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);
        return user;
    }

    /** 修改用户；角色或启停用变更时，强制该用户已签发令牌失效 */
    @Transactional(rollbackFor = Exception.class)
    public SysUser update(Long id, UserSaveRequest request) {
        SysUser exist = sysUserMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("用户不存在");
        }
        SysUser sameName = sysUserMapper.selectByUsername(request.getUsername());
        if (sameName != null && !sameName.getId().equals(id)) {
            throw BusinessException.conflict("登录名已存在：" + request.getUsername());
        }
        checkRole(request.getRoleCode());
        checkTeacherBinding(request);

        SysUser update = new SysUser();
        update.setId(id);
        update.setUsername(request.getUsername());
        update.setRealName(request.getRealName());
        update.setRoleCode(request.getRoleCode());
        update.setTeacherId(request.getTeacherId());
        update.setDepartmentCode(resolveDepartment(request));
        update.setEnabled(request.getEnabled());
        update.setUpdatedAt(LocalDateTime.now());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            update.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        sysUserMapper.updateById(update);

        boolean roleChanged = !java.util.Objects.equals(exist.getRoleCode(), request.getRoleCode());
        boolean disabled = request.getEnabled() != null && request.getEnabled() == 0;
        if (roleChanged || disabled) {
            tokenService.revokeAllOfUser(id);
        }
        return sysUserMapper.selectById(id);
    }

    /** 重置口令，同时使旧令牌失效 */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String rawPassword) {
        SysUser exist = sysUserMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException("新密码不能为空");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(rawPassword));
        update.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(update);
        tokenService.revokeAllOfUser(id);
    }

    /** 删除用户；禁止删除自己，避免误操作锁死系统 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, CurrentUser current) {
        SysUser exist = sysUserMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (current != null && current.getUserId().equals(id)) {
            throw new BusinessException("不能删除当前登录账号");
        }
        if (RoleCodes.ADMIN.equals(exist.getRoleCode())) {
            Long adminCount = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getRoleCode, RoleCodes.ADMIN));
            if (adminCount != null && adminCount <= 1) {
                throw new BusinessException("系统至少保留一个管理员账号");
            }
        }
        tokenService.revokeAllOfUser(id);
        sysUserMapper.deleteById(id);
    }

    /** 角色清单（含账号数） */
    public List<Map<String, Object>> roles() {
        return sysRoleMapper.selectRoleWithUserCount();
    }

    /** 角色分布统计（管理员工作台图表） */
    public List<Map<String, Object>> roleDistribution() {
        return sysUserMapper.selectRoleDistribution();
    }

    /** 操作日志分页 */
    public PageResult<SysAuditLog> auditLogs(long page, long size, String operator, String action, String targetType) {
        Page<SysAuditLog> p = new Page<>(page, size);
        IPage<SysAuditLog> result = auditLogMapper.selectPage(p, Wrappers.<SysAuditLog>lambdaQuery()
                .like(operator != null && !operator.isBlank(), SysAuditLog::getOperator, operator)
                .eq(action != null && !action.isBlank(), SysAuditLog::getAction, action)
                .eq(targetType != null && !targetType.isBlank(), SysAuditLog::getTargetType, targetType)
                .orderByDesc(SysAuditLog::getId));
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** 账号总数 */
    public long userCount() {
        Long c = sysUserMapper.selectCount(null);
        return c == null ? 0 : c;
    }

    /** 启用账号数 */
    public long enabledUserCount() {
        Long c = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEnabled, 1));
        return c == null ? 0 : c;
    }

    /** 实体转展示对象 */
    private Map<String, Object> toView(SysUser user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("realName", user.getRealName());
        m.put("roleCode", user.getRoleCode());
        m.put("teacherId", user.getTeacherId());
        m.put("departmentCode", user.getDepartmentCode());
        m.put("enabled", user.getEnabled());
        m.put("lastLoginTime", user.getLastLoginTime());
        m.put("createdAt", user.getCreatedAt());
        SysRole role = sysRoleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, user.getRoleCode()));
        m.put("roleName", role == null ? user.getRoleCode() : role.getRoleName());
        m.put("dataScope", role == null ? null : role.getDataScope());
        if (user.getTeacherId() != null) {
            Teacher t = teacherMapper.selectById(user.getTeacherId());
            m.put("teacherName", t == null ? null : t.getName());
        }
        return m;
    }

    /** 角色码必须是已登记角色 */
    private void checkRole(String roleCode) {
        if (roleCode == null || sysRoleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode)) == 0) {
            throw new BusinessException("角色不存在：" + roleCode);
        }
    }

    /** 教师角色必须绑定教师档案，否则 SELF 数据范围无法生效 */
    private void checkTeacherBinding(UserSaveRequest request) {
        if (RoleCodes.TEACHER.equals(request.getRoleCode())) {
            if (request.getTeacherId() == null) {
                throw new BusinessException("教师账号必须绑定教师档案");
            }
            if (teacherMapper.selectById(request.getTeacherId()) == null) {
                throw BusinessException.notFound("绑定的教师档案不存在");
            }
        }
    }

    /** 未显式指定院系时，教师账号按其教师档案所属院系补齐 */
    private String resolveDepartment(UserSaveRequest request) {
        if (request.getDepartmentCode() != null && !request.getDepartmentCode().isBlank()) {
            return request.getDepartmentCode();
        }
        if (request.getTeacherId() != null) {
            Teacher t = teacherMapper.selectById(request.getTeacherId());
            if (t != null) {
                return t.getDepartmentCode();
            }
        }
        return null;
    }
}
