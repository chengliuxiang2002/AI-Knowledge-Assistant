package com.aiassistant.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    private ChatClientRequest before(ChatClientRequest chatClientRequest) {
        String userText = chatClientRequest.prompt().getUserMessage().getText();
        chatClientRequest.context().put("re2_input_query", userText);
        String newUserText = """
                %s
                Read the question again: %s
                """.formatted(userText, userText);
        Prompt newPrompt = chatClientRequest.prompt().augmentUserMessage(newUserText);
        return new ChatClientRequest(newPrompt, chatClientRequest.context());
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        return chain.nextCall(this.before(chatClientRequest));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        return chain.nextStream(this.before(chatClientRequest));
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
