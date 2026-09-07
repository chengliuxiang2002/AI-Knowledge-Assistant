package com.aiassistant.observability;

import java.time.Instant;

/**
 * Golden 回归用例：由 BadCase 固化而来，作为离线回归验收的标准样本。
 *
 * @param id             用例 ID
 * @param sourceTraceId  来源请求的 traceId（可追溯现场）
 * @param query          用于回归重放的查询文本
 * @param category       归因桶（检索为空 / 注入条数越界 / manual）
 * @param expectedStatus 期望断言状态，通常为 {@code PASS}
 * @param note           人工备注（可选）
 * @param createdAt      固化时间
 */
public record GoldenCase(
        String id,
        String sourceTraceId,
        String query,
        String category,
        String expectedStatus,
        String note,
        Instant createdAt) {
}
