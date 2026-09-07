package com.aiassistant.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 日志重放记录器：把观测对象（断言事件 / BadCase 记录）序列化为 JSON 后，
 * 通过<b>单线程 + 有界队列</b>异步追加到按天切分的 jsonl 文件，避免阻塞主链路。
 *
 * <p>落盘目录结构（默认基于 {@code assistant.observability.dir=./data}）：
 * <pre>
 *   data/replay/replay-yyyy-MM-dd.jsonl
 *   data/badcase/badcase-yyyy-MM-dd.jsonl
 * </pre>
 */
@Slf4j
@Component
public class ReplayRecorder {

    private final ObjectMapper objectMapper;
    private final File replayDir;
    private final File badcaseDir;
    private final BlockingQueue<WriteTask> queue = new LinkedBlockingQueue<>(10000);
    private final ExecutorService writer;

    public ReplayRecorder(ObjectMapper objectMapper,
            @Value("${assistant.observability.dir:./data}") String baseDir) {
        this.objectMapper = objectMapper;
        this.replayDir = new File(baseDir, "replay");
        this.badcaseDir = new File(baseDir, "badcase");
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "observability-writer");
            t.setDaemon(true);
            return t;
        });
        this.writer.submit(this::drainLoop);
    }

    public void appendReplay(Object payload) {
        enqueue(new WriteTask(fileFor(replayDir, "replay"), serialize(payload)));
    }

    public void appendBadCase(Object payload) {
        enqueue(new WriteTask(fileFor(badcaseDir, "badcase"), serialize(payload)));
    }

    private void enqueue(WriteTask task) {
        if (task == null || task.line() == null) {
            return;
        }
        if (!queue.offer(task)) {
            log.warn("观测写入队列已满，丢弃一条记录");
        }
    }

    private void drainLoop() {
        while (true) {
            try {
                WriteTask task = queue.take();
                task.write();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("写入观测记录失败: {}", e.getMessage());
            }
        }
    }

    private File fileFor(File dir, String prefix) {
        return new File(dir, prefix + "-" + LocalDate.now() + ".jsonl");
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("序列化观测记录失败: {}", e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        writer.shutdownNow();
    }

    private record WriteTask(File file, String line) {
        void write() throws Exception {
            Path dir = file.getParentFile().toPath();
            Files.createDirectories(dir);
            Files.write(file.toPath(), (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }
}
