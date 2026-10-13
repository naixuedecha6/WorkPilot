package com.workpilot.repository;

import com.workpilot.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @Query("SELECT c FROM Conversation c WHERE c.userId = :userId ORDER BY c.updatedAt DESC")
    List<Conversation> findByUserIdOrderByUpdatedDesc(Long userId);
}
