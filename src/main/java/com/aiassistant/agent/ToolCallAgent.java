package com.aiassistant.agent;

import cn.hutool.core.collection.CollUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Concrete ReAct agent that uses Tool Calling to execute actions.
 * <p>
 * The think() phase calls the LLM with available tools and decides which tool(s) to invoke.
 * The act() phase executes the selected tools and feeds results back to the LLM.
 * <p>
 * Usage:
 * <pre>
 *   ToolCallAgent agent = new ToolCallAgent(tools, chatClient, chatOptions);
 *   agent.setName("blog-assistant");
 *   agent.setSystemPrompt("You are a helpful blog assistant...");
 *   agent.addUserMessage("Summarise the key points about ...");
 *   String result = agent.run();
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private final ToolCallback[] availableTools;
    private final ChatClient chatClient;
    private final ChatOptions chatOptions;
    private final List<Message> messageList = new ArrayList<>();
    private ChatResponse lastChatResponse;

    public ToolCallAgent(ToolCallback[] availableTools, ChatClient chatClient, ChatOptions chatOptions) {
        this.availableTools = availableTools;
        this.chatClient = chatClient;
        this.chatOptions = chatOptions;
    }

    /**
     * Add a user message to the conversation context.
     */
    public void addUserMessage(String text) {
        messageList.add(new UserMessage(text));
    }

    /**
     * Think phase: ask the LLM whether any tools need to be called.
     *
     * @return true if tool calls are needed
     */
    @Override
    public boolean think() {
        log.info("{}: thinking...", getName());
        Prompt prompt = new Prompt(new ArrayList<>(messageList), chatOptions);
        try {
            ChatResponse chatResponse = chatClient.prompt(prompt)
                    .system(getSystemPrompt())
                    .tools(availableTools)
                    .call()
                    .chatResponse();
            this.lastChatResponse = chatResponse;

            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

            if (CollUtil.isEmpty(toolCalls)) {
                String text = assistantMessage.getText();
                log.info("{}: finished thinking — {}", getName(), text != null ? text.substring(0, Math.min(100, text.length())) : "(empty)");
                messageList.add(assistantMessage);
                return false;
            }

            String toolInfo = toolCalls.stream()
                    .map(tc -> tc.name() + "(" + tc.arguments() + ")")
                    .collect(Collectors.joining(", "));
            log.info("{}: decided to call {} tool(s): {}", getName(), toolCalls.size(), toolInfo);
            return true;
        } catch (Exception e) {
            log.error("{}: think phase failed: {}", getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Act phase: execute the chosen tools and record results.
     *
     * @return summary of tool execution results
     */
    @Override
    public String act() {
        log.info("{}: acting...", getName());
        AssistantMessage assistantMessage = lastChatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

        // Execute each requested tool by matching its name to the available callbacks
        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            ToolCallback callback = findToolCallback(toolCall.name());
            String result;
            if (callback == null) {
                result = "No tool found with name: " + toolCall.name();
            } else {
                try {
                    result = callback.call(toolCall.arguments());
                } catch (Exception e) {
                    result = "Error executing tool " + toolCall.name() + ": " + e.getMessage();
                }
            }
            toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), result));
        }

        // Record assistant message + tool responses in conversation context
        messageList.add(assistantMessage);
        messageList.add(new ToolResponseMessage(toolResponses, null));

        String summary = toolResponses.stream()
                .map(r -> r.name() + ": " + truncate(r.responseData(), 200))
                .collect(Collectors.joining(" | "));
        log.info("{}: action complete — {}", getName(), summary);
        return summary;
    }

    private ToolCallback findToolCallback(String name) {
        for (ToolCallback tool : availableTools) {
            if (tool.getToolDefinition().name().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
