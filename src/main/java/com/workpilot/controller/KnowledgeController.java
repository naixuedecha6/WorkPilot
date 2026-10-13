package com.workpilot.controller;

import com.workpilot.entity.Document;
import com.workpilot.service.KnowledgeService;
import com.workpilot.service.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final UserContextService userContextService;
    private final VectorStore vectorStore;

    @PostMapping("/documents")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Document doc = knowledgeService.upload(file, userContextService.currentUserId());
        return Map.of(
                "id", doc.getId(),
                "name", doc.getName(),
                "status", doc.getStatus().name()
        );
    }

    @GetMapping("/documents")
    public List<Document> list() {
        return knowledgeService.list(userContextService.currentUserId());
    }

    @DeleteMapping("/documents/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        knowledgeService.delete(id, userContextService.currentUserId());
        return Map.of("deleted", id);
    }

    /**
     * 直接检索(给知识库 Tab 用的"试试搜索"功能)
     * 不走 Agent,返回原始命中片段,用于人工预览。
     * SimpleVectorStore 不一定支持 filterExpression,这里在应用层做 owner 过滤。
     */
    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        if (q == null || q.isBlank()) {
            return Map.of("count", 0, "hits", List.of());
        }
        if (topK <= 0 || topK > 20) topK = 5;

        Long ownerId = userContextService.currentUserId();
        // 多查一些,过滤后可能不够
        List<org.springframework.ai.document.Document> all = vectorStore.similaritySearch(
                SearchRequest.builder().query(q).topK(Math.max(topK * 3, 15)).build());
        if (all == null) all = List.of();

        // 应用层按 owner_id 过滤
        List<org.springframework.ai.document.Document> docs = all.stream()
                .filter(d -> {
                    Object oid = d.getMetadata() == null ? null : d.getMetadata().get("owner_id");
                    return oid != null && ownerId.toString().equals(oid.toString());
                })
                .limit(topK)
                .toList();

        List<Map<String, Object>> hits = new ArrayList<>();
        for (org.springframework.ai.document.Document d : docs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", d.getText() == null ? "" : d.getText());
            m.put("metadata", d.getMetadata() == null ? Map.of() : d.getMetadata());
            hits.add(m);
        }
        return Map.of("count", hits.size(), "hits", hits);
    }
}
