package com.workpilot.service;

import com.workpilot.agent.AgentOrchestrator;
import com.workpilot.entity.AgentRun;
import com.workpilot.entity.Conversation;
import com.workpilot.entity.Message;
import com.workpilot.entity.MessageRole;
import com.workpilot.repository.ConversationRepository;
import com.workpilot.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 聊天服务:负责会话管理、消息持久化、Agent 运行触发。
 * <p>普通问答 vs Agent 任务的简单分流:包含"分析/排查/创建/生成报告"等关键词 → Agent 任务,
 * 否则直接调模型回答。第一版用启发式规则,后续可换成更智能的分类器。</p>
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AgentOrchestrator orchestrator;
    private final UserContextService userContextService;

    @Transactional
    public Long createConversation(String title) {
        Conversation c = Conversation.builder()
                .userId(userContextService.currentUserId())
                .title(title == null || title.isBlank() ? "新会话" : title)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return conversationRepository.save(c).getId();
    }

    public List<Conversation> listConversations() {
        return conversationRepository.findByUserIdOrderByUpdatedDesc(userContextService.currentUserId());
    }

    public List<Message> listMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public boolean conversationExists(Long conversationId) {
        return conversationRepository.existsById(conversationId);
    }

    @Transactional
    public Long saveUserMessage(Long conversationId, String content) {
        Message msg = Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content(content)
                .createdAt(Instant.now())
                .build();
        return messageRepository.save(msg).getId();
    }

    @Transactional
    public Long saveAssistantMessage(Long conversationId, String content, Long runId) {
        Message msg = Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.ASSISTANT)
                .content(content)
                .runId(runId)
                .createdAt(Instant.now())
                .build();
        Long id = messageRepository.save(msg).getId();
        if (conversationId != null) {
            conversationRepository.findById(conversationId).ifPresent(c -> {
                c.setUpdatedAt(Instant.now());
                conversationRepository.save(c);
            });
        }
        return id;
    }

    /**
     * 判断是否需要走 Agent 任务。
     * 启发式:消息里包含"分析/排查/创建/待办/报告/工具/检索知识库"等关键词就当作任务。
     */
    public boolean isTaskRequest(String text) {
        if (text == null) return false;
        String[] keywords = {"分析", "排查", "总结", "归纳", "报告", "创建", "新建", "写报告", "待办", "检索知识库", "知识库"};
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    @Transactional
    public AgentRun runTask(String userInput, Long conversationId) {
        return orchestrator.start(userInput, userContextService.currentUserId(), conversationId);
    }
}
