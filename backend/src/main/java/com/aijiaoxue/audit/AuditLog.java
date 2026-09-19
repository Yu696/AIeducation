package com.aijiaoxue.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解。标注在 Controller 的写操作方法上，
 * 由 {@link AuditLogAspect} 自动写入 sys_audit_log。
 *
 * <p>用途：满足实验一风险对策「AI 输出先记录、再审查、后使用」的可追溯要求，
 * 以及 US-01「权限配置可追溯」的验收需要。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 动作：新增 / 修改 / 删除 / 导入 / 导出 */
    String action();

    /** 目标类型，如 COURSE、OFFERING、USER、ROLE_PERMISSION */
    String targetType();

    /** 目标主键在方法参数中的位置，-1 表示不取参数 */
    int idParamIndex() default -1;
}
