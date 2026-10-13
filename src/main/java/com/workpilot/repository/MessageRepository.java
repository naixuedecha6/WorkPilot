package com.workpilot.repository;

import com.workpilot.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    List<Message> findByRunIdOrderByCreatedAtAsc(Long runId);
}
