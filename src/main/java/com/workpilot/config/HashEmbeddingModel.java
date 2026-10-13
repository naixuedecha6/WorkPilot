package com.workpilot.config;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * 兜底 Embedding:DeepSeek 没有 embedding API,所以用确定性哈希向量兜底。
 *
 * <p>把文本按段哈希到固定维度(512)的浮点向量,语义检索退化为关键词+字面相似度。
 * 检索仍能工作(包含共同 token 的文档分数更高),但不是真语义匹配。</p>
 *
 * <p>生产应替换为:</p>
 * <ul>
 *   <li>OpenAI text-embedding-3-small(需要 OpenAI Key)</li>
 *   <li>阿里 DashScope text-embedding-v3(通义千问)</li>
 *   <li>本地 BGE-small-en-v1.5(Ollama / Transformers)</li>
 * </ul>
 */
@Configuration
@Primary
public class HashEmbeddingModel implements EmbeddingModel {

    private static final int DIM = 512;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return embedForResponse(request.getInstructions());
    }

    @Override
    public float[] embed(String text) {
        return hash(text, DIM);
    }

    @Override
    public float[] embed(org.springframework.ai.document.Document document) {
        return embed(document.getText() == null ? "" : document.getText());
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    @Override
    public EmbeddingResponse embedForResponse(List<String> texts) {
        List<float[]> all = embed(texts);
        List<Embedding> embeddings = new java.util.ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            embeddings.add(new Embedding(all.get(i), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIM;
    }

    private static float[] hash(String text, int dim) {
        float[] vec = new float[dim];
        if (text == null) return vec;
        // 简单 token 化:按字符+滑动窗口
        String t = text.toLowerCase();
        try {
            // 多轮 hash 把稀疏 token 散到向量空间
            for (int i = 0; i < t.length(); i++) {
                int idx = Math.floorMod(t.substring(i, Math.min(i + 3, t.length())).hashCode(), dim);
                vec[idx] += 1.0f;
            }
            // 加几个大块的"摘要"特征
            for (int i = 0; i < t.length(); i += 16) {
                int end = Math.min(i + 16, t.length());
                int idx = Math.floorMod(t.substring(i, end).hashCode(), dim);
                vec[idx] += 0.5f;
            }
        } catch (Exception ignored) { }
        // L2 归一化(让余弦相似度等价于内积)
        double norm = 0;
        for (float v : vec) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < dim; i++) vec[i] /= (float) norm;
        return vec;
    }
}
