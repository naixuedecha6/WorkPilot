package com.workpilot.service;

import com.workpilot.entity.Document;
import com.workpilot.entity.DocumentChunk;
import com.workpilot.entity.DocumentStatus;
import com.workpilot.exception.AgentException;
import com.workpilot.repository.DocumentChunkRepository;
import com.workpilot.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 知识库服务:文档上传、解析、分块、嵌入、删除。
 * 第一版支持 PDF、Markdown、TXT。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final VectorStore vectorStore;

    private static final long MAX_SIZE = 20 * 1024 * 1024L; // 20MB

    @Transactional
    public Document upload(MultipartFile file, Long ownerId) {
        if (file == null || file.isEmpty()) {
            throw new AgentException("INVALID_FILE", "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new AgentException("FILE_TOO_LARGE", "文件超过 20MB");
        }
        String name = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();

        Document doc = Document.builder()
                .ownerId(ownerId)
                .name(name)
                .mimeType(mime)
                .status(DocumentStatus.UPLOADED)
                .chunkCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        doc = documentRepository.save(doc);

        // 异步处理
        processAsync(doc.getId(), file, ownerId);
        return doc;
    }

    @Async
    public void processAsync(Long docId, MultipartFile file, Long ownerId) {
        try {
            process(docId, file, ownerId);
        } catch (Exception e) {
            log.error("Document process failed for id={}", docId, e);
            markFailed(docId, e.getMessage());
        }
    }

    @Transactional
    public void process(Long docId, MultipartFile file, Long ownerId) throws Exception {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new AgentException("DOC_NOT_FOUND", "文档不存在"));
        doc.setStatus(DocumentStatus.PROCESSING);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        String name = doc.getName().toLowerCase();
        List<org.springframework.ai.document.Document> springDocs;
        if (name.endsWith(".pdf")) {
            springDocs = readPdf(file);
        } else if (name.endsWith(".md") || name.endsWith(".markdown")) {
            springDocs = readMarkdown(file);
        } else if (name.endsWith(".txt")) {
            springDocs = List.of(new org.springframework.ai.document.Document(
                    new String(file.getBytes())));
        } else {
            throw new AgentException("UNSUPPORTED_TYPE", "暂不支持的文件类型: " + doc.getName());
        }

        // 文本分块
        TokenTextSplitter splitter = new TokenTextSplitter(800, 200, 5, 10000, true);
        List<org.springframework.ai.document.Document> chunks = splitter.apply(springDocs);

        // 给每块加 metadata
        for (int i = 0; i < chunks.size(); i++) {
            org.springframework.ai.document.Document c = chunks.get(i);
            c.getMetadata().put("document_id", docId);
            c.getMetadata().put("document_name", doc.getName());
            c.getMetadata().put("owner_id", ownerId);
            c.getMetadata().put("chunk_index", i);
        }

        // 写入向量库
        vectorStore.add(chunks);
        // 立刻持久化到文件(避免依赖 JVM 优雅关闭)
        try {
            ((org.springframework.ai.vectorstore.SimpleVectorStore) vectorStore)
                    .save(java.nio.file.Paths.get("data", "vectorstore.json").toAbsolutePath().toFile());
        } catch (Exception ignored) { /* 下次启动还能接受 */ }

        // 元数据写入 document_chunks(便于按 id 追溯)
        for (int i = 0; i < chunks.size(); i++) {
            org.springframework.ai.document.Document c = chunks.get(i);
            DocumentChunk ch = DocumentChunk.builder()
                    .documentId(docId)
                    .content(c.getText() == null ? "" : c.getText())
                    .chunkIndex(i)
                    .metadata(c.getMetadata().toString())
                    .createdAt(Instant.now())
                    .build();
            chunkRepository.save(ch);
        }

        doc.setStatus(DocumentStatus.INDEXED);
        doc.setChunkCount(chunks.size());
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);
    }

    private List<org.springframework.ai.document.Document> readPdf(MultipartFile file) throws Exception {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                new ByteArrayResource(file.getBytes()),
                PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                        .build());
        return reader.get();
    }

    private List<org.springframework.ai.document.Document> readMarkdown(MultipartFile file) throws java.io.IOException {
        MarkdownDocumentReader reader = new MarkdownDocumentReader(
                new ByteArrayResource(file.getBytes()),
                MarkdownDocumentReaderConfig.defaultConfig());
        return reader.get();
    }

    @Transactional
    public void markFailed(Long docId, String message) {
        documentRepository.findById(docId).ifPresent(d -> {
            d.setStatus(DocumentStatus.FAILED);
            d.setErrorMessage(message == null ? "" : message);
            d.setUpdatedAt(Instant.now());
            documentRepository.save(d);
        });
    }

    @Transactional
    public void delete(Long docId, Long ownerId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new AgentException("DOC_NOT_FOUND", "文档不存在"));
        if (!doc.getOwnerId().equals(ownerId)) {
            throw new AgentException("FORBIDDEN", "无权删除该文档");
        }
        chunkRepository.deleteByDocumentId(docId);
        documentRepository.delete(doc);
    }

    public List<Document> list(Long ownerId) {
        return documentRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }
}
