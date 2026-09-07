package com.aiassistant.observability;

import com.aiassistant.advisor.RetrievalAssertAdvisor.AssertStatus;

import java.time.Instant;

/**
 * BadCase 记录：断言命中 FAIL/WARN 时生成的持久化条目，携带 traceId 作为证据指针，
 * 供离线重放定位现场、归因与回归固化。
 */
public record BadCaseRecord(
        String id,
        String traceId,
        String chatId,
        String layer,
        AssertStatus status,
        String category,
        String message,
        Instant createdAt) {
}
