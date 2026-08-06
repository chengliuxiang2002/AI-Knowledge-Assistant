package com.aiassistant.service;

import com.aiassistant.advisor.LoggingAdvisor;
import com.aiassistant.agent.ToolCallAgent;
import com.aiassistant.rag.QueryRewriter;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.stream.Collectors;

@Service
@Slf4j
public class KnowledgeAssistantService {

    private final ChatClient chatClient;

    @Resource
    private VectorStore knowledgeVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private ToolCallback[] allTools;

    private static final String SYSTEM_PROMPT = """
            You are an AI Knowledge Assistant, specialized in answering questions based on
            provided knowledge base and web resources. Be concise, accurate, and helpful.
            When answering, cite sources when possible. If you don't know something,
            say so honestly and offer to search the web for more information.
            """;

    public KnowledgeAssistantService(ChatModel dashscopeChatModel) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new LoggingAdvisor()
                )
                .build();
    }

    /**
     * Basic chat with multi-turn memory
     */
    public String chat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * Streaming chat with multi-turn memory
     */
    public Flux<String> chatStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    /**
     * RAG-based knowledge Q&A with query rewriting
     */
    public String chatWithRag(String message, String chatId) {
        String rewrittenMessage = queryRewriter.rewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new QuestionAnswerAdvisor(knowledgeVectorStore))
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * Streaming RAG-based knowledge Q&A
     */
    public Flux<String> chatWithRagStream(String message, String chatId) {
        String rewrittenMessage = queryRewriter.rewrite(message);
        return chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new QuestionAnswerAdvisor(knowledgeVectorStore))
                .stream()
                .content();
    }

    /**
     * Chat with tool calling (web search capability)
     */
    public String chatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * Agent-based autonomous reasoning with RAG + Tools.
     * The agent can decide whether to search the web, scrape pages,
     * summarise content, or query the knowledge base — all autonomously.
     */
    public String agentResearch(String question) {
        var chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();

        ToolCallAgent agent = new ToolCallAgent(allTools, chatClient, chatOptions);
        agent.setName("knowledge-agent");
        agent.setMaxSteps(8);
        String systemPrompt = """
                You are a research agent for a personal blog. Your task is to answer
                the user's question thoroughly using the available tools.
                
                Available capabilities:
                - searchWeb: search the internet for information
                - scrapeWebPage: fetch and extract text from a URL
                - summarise: condense long text into key points
                
                Strategy: if you need external information, search the web first,
                then scrape relevant pages, then summarise the findings.
                If the answer is straightforward, provide it directly without tools.
                """;
        agent.setSystemPrompt(systemPrompt);
        agent.addUserMessage(question);
        return agent.run();
    }
}
