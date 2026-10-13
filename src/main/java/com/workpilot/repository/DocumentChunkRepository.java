package com.workpilot.repository;

import com.workpilot.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);
    long countByDocumentId(Long documentId);
    void deleteByDocumentId(Long documentId);
}
