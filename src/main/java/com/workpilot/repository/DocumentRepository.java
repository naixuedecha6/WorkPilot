package com.workpilot.repository;

import com.workpilot.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
