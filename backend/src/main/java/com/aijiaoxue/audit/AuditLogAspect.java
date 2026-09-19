package com.aijiaoxue.audit;

import com.aijiaoxue.entity.SysAuditLog;
import com.aijiaoxue.mapper.SysAuditLogMapper;
import com.aijiaoxue.security.CurrentUser;
import com.aijiaoxue.security.CurrentUserHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作审计切面：拦截 {@link AuditLog} 标注的方法，成功后落库一条操作日志。
 *
 * <p>记录失败不影响业务返回（仅打警告日志），保证审计能力不会成为主流程的单点故障。
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final SysAuditLogMapper auditLogMapper;

    public AuditLogAspect(SysAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @AfterReturning(pointcut = "@annotation(auditLog)", returning = "result")
    public void record(JoinPoint joinPoint, AuditLog auditLog, Object result) {
        try {
            CurrentUser user = CurrentUserHolder.get();
            SysAuditLog entity = new SysAuditLog();
            entity.setOperator(user == null ? "anonymous" : user.getUsername());
            entity.setRoleCode(user == null ? "-" : user.getRoleCode());
            entity.setAction(auditLog.action());
            entity.setTargetType(auditLog.targetType());
            entity.setTargetId(resolveTargetId(joinPoint, auditLog));
            entity.setDetail(buildDetail(joinPoint, result));
            entity.setIp(resolveIp());
            entity.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写入操作日志失败（不影响业务）：{}", e.getMessage());
        }
    }

    /** 取目标主键，优先取注解指定参数位，其次取返回值中的 id */
    private String resolveTargetId(JoinPoint joinPoint, AuditLog auditLog) {
        int idx = auditLog.idParamIndex();
        Object[] args = joinPoint.getArgs();
        if (idx >= 0 && idx < args.length && args[idx] != null) {
            return String.valueOf(args[idx]);
        }
        return "-";
    }

    /** 记录方法签名与入参摘要，便于事后追溯 */
    private String buildDetail(JoinPoint joinPoint, Object result) {
        String method = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        StringBuilder sb = new StringBuilder(method);
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            sb.append(" 参数[");
            for (int i = 0; i < Math.min(args.length, 3); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Object a = args[i];
                String text = a == null ? "null" : String.valueOf(a);
                sb.append(text.length() > 120 ? text.substring(0, 120) + "..." : text);
            }
            sb.append("]");
        }
        String detail = sb.toString();
        return detail.length() > 500 ? detail.substring(0, 500) : detail;
    }

    /** 取请求来源 IP，便于审计追责 */
    private String resolveIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "-";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
