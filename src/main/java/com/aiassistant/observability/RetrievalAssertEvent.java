package com.aiassistant.observability;

import com.aiassistant.advisor.RetrievalAssertAdvisor.RetrievalAssertion;

import java.time.Instant;
import java.util.List;

/**
 * 检索节点断言事件：由 {@code RetrievalAssertAdvisor} 在完成检索+断言后发布，
 * 供日志重放（ReplayRecorder）与 BadCase 上报（BadCaseReporter）消费。
 *
 * @param traceId   请求追踪 ID
 * @param chatId    会话 ID
 * @param layer     节点名，固定为 {@code retrieval-assert}
 * @param ts        断言发生时间
 * @param query     用于检索的查询文本（可能是改写后的 query）
 * @param assertion 断言结果
 * @param evidence  检索到的文档证据（docId + score + text），供离线重放定位
 */
public record RetrievalAssertEvent(
        String traceId,
        String chatId,
        String layer,
        Instant ts,
        String query,
        RetrievalAssertion assertion,
        List<Evidence> evidence) {

    public record Evidence(String docId, Double score, String text) {
    }
}
