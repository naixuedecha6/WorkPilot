package com.workpilot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AI 相关 Bean:ChatClient、EmbeddingModel、VectorStore。
 *
 * 说明:开发期用 SimpleVectorStore(文件持久化到本地),生产期切换为 pgvector 时,
 * 只需替换 VectorStore Bean 即可,业务代码不变。
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * 开发期向量库:把向量写到 ./data/vectorstore.json,重启不丢。
     * 切到 PostgreSQL+pgvector 时删掉此 Bean,启动 spring-ai-pgvector-store 自动装配。
     */
    @Bean
    @Primary
    public VectorStore vectorStore(EmbeddingModel embeddingModel,
                                   @Value("${workpilot.vector-store.path:./data/vectorstore.json}") String path) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        ResourceLoader rl = new org.springframework.core.io.DefaultResourceLoader();
        Resource res = rl.getResource("file:" + absolutePath(path));
        if (res.exists()) {
            try {
                store.load(res.getFile());
            } catch (Exception ignored) {
                // 加载失败就当空库
            }
        }
        // 注册到 JVM 关闭钩子,保证最后一次写入
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                store.save(Paths.get(absolutePath(path)).toFile());
            } catch (Exception ignored) {
            }
        }));
        return store;
    }

    private String absolutePath(String p) {
        Path path = Paths.get(p);
        return path.isAbsolute() ? p : path.toAbsolutePath().toString();
    }
}
