package com.aiassistant.observability;

import org.slf4j.MDC;

/**
 * 请求级追踪上下文，通过 ThreadLocal 保存 {@link TraceInfo}，并同步写入 MDC 供日志输出。
 *
 * <p>说明：Advisor 的 {@code before()} 在请求线程内执行，因此可通过本类读取当前请求的
 * traceId/chatId；若未来检索断言被移到异步线程执行，需改为显式传参或使用 reactive context，
 * 而非依赖 ThreadLocal。
 */
public final class TraceContext {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_CHAT_ID = "chatId";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    private static final ThreadLocal<TraceInfo> HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(TraceInfo info) {
        HOLDER.set(info);
        MDC.put(MDC_TRACE_ID, info.traceId());
        MDC.put(MDC_CHAT_ID, info.chatId());
    }

    public static TraceInfo get() {
        return HOLDER.get();
    }

    public static String traceId() {
        TraceInfo info = HOLDER.get();
        return info != null ? info.traceId() : null;
    }

    public static String chatId() {
        TraceInfo info = HOLDER.get();
        return info != null ? info.chatId() : "default";
    }

    public static void clear() {
        HOLDER.remove();
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_CHAT_ID);
    }
}
