package com.aiassistant.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求级 traceId 过滤器：优先透传上游 {@code X-Trace-Id}，否则生成新的 traceId；
 * 同时从 query 参数读取 chatId（默认 default），写入 {@link TraceContext} 与 MDC，
 * 并在响应头回传 traceId 便于前端/网关串联。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        String chatId = request.getParameter("chatId");
        if (chatId == null || chatId.isBlank()) {
            chatId = "default";
        }

        TraceContext.set(new TraceInfo(traceId, chatId));
        response.setHeader(TraceContext.HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
