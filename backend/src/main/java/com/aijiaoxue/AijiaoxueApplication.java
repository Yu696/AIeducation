package com.aijiaoxue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 爱教学 · 教学质量全链路数字化管理平台
 * 实验二 Sprint 1「查课程」后端启动类。
 *
 * <p>Sprint 1 交付范围（US-01 ~ US-06）：
 * 统一数据建模与样例数据导入、角色—功能—数据权限矩阵、教研室主任/教师/教学督导三类工作台。
 *
 * @author 16组 DRI 杨继鑫
 */
@SpringBootApplication
@EnableTransactionManagement
@MapperScan("com.aijiaoxue.mapper")
public class AijiaoxueApplication {

    public static void main(String[] args) {
        SpringApplication.run(AijiaoxueApplication.class, args);
    }
}
