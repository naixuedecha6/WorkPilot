package com.workpilot.controller;

import com.workpilot.entity.Conversation;
import com.workpilot.entity.Message;
import com.workpilot.entity.AgentRun;
import com.workpilot.service.ChatService;
import com.workpilot.service.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient chatClient;
    private final ChatService chatService;
    private final UserContextService userContextService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "workpilot", "userId", userContextService.currentUserId());
    }

    /** 会话管理 */
    @PostMapping("/conversations")
    public Map<String, Object> createConversation(@RequestBody Map<String, String> body) {
        Long id = chatService.createConversation(body.get("title"));
        return Map.of("id", id);
    }

    @GetMapping("/conversations")
    public List<Conversation> listConversations() {
        return chatService.listConversations();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<Message> listMessages(@PathVariable Long id) {
        return chatService.listMessages(id);
    }

    /**
     * 聊天入口:如果是任务型请求,启动 Agent Run(非流式,返回 run 信息);
     * 否则直接调模型返回。
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", ""));
        Long conversationId = body.get("conversationId") == null
                ? null : Long.valueOf(body.get("conversationId").toString());
        if (message.isBlank()) {
            return Map.of("error", "message is empty");
        }

        // 自动建会话(若提供了 conversationId 但不存在,也会新建)
        if (conversationId == null) {
            String title = message.length() > 30 ? message.substring(0, 30) : message;
            conversationId = chatService.createConversation(title);
        } else if (!chatService.conversationExists(conversationId)) {
            String title = message.length() > 30 ? message.substring(0, 30) : message;
            conversationId = chatService.createConversation(title);
        }
        chatService.saveUserMessage(conversationId, message);

        if (chatService.isTaskRequest(message)) {
            AgentRun run = chatService.runTask(message, conversationId);
            chatService.saveAssistantMessage(conversationId, "已启动 Agent 任务,当前状态: " + run.getStatus(), run.getId());
            return Map.of(
                    "conversationId", conversationId,
                    "runId", run.getId(),
                    "status", run.getStatus().name(),
                    "output", run.getOutput() == null ? "" : run.getOutput(),
                    "error", run.getErrorMessage() == null ? "" : run.getErrorMessage()
            );
        }

        // 普通问答:直调模型
        String reply = chatClient.prompt().user(message).call().content();
        chatService.saveAssistantMessage(conversationId, reply, null);
        return Map.of("conversationId", conversationId, "reply", reply);
    }

    /** 流式聊天(SSE):普通问答流式输出 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", ""));
        if (chatService.isTaskRequest(message)) {
            return Flux.error(new IllegalArgumentException("任务型请求请用 /api/v1/agent/runs"));
        }
        return chatClient.prompt().user(message).stream().content();
    }
}
