package com.aiassistant.observability;

import com.aiassistant.advisor.RetrievalAssertAdvisor.AssertStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BadCase 自动上报器：监听 {@link RetrievalAssertEvent}。
 * <ul>
 *   <li>所有事件先交给 {@link ReplayRecorder} 落盘（日志重放）；</li>
 *   <li>FAIL（检索为空）→ 必报；</li>
 *   <li>WARN（条数越界）→ 默认收敛（同一会话累计达到阈值才上报），
 *       可配 {@code assistant.observability.report-on-warn=true} 每次上报；</li>
 *   <li>同一 (chatId, category) 一分钟内去重，避免风暴。</li>
 * </ul>
 */
@Slf4j
@Component
public class BadCaseReporter {

    private final ReplayRecorder recorder;
    private final boolean reportOnWarn;
    private final int warnThreshold;
    private final ConcurrentHashMap<String, Instant> lastReport = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> warnCounter = new ConcurrentHashMap<>();

    public BadCaseReporter(ReplayRecorder recorder,
            @Value("${assistant.observability.report-on-warn:false}") boolean reportOnWarn,
            @Value("${assistant.observability.warn-threshold:3}") int warnThreshold) {
        this.recorder = recorder;
        this.reportOnWarn = reportOnWarn;
        this.warnThreshold = warnThreshold;
    }

    @EventListener
    public void onRetrievalAssert(RetrievalAssertEvent event) {
        recorder.appendReplay(event);

        AssertStatus status = event.assertion().status();
        if (status == AssertStatus.PASS) {
            return;
        }
        if (status == AssertStatus.FAIL) {
            report(event, "检索为空");
            return;
        }
        // WARN
        if (reportOnWarn) {
            report(event, "注入条数越界");
            return;
        }
        String key = event.chatId() + ":注入条数越界";
        int count = warnCounter.merge(key, 1, Integer::sum);
        if (count >= warnThreshold) {
            warnCounter.remove(key);
            report(event, "注入条数越界(累计" + count + "次)");
        }
    }

    private void report(RetrievalAssertEvent event, String category) {
        String dedupKey = event.chatId() + ":" + category;
        Instant now = Instant.now();
        Instant last = lastReport.putIfAbsent(dedupKey, now);
        if (last != null && now.toEpochMilli() - last.toEpochMilli() < 60_000) {
            return;
        }
        lastReport.put(dedupKey, now);

        BadCaseRecord record = new BadCaseRecord(
                UUID.randomUUID().toString(),
                event.traceId(),
                event.chatId(),
                event.layer(),
                event.assertion().status(),
                category,
                event.assertion().message(),
                now);
        recorder.appendBadCase(record);
        log.warn("上报 BadCase: traceId={} chatId={} category={} status={}",
                event.traceId(), event.chatId(), category, event.assertion().status());
    }
}
