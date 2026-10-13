package com.workpilot.tools;

import com.workpilot.dto.ToolContext;
import com.workpilot.dto.ToolResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库检索工具:基于向量库的语义检索 + 引用溯源。
 *
 * <p>已按 owner_id 过滤,只返回当前用户的文档片段。
 * 这样不同用户之间数据不会互相串。</p>
 */
@Component
public class KnowledgeTools implements Tool {

    private final VectorStore vectorStore;

    @Value("${workpilot.rag.top-k:5}")
    private int topK;

    public KnowledgeTools(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override public String name() { return "search_knowledge"; }

    @Override
    public String description() {
        return "在用户的知识库里做语义检索,返回与查询最相关的文档片段(含来源信息)。" +
                "参数:query(必填,问题文本),topK(可选,默认5)。" +
                "无需审批。";
    }

    @Override
    public ToolResult execute(MapLike arguments, ToolContext context) {
        String query = arguments.getString("query");
        if (query == null || query.isBlank()) {
            return ToolResult.fail("INVALID_ARGS", "query 必填");
        }
        int k = arguments.getInt("topK", topK == 0 ? 5 : topK);
        if (k <= 0 || k > 20) k = 5;

        // 多查一些,然后在应用层按 owner_id 过滤(SimpleVectorStore 不保证支持 filter)
        int probe = Math.max(k * 3, 15);
        List<Document> raw = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(probe).build());
        if (raw == null) raw = List.of();
        Long ownerId = context.getUserId();
        List<Document> docs = ownerId == null ? raw : raw.stream()
                .filter(d -> {
                    Object oid = d.getMetadata() == null ? null : d.getMetadata().get("owner_id");
                    return oid != null && ownerId.toString().equals(oid.toString());
                })
                .limit(k)
                .toList();

        if (docs.isEmpty()) {
            return ToolResult.ok(Map.of("hits", List.of(), "count", 0),
                    "知识库中没有匹配的内容");
        }

        List<Map<String, Object>> hits = docs.stream().map(d -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("content", d.getText() == null ? "" : d.getText());
            m.put("metadata", d.getMetadata() == null ? Map.of() : d.getMetadata());
            return m;
        }).collect(Collectors.toList());

        String summary = "检索到 " + hits.size() + " 条相关片段,最相关的一条摘要: "
                + truncate(docs.get(0).getText(), 200);

        return ToolResult.ok(Map.of("hits", hits, "count", hits.size()), summary);
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
