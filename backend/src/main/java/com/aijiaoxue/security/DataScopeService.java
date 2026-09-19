package com.aijiaoxue.security;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.RoleCodes;
import com.aijiaoxue.entity.SysConfig;
import com.aijiaoxue.entity.view.OfferingView;
import com.aijiaoxue.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

/**
 * 数据范围服务：把「角色 + 账号归属」翻译成查询过滤条件，实现数据级权限控制。
 *
 * <p>三重可见范围（对应 sys_role.data_scope）：
 * <ul>
 *   <li>ALL——系统管理员、教学督导：全量数据</li>
 *   <li>DEPARTMENT——教研室主任：本教研室数据；是否可跨院由
 *       {@code sys_config.cross_department} 控制（实验一遗留问题第 3 条落地）</li>
 *   <li>SELF——教师：仅本人课程、教学班、资源、评价</li>
 * </ul>
 */
@Service
public class DataScopeService {

    /** 跨院查询配置键 */
    public static final String CONFIG_CROSS_DEPARTMENT = "cross_department";

    private final SysConfigMapper sysConfigMapper;

    public DataScopeService(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    /** 当前登录用户可用的院系过滤值；null 表示不限制院系 */
    public String deptScope(CurrentUser user) {
        if (user == null) {
            return null;
        }
        if (RoleCodes.DIRECTOR.equals(user.getRoleCode()) && !isCrossDepartmentEnabled()) {
            return user.getDepartmentCode();
        }
        return null;
    }

    /** 当前登录用户可用的教师过滤值；null 表示不限制教师 */
    public Long teacherScope(CurrentUser user) {
        if (user == null) {
            return null;
        }
        if (RoleCodes.TEACHER.equals(user.getRoleCode())) {
            return user.requireTeacherId();
        }
        return null;
    }

    /** 有效的院系过滤：外部显式传入的院系参数被数据范围收窄，防止越权查询他院数据 */
    public String effectiveDept(String requestedDept, CurrentUser user) {
        String scope = deptScope(user);
        if (scope == null) {
            return requestedDept;
        }
        // 主任只能查本教研室；即使请求参数传了别的院系也强制收窄为本院系
        return scope;
    }

    /** 有效的教师过滤：教师角色强制为本人 */
    public Long effectiveTeacher(Long requestedTeacherId, CurrentUser user) {
        Long scope = teacherScope(user);
        if (scope != null) {
            return scope;
        }
        return requestedTeacherId;
    }

    /**
     * 校验当前用户是否可以访问指定教学班的数据（用于详情类接口）。
     *
     * @throws BusinessException 越权时抛出 403
     */
    public void assertOfferingAccessible(OfferingView offering, CurrentUser user) {
        if (offering == null) {
            throw BusinessException.notFound("教学班不存在");
        }
        if (RoleCodes.ADMIN.equals(user.getRoleCode()) || RoleCodes.SUPERVISOR.equals(user.getRoleCode())
                || RoleCodes.COURSE_ADMIN.equals(user.getRoleCode())) {
            return;
        }
        if (RoleCodes.DIRECTOR.equals(user.getRoleCode())) {
            String scope = deptScope(user);
            if (scope == null || scope.equals(offering.getDepartmentCode())) {
                return;
            }
            throw BusinessException.forbidden("无权访问其他教研室的教学班数据");
        }
        if (RoleCodes.TEACHER.equals(user.getRoleCode())) {
            if (user.getTeacherId() != null && user.getTeacherId().equals(offering.getTeacherId())) {
                return;
            }
            throw BusinessException.forbidden("教师只能访问本人所授教学班的数据");
        }
        throw BusinessException.forbidden("当前角色无权访问教学班数据");
    }

    /** 校验教师是否在当前用户可见范围内 */
    public void assertTeacherAccessible(Long teacherId, CurrentUser user) {
        if (RoleCodes.TEACHER.equals(user.getRoleCode()) && !user.requireTeacherId().equals(teacherId)) {
            throw BusinessException.forbidden("教师只能访问本人档案数据");
        }
    }

    /** 读取布尔型系统配置 */
    public boolean isCrossDepartmentEnabled() {
        SysConfig cfg = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, CONFIG_CROSS_DEPARTMENT));
        return cfg != null && "true".equalsIgnoreCase(cfg.getConfigValue());
    }

    /** 读取字符串型系统配置 */
    public String getConfig(String key, String defaultValue) {
        SysConfig cfg = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key));
        return cfg == null || cfg.getConfigValue() == null ? defaultValue : cfg.getConfigValue();
    }
}
