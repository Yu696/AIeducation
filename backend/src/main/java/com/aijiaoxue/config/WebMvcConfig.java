package com.aijiaoxue.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 资源映射配置。
 *
 * <p>Sprint 1 采用前后端分离目录（backend / frontend），但演示时希望「启动后端即可打开页面」，
 * 因此把仓库中的 frontend 目录额外映射为静态资源位置，同时保留 Spring Boot 默认的
 * classpath:/static/ 位置。
 *
 * <p>解析策略：依次尝试若干候选路径（支持从 backend/ 目录或仓库根目录启动），
 * 命中第一个存在的目录即生效，避免写死相对路径导致换机器就失效。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    /** 可选的 frontend 目录位置，按优先级排列 */
    private static final String[] CANDIDATES = {
            "../frontend", "frontend", "./frontend", "../../frontend"
    };

    @Value("${aijiaoxue.frontend-dir:}")
    private String configuredDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path frontend = resolveFrontendDir();
        if (frontend == null) {
            log.warn("未找到 frontend 目录，前端页面将不可用；可用 -Daijiaoxue.frontend-dir=<绝对路径> 指定");
            return;
        }
        String root = frontend.toUri().toString();          // .../frontend/
        String assets = frontend.resolve("assets").toUri().toString(); // .../frontend/assets/
        log.info("前端静态资源目录已挂载：{}", root);

        // 注意：资源处理器匹配后，剩余路径是「去掉前缀」的部分。
        // 因此 /assets/** 必须指向 frontend/assets/ 而不是 frontend/，否则会去找 frontend/js/api.js。
        registry.addResourceHandler("/index.html")
                .addResourceLocations(root)
                .setCachePeriod(0);
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(assets)
                .setCachePeriod(0);
    }

    /** 根路径与 /index.html 都指向登录页 */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    /** 解析 frontend 目录 */
    private Path resolveFrontendDir() {
        if (configuredDir != null && !configuredDir.isBlank()) {
            Path p = Paths.get(configuredDir).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        for (String candidate : CANDIDATES) {
            Path p = Paths.get(candidate).toAbsolutePath().normalize();
            if (Files.isDirectory(p) && Files.exists(p.resolve("index.html"))) {
                return p;
            }
        }
        return null;
    }
}
