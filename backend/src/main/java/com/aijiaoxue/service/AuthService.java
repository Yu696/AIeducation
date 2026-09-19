package com.aijiaoxue.service;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.RoleCodes;
import com.aijiaoxue.dto.LoginRequest;
import com.aijiaoxue.dto.UserInfo;
import com.aijiaoxue.entity.SysRole;
import com.aijiaoxue.entity.SysUser;
import com.aijiaoxue.entity.Teacher;
import com.aijiaoxue.mapper.SysRoleMapper;
import com.aijiaoxue.mapper.SysUserMapper;
import com.aijiaoxue.mapper.TeacherMapper;
import com.aijiaoxue.security.AuthResult;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.PermissionService;
import com.aijiaoxue.security.TokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证服务（US-01）。
 *
 * <p>负责登录口令校验、令牌签发、当前用户信息组装、注销。
 */
@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final TeacherMapper teacherMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final PermissionService permissionService;

    public AuthService(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper, TeacherMapper teacherMapper,
                       PasswordEncoder passwordEncoder, TokenService tokenService,
                       PermissionService permissionService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.teacherMapper = teacherMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.permissionService = permissionService;
    }

    /**
     * 登录。
     *
     * @throws BadCredentialsException 用户名不存在、口令错误或账号停用（统一提示，避免账号枚举）
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResult login(LoginRequest request) {
        SysUser user = sysUserMapper.selectByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (user.getEnabled() == null || user.getEnabled() != 1) {
            throw new BadCredentialsException("账号已停用，请联系系统管理员");
        }

        String token = tokenService.issue(user);

        // 记录最近登录时间
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(update);
        user.setLastLoginTime(update.getLastLoginTime());

        CurrentUser cu = CurrentUser.from(user, token);
        return new AuthResult(token, buildUserInfo(cu), new ArrayList<>(permissionService.effectivePermissions(user.getRoleCode())));
    }

    /** 组装前端所需的用户上下文（角色名、教师姓名、数据范围、权限码） */
    public UserInfo buildUserInfo(CurrentUser cu) {
        SysUser user = sysUserMapper.selectById(cu.getUserId());
        if (user == null) {
            throw BusinessException.notFound("账号不存在");
        }
        UserInfo info = UserInfo.from(user);
        SysRole role = sysRoleMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getRoleCode, user.getRoleCode()));
        if (role != null) {
            info.setRoleName(role.getRoleName());
            info.setDataScope(role.getDataScope());
            // 主任的可见教研室名称以 sys_role 描述之外的数据为准，这里用教师表/课程表补齐
            if (RoleCodes.DIRECTOR.equals(user.getRoleCode()) && user.getDepartmentCode() != null) {
                info.setDepartmentName(resolveDepartmentName(user.getDepartmentCode()));
            }
        }
        if (user.getTeacherId() != null) {
            Teacher teacher = teacherMapper.selectById(user.getTeacherId());
            if (teacher != null) {
                info.setTeacherName(teacher.getName());
                if (info.getDepartmentCode() == null) {
                    info.setDepartmentCode(teacher.getDepartmentCode());
                }
                if (info.getDepartmentName() == null) {
                    info.setDepartmentName(teacher.getDepartmentName());
                }
            }
        }
        return info;
    }

    /** 当前用户信息 */
    public UserInfo me(CurrentUser cu) {
        return buildUserInfo(cu);
    }

    /** 当前用户的权限码清单 */
    public List<String> permissionsOf(CurrentUser cu) {
        return new ArrayList<>(permissionService.effectivePermissions(cu.getRoleCode()));
    }

    /** 注销 */
    public void logout(CurrentUser cu) {
        if (cu != null) {
            tokenService.revoke(cu.getToken());
        }
    }

    /** 由院系代码反查院系名称 */
    private String resolveDepartmentName(String departmentCode) {
        List<Teacher> teachers = teacherMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Teacher>lambdaQuery()
                        .eq(Teacher::getDepartmentCode, departmentCode).last("LIMIT 1"));
        if (teachers != null && !teachers.isEmpty()) {
            return teachers.get(0).getDepartmentName();
        }
        return departmentCode;
    }
}
