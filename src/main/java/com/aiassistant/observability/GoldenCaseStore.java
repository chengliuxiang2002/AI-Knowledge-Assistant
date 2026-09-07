package com.aiassistant.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Golden 用例存储：负责 golden 回归用例的<b>同步</b>写入与读取。
 * <p>固化（promote）是低频人工操作，同步写可保证固化后立即能被回归端点读到。
 * <p>落盘：{@code data/golden/golden-yyyy-MM-dd.jsonl}
 */
@Component
public class GoldenCaseStore {

    private final ObjectMapper objectMapper;
    private final File goldenDir;

    public GoldenCaseStore(ObjectMapper objectMapper,
            @Value("${assistant.observability.dir:./data}") String baseDir) {
        this.objectMapper = objectMapper;
        this.goldenDir = new File(baseDir, "golden");
    }

    public GoldenCase save(GoldenCase goldenCase) {
        File file = new File(goldenDir, "golden-" + LocalDate.now() + ".jsonl");
        try {
            Path dir = goldenDir.toPath();
            Files.createDirectories(dir);
            String line = objectMapper.writeValueAsString(goldenCase) + System.lineSeparator();
            Files.write(file.toPath(), line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return goldenCase;
        } catch (Exception e) {
            throw new IllegalStateException("保存 golden 用例失败: " + e.getMessage(), e);
        }
    }

    public List<GoldenCase> findAll() {
        List<GoldenCase> result = new ArrayList<>();
        File[] files = goldenDir.listFiles((dir, name) -> name.endsWith(".jsonl"));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            try {
                for (String line : Files.readAllLines(file.toPath())) {
                    if (line.isBlank()) {
                        continue;
                    }
                    result.add(objectMapper.readValue(line, GoldenCase.class));
                }
            } catch (Exception e) {
                // 跳过解析失败的行
            }
        }
        return result;
    }
}
