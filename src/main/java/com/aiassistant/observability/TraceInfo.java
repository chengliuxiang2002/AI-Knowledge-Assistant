package com.aiassistant.observability;

/**
 * 一次请求的追踪信息。
 *
 * @param traceId 请求级追踪 ID（透传或生成）
 * @param chatId  会话 ID（默认 default）
 */
public record TraceInfo(String traceId, String chatId) {
}
