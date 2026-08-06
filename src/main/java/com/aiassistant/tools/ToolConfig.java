package com.aiassistant.tools;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    @Value("${search-api.api-key:}")
    private String searchApiKey;

    @Bean
    public ToolCallback[] allTools(ChatModel dashscopeChatModel) {
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ContentSummaryTool summaryTool = new ContentSummaryTool(dashscopeChatModel);
        return ToolCallbacks.from(webSearchTool, webScrapingTool, summaryTool);
    }
}
