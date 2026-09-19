package com.aijiaoxue.security;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.entity.SysRolePermission;
import com.aijiaoxue.mapper.SysRolePermissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限服务：解析角色有效权限、维护角色权限覆写。
 *
 * <p>取值优先级：{@code sys_role_permission} 表覆写 &gt; {@link PermissionCodes} 默认矩阵。
 * 采用该设计的原因：Sprint 1 交付「角色—功能—数据权限矩阵」既要有可审阅、可测试的默认值，
 * 又要支持管理员在界面上调整（US-01「配置角色与权限」）。
 */
@Service
public class PermissionService {

    private final SysRolePermissionMapper rolePermissionMapper;

    /** 角色 -> 有效权限 的进程内缓存；权限变更时精确失效 */
    private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();

    public PermissionService(SysRolePermissionMapper rolePermissionMapper) {
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /**
     * 计算角色的有效权限集合。
     * 若表中存在该角色的任何记录，则以表中记录为准（视为已人工覆写）。
     */
    public Set<String> effectivePermissions(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Set.of();
        }
        return cache.computeIfAbsent(roleCode, rc -> {
            List<SysRolePermission> overrides = rolePermissionMapper.selectList(
                    Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleCode, rc));
            if (overrides == null || overrides.isEmpty()) {
                return new LinkedHashSet<>(PermissionCodes.defaultsOf(rc));
            }
            Set<String> perms = new LinkedHashSet<>();
            for (SysRolePermission rp : overrides) {
                if (PermissionCodes.isValid(rp.getPermCode())) {
                    perms.add(rp.getPermCode());
                }
            }
            return perms;
        });
    }

    /** 角色是否拥有某权限码 */
    public boolean hasPermission(String roleCode, String permCode) {
        return effectivePermissions(roleCode).contains(permCode);
    }

    /** 查询角色的权限覆写记录（不含默认值），用于权限矩阵页回显 */
    public Set<String> overriddenPermissions(String roleCode) {
        List<SysRolePermission> overrides = rolePermissionMapper.selectList(
                Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleCode, roleCode));
        Set<String> perms = new LinkedHashSet<>();
        if (overrides != null) {
            overrides.forEach(rp -> perms.add(rp.getPermCode()));
        }
        return perms;
    }

    /**
     * 保存角色权限（整体覆盖式写入）。
     *
     * @param roleCode    角色码
     * @param permissions 目标权限码集合；空集合表示该角色无任何权限
     * @throws BusinessException 出现未登记的权限码时拒绝写入，避免脏数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRolePermissions(String roleCode, List<String> permissions) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BusinessException("角色码不能为空");
        }
        if (PermissionCodes.defaultsOf(roleCode).isEmpty()) {
            throw new BusinessException("角色不存在：" + roleCode);
        }
        if (permissions != null) {
            for (String p : permissions) {
                if (!PermissionCodes.isValid(p)) {
                    throw new BusinessException("未登记的权限码：" + p);
                }
            }
        }
        rolePermissionMapper.deleteByRoleCode(roleCode);
        if (permissions != null) {
            for (String p : new LinkedHashSet<>(permissions)) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleCode(roleCode);
                rp.setPermCode(p);
                rolePermissionMapper.insert(rp);
            }
        }
        evictCacheWithTransaction(roleCode);
    }

    /** 重置为代码内默认矩阵 */
    @Transactional(rollbackFor = Exception.class)
    public void resetRolePermissions(String roleCode) {
        rolePermissionMapper.deleteByRoleCode(roleCode);
        evictCacheWithTransaction(roleCode);
    }

    /**
     * 失效角色权限缓存，并把失效动作绑定到事务生命周期。
     *
     * <p>必要性：缓存是进程级的，而权限写入是事务性的。若只在写入时清缓存，
     * 一旦事务回滚（或测试用例回滚），数据库已恢复旧权限、缓存却停在事务内的新权限，
     * 后续所有鉴权判断都会基于错误数据 —— 真实项目中表现为"改正权限后仍需重启服务"。
     * 因此提交后再清一次、回滚后也清一次，保证缓存始终与已提交数据一致。
     */
    private void evictCacheWithTransaction(String roleCode) {
        cache.remove(roleCode);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    cache.remove(roleCode);
                }
            });
        }
    }

    /** 供测试与配置变更后清缓存 */
    public void clearCache() {
        cache.clear();
    }

    /** 全部权限码分组（前端权限矩阵渲染用） */
    public Map<String, String> allPermissionNames() {
        return PermissionCodes.allWithName();
    }

    /** 读取角色默认矩阵（前端可展示「恢复默认」提示） */
    public Set<String> defaultPermissions(String roleCode) {
        return PermissionCodes.defaultsOf(roleCode);
    }

    /** 角色码集合，便于校验 */
    public List<String> knownRoles() {
        return Arrays.asList("ADMIN", "DIRECTOR", "TEACHER", "SUPERVISOR", "COURSE_ADMIN");
    }
}
