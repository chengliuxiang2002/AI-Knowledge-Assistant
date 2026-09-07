package com.aiassistant.advisor;

import com.aiassistant.observability.RetrievalAssertEvent;
import com.aiassistant.observability.RetrievalAssertionEvaluator;
import com.aiassistant.observability.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 检索节点断言 Advisor —— 可插拔的 RAG 检索质量检查器。
 *
 * <p>在请求增强阶段执行一次向量检索，并对检索结果做两处断言：
 * <ol>
 *   <li><b>非空断言</b>：检索是否返回了任何文档；</li>
 *   <li><b>注入条数断言</b>：最终注入 prompt 的文档数是否落在 [minInjected, maxInjected] 区间。</li>
 * </ol>
 * 断言结果写入 advisor context（key 见 {@link #ASSERTION_CONTEXT_KEY}），并发布
 * {@link RetrievalAssertEvent} 供日志重放 / BadCase 闭环复盘消费。
 *
 * <p>说明：Spring AI 的 {@code QuestionAnswerAdvisor} 不对外暴露检索中间结果，
 * 因此本 Advisor 自行执行检索以获得可断言的检索结果；接入时应<b>替代</b>而非叠加
 * {@code QuestionAnswerAdvisor}，避免同一请求重复检索。
 */
@Slf4j
@Component
public class RetrievalAssertAdvisor implements CallAdvisor, StreamAdvisor {

    /** 断言结果在 advisor context 中的 key，下游（日志重放 / 观测）由此读取。 */
    public static final String ASSERTION_CONTEXT_KEY = "retrieval.assertion";

    private final VectorStore vectorStore;
    private final ApplicationEventPublisher eventPublisher;
    private final int topK;
    private final double similarityThreshold;
    private final int minInjecte;
    private final int maxInjected;

    /** 默认配置：对齐 QuestionAnswerAdvisor 的 topK=4，不做相似度阈值过滤，注入条数区间 [1, 4]。 */
    public RetrievalAssertAdvisor(VectorStore vectorStore, ApplicationEventPublisher eventPublisher) {
        this(vectorStore, eventPublisher, 4, 0.0, 1, 4);
    }

    public RetrievalAssertAdvisor(VectorStore vectorStore, ApplicationEventPublisher eventPublisher,
            int topK, double similarityThreshold, int minInjected, int maxInjected) {
        this.vectorStore = vectorStore;
        this.eventPublisher = eventPublisher;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.minInjected = minInjected;
        this.maxInjected = maxInjected;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(before(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(before(request));
    }

    /**
     * 请求增强阶段：提取 query -> 检索 -> 断言 -> 发布事件 -> 注入上下文 -> 返回新请求。
     */
    private ChatClientRequest before(ChatClientRequest request) {
        String query = extractQuery(request);
        List<Document> retrieved = retrieve(query);
        RetrievalAssertion assertion = RetrievalAssertionEvaluator.evaluate(retrieved, minInjected, maxInjected);

        request.context().put(ASSERTION_CONTEXT_KEY, assertion);
        logAssertion(assertion);
        publishAssertEvent(query, retrieved, assertion);

        Prompt newPrompt = request.prompt().augmentUserMessage(buildAugmentedText(query, retrieved));
        return new ChatClientRequest(newPrompt, request.context());
    }

    private String extractQuery(ChatClientRequest request) {
        var userMessage = request.prompt().getUserMessage();
        return userMessage != null ? userMessage.getText() : "";
    }

    private List<Document> retrieve(String query) {
        var builder = org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(query)
                .topK(topK);
        if (similarityThreshold > 0) {
            builder.similarityThreshold(similarityThreshold);
        }
        return vectorStore.similaritySearch(builder.build());
    }

    private void publishAssertEvent(String query, List<Document> retrieved, RetrievalAssertion assertion) {
        String traceId = TraceContext.traceId();
        String chatId = TraceContext.chatId();
        List<RetrievalAssertEvent.Evidence> evidence = retrieved.stream()
                .map(d -> new RetrievalAssertEvent.Evidence(d.getId(), d.getScore(), d.getText()))
                .toList();
        RetrievalAssertEvent event = new RetrievalAssertEvent(
                traceId == null ? "unknown" : traceId,
                chatId == null ? "default" : chatId,
                "retrieval-assert",
                Instant.now(),
                query,
                assertion,
                evidence);
        eventPublisher.publishEvent(event);
    }

    private String buildAugmentedText(String query, List<Document> retrieved) {
        if (retrieved.isEmpty()) {
            // 检索为空：仅保留原始问题，交由模型按"不知为不知"兜底。
            return query;
        }
        String context = retrieved.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
        return """
                请优先基于以下参考资料回答；若参考资料无法回答，请明确说明"知识库中未找到相关信息"，不要编造。

                【参考资料】
                %s

                【用户问题】
                %s
                """.formatted(context, query);
    }

    private void logAssertion(RetrievalAssertion assertion) {
        log.info("检索断言 result={} nonEmpty={} retrieved={} injected={} range=[{},{}] msg={}",
                assertion.status(), assertion.nonEmpty(), assertion.retrievedCount(),
                assertion.injectedCount(), assertion.minInjected(), assertion.maxInjected(),
                assertion.message());
    }

    /** 断言状态。 */
    public enum AssertStatus {
        PASS, WARN, FAIL
    }

    /**
     * 检索节点断言的不可变结果，写入 advisor context 供下游读取。
     */
    public record RetrievalAssertion(
            AssertStatus status,
            boolean nonEmpty,
            int retrievedCount,
            int injectedCount,
            int minInjected,
            int maxInjected,
            String message) {

        public boolean passed() {
            return status == AssertStatus.PASS;
        }
    }
}
