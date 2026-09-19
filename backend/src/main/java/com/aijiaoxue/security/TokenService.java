package com.aijiaoxue.security;

import com.aijiaoxue.common.BusinessException;
import com.aijiaoxue.common.RoleCodes;
import com.aijiaoxue.entity.SysUser;
import com.aijiaoxue.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌服务。
 *
 * <p>Sprint 1 采用服务端内存令牌（登录后返回 token，重启即失效）。
 * 该设计取舍已记录：教学模拟 Sprint 内无需 JWT 无状态能力，
 * 内存令牌实现更简单、可测试，且天然支持「登出立即失效」。
 * Sprint 2 若需横向扩展再替换为 JWT，接口不变。
 */
@Service
public class TokenService {

    /** token -> 会话信息 */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private final SysUserMapper sysUserMapper;

    /** 令牌有效期（分钟），默认 12 小时 */
    @Value("${aijiaoxue.token-expire-minutes:720}")
    private long expireMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    public TokenService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 为登录成功的用户签发令牌。
     *
     * @param user 已通过口令校验的用户
     * @return 令牌字符串
     */
    public String issue(SysUser user) {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        sessions.put(token, new Session(user.getId(), LocalDateTime.now().plusMinutes(expireMinutes)));
        return token;
    }

    /**
     * 校验令牌并返回最新用户信息。
     *
     * @return 用户；令牌不存在、过期或账号被停用时返回 null
     */
    public SysUser resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expireAt.isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            return null;
        }
        SysUser user = sysUserMapper.selectById(session.userId);
        // 账号被停用时令牌立即失效，避免停用后仍可访问（US-01 验收标准②）
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
            sessions.remove(token);
            return null;
        }
        return user;
    }

    /** 注销令牌 */
    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** 强制某用户全部令牌失效（修改角色或停用账号时调用） */
    public void revokeAllOfUser(Long userId) {
        sessions.entrySet().removeIf(e -> e.getValue().userId.equals(userId));
    }

    /**
     * 角色数据范围，用于数据可见性判断。
     *
     * @throws BusinessException 角色未知时抛出，避免越权默认可见
     */
    public static String requireKnownRole(SysUser user) {
        String role = user.getRoleCode();
        if (RoleCodes.ADMIN.equals(role) || RoleCodes.DIRECTOR.equals(role)
                || RoleCodes.TEACHER.equals(role) || RoleCodes.SUPERVISOR.equals(role)
                || RoleCodes.COURSE_ADMIN.equals(role)) {
            return role;
        }
        throw BusinessException.forbidden("账号角色未授权，无法访问系统：" + role);
    }

    /** 会话记录 */
    private static final class Session {
        private final Long userId;
        private final LocalDateTime expireAt;

        private Session(Long userId, LocalDateTime expireAt) {
            this.userId = userId;
            this.expireAt = expireAt;
        }
    }
}
