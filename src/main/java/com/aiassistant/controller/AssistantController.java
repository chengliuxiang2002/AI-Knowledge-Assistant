package com.aiassistant.controller;

import com.aiassistant.service.KnowledgeAssistantService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    @Resource
    private KnowledgeAssistantService knowledgeAssistantService;

    /**
     * Basic chat (sync)
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message,
                       @RequestParam(defaultValue = "default") String chatId) {
        return knowledgeAssistantService.chat(message, chatId);
    }

    /**
     * Streaming chat (SSE)
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String message,
                                    @RequestParam(defaultValue = "default") String chatId) {
        return knowledgeAssistantService.chatStream(message, chatId);
    }

    /**
     * RAG knowledge base Q&A
     */
    @GetMapping("/chat/rag")
    public String chatWithRag(@RequestParam String message,
                               @RequestParam(defaultValue = "default") String chatId) {
        return knowledgeAssistantService.chatWithRag(message, chatId);
    }

    /**
     * Streaming RAG knowledge base Q&A
     */
    @GetMapping(value = "/chat/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithRagStream(@RequestParam String message,
                                           @RequestParam(defaultValue = "default") String chatId) {
        return knowledgeAssistantService.chatWithRagStream(message, chatId);
    }

    /**
     * Chat with tool calling (web search)
     */
    @GetMapping("/chat/tools")
    public String chatWithTools(@RequestParam String message,
                                 @RequestParam(defaultValue = "default") String chatId) {
        return knowledgeAssistantService.chatWithTools(message, chatId);
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    /**
     * Agent-based autonomous research — the AI plans and chains
     * multiple tool calls (web search → scrape → summarise) on its own.
     */
    @GetMapping("/agent/research")
    public String agentResearch(@RequestParam String question) {
        return knowledgeAssistantService.agentResearch(question);
    }
}
