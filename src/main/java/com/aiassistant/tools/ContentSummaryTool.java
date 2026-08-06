package com.aiassistant.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool that uses the AI model itself to summarise text content.
 * Useful after scraping a web page — reduces content before presenting to the user.
 */
public class ContentSummaryTool {

    private final ChatClient chatClient;

    public ContentSummaryTool(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("You are a text summariser. Produce concise, accurate summaries.")
                .build();
    }

    @Tool(description = "Summarise a piece of text into a short, digestible summary. "
            + "Use this after scraping a web page to condense the main points.")
    public String summarise(
            @ToolParam(description = "The raw text content to summarise") String content,
            @ToolParam(description = "Maximum number of sentences in the summary (default 5)") int maxSentences) {
        if (content == null || content.isBlank()) {
            return "No content provided to summarise.";
        }
        int limit = Math.max(1, Math.min(maxSentences, 20));

        // Truncate input to avoid excessive tokens
        String truncated = content.length() > 8000 ? content.substring(0, 8000) : content;

        String prompt = """
                Summarise the following text into no more than %d sentences.
                Focus on key facts and main ideas only.
                
                ---
                %s
                ---
                """.formatted(limit, truncated);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
