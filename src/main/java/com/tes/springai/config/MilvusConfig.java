package com.tes.springai.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private Integer milvusPort;

    @Bean
    @ConditionalOnProperty(name = "milvus.enabled", havingValue = "true", matchIfMissing = false)
    public MilvusServiceClient milvusClient() {
        log.info("连接 Milvus: {}:{}", milvusHost, milvusPort);
        
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(milvusHost)
                    .withPort(milvusPort)
                    .build();
            
            MilvusServiceClient client = new MilvusServiceClient(connectParam);
            log.info("Milvus 连接成功");
            
            return client;
        } catch (Exception e) {
            log.error("Milvus 连接失败: {}", e.getMessage());
            throw new RuntimeException("无法连接到 Milvus，请确保 Milvus 服务已启动", e);
        }
    }
}
