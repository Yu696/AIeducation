package com.aijiaoxue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 测试基类。
 *
 * <p>统一提供：H2 测试库环境（application-test.yml）、MockMvc、登录取 token 的工具方法。
 * 所有测试均不依赖本机 MySQL 是否启动，保证在任意机器上可复现。
 *
 * <p>写操作测试类请叠加 {@code @Transactional}，使数据变更自动回滚，
 * 避免影响其他测试类对样例数据的断言（如督导覆盖率的 60.0%）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    /** 演示账号口令，与 data-h2.sql 中的 BCrypt 哈希一致 */
    protected static final String DEMO_PASSWORD = "123456";

    /**
     * 每个测试方法前把样例数据恢复为基线快照。
     *
     * <p>H2 内存库在整个测试 JVM 内共享，且自增序列在事务回滚后不会回退：
     * 先用 {@code id > N} 清理会漏掉「序列已前进、id 却落在基线区间内」的残留行。
     * 因此这里改为<b>显式白名单基线 ID</b>：只保留 data-h2.sql 中定义的 id，其余全部删除。
     */
    @BeforeEach
    void resetFixtureData() {
        // 保留基线的 1 条日志；其余引用数据按依赖顺序清理
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE id <> 1");
        jdbcTemplate.update("DELETE FROM supervision WHERE id NOT IN (1,2,3,4,5)");
        jdbcTemplate.update("DELETE FROM evaluation WHERE id NOT IN (1,2,3,4,5,6,7)");
        jdbcTemplate.update("DELETE FROM course_resource WHERE id NOT IN (1,2,3,4,5,6)");
        jdbcTemplate.update("DELETE FROM teaching_resource WHERE id NOT IN (1,2,3,4,5,6)");
        jdbcTemplate.update("DELETE FROM student_count WHERE id NOT IN (1,2,3,4,5)");
        jdbcTemplate.update("DELETE FROM offering_teacher WHERE id NOT IN (1,2,3,4,5,6,7)");
        jdbcTemplate.update("DELETE FROM offering WHERE id NOT IN (1,2,3,4,5,6,7)");
        jdbcTemplate.update("DELETE FROM course_intro WHERE course_id NOT IN (1,3)");
        jdbcTemplate.update("DELETE FROM course WHERE id NOT IN (1,2,3,4,5,6,7)");
        jdbcTemplate.update("DELETE FROM teacher WHERE id NOT IN (1,2,3,4,5)");
        jdbcTemplate.update("DELETE FROM sys_role_permission");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id NOT IN (1,2,3,4,5,6)");
        // 恢复可能被测试修改过的基线行，保证断言不依赖执行顺序
        jdbcTemplate.update("UPDATE sys_config SET config_value = 'false' WHERE config_key = 'cross_department'");
        jdbcTemplate.update("UPDATE sys_user SET enabled = 1 WHERE id = 3");
        jdbcTemplate.update("UPDATE offering SET offering_status = '正常开课', student_count = 76 WHERE id = 5");
        jdbcTemplate.update("UPDATE supervision SET status = '已安排', finish_date = NULL, score = NULL WHERE id = 2");
        jdbcTemplate.update("UPDATE supervision SET status = '已完成', finish_date = '2025-04-10', score = 88.0 WHERE id = 5");
        jdbcTemplate.update("UPDATE course SET course_name = '人工智能导论', credit = 2.0, "
                + "total_hours = 32, status = '草稿' WHERE id = 7");
        // US-07：挂接状态可能被非事务用例改写，恢复基线（1-5 已挂接，6 已软解绑）
        jdbcTemplate.update("UPDATE course_resource SET associated = 1 WHERE id IN (1,2,3,4,5)");
        jdbcTemplate.update("UPDATE course_resource SET associated = 0 WHERE id = 6");
        // 清空角色权限后，把角色权限缓存一并失效，避免缓存与库不一致
        permissionCacheEvict();
    }

    /** 清空 PermissionService 的角色权限缓存，避免缓存与库不一致 */
    private void permissionCacheEvict() {
        try {
            Object service = applicationContext.getBean("permissionService");
            service.getClass().getMethod("clearCache").invoke(service);
        } catch (Exception ignored) {
            // 缓存清理失败不影响测试主流程
        }
    }

    /**
     * 登录并返回令牌。
     *
     * @param username 演示账号：admin / director / teacher1 / teacher2 / supervisor / courseadmin
     */
    protected String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        if (node.path("code").asInt() != 0) {
            throw new IllegalStateException("登录失败：" + body);
        }
        return node.path("data").path("token").asText();
    }

    /** Bearer 请求头值 */
    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
