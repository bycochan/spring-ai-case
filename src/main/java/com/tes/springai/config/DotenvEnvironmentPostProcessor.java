package com.tes.springai.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取项目根目录 .env 文件并注入为高优先级属性源。
 * 最终优先级：-D 系统属性 > .env > OS 环境变量 > application.yml 默认值。
 * 用于本地密钥配置（如智谱 API Key）：.env 已被 .gitignore 排除不随仓库分发，
 * 直接 mvn spring-boot:run 即可生效，无需设置系统环境变量或脚本插参。
 * 通过 META-INF/spring.factories 注册，在 application.yml 占位符解析前执行。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 属性源名称，避免重复注册
     */
    static final String PROPERTY_SOURCE_NAME = "dotenvLocal";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }

        Path envFile = Paths.get(System.getProperty("user.dir"), ".env");
        if (!Files.isReadable(envFile)) {
            return;
        }

        Map<String, Object> props = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                // 去除可能残留的 UTF-8 BOM 后再判断注释与空行
                if (trimmed.startsWith("\uFEFF")) {
                    trimmed = trimmed.substring(1).trim();
                }
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                // 去掉可选的成对引号
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                props.put(key, value);
            }
        } catch (IOException e) {
            // 此时日志系统未初始化，直接输出便于在 boot.log 中定位
            System.out.println("[.env] 读取失败，跳过本地配置注入: " + e.getMessage());
            return;
        }

        if (!props.isEmpty()) {
            // 插到 OS 环境变量之前：.env 覆盖残留的同名环境变量，仍可被 -D 系统属性覆盖
            environment.getPropertySources().addBefore(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new MapPropertySource(PROPERTY_SOURCE_NAME, props));
            System.out.println("[.env] loaded " + props.size() + " local config(s): " + props.keySet());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
