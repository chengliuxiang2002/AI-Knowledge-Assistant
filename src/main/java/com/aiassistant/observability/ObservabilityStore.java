package com.aiassistant.observability;

import com.aiassistant.advisor.RetrievalAssertAdvisor.RetrievalAssertion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 观测数据读取层：统一从 replay / badcase 的 jsonl 文件中还原现场，
 * 供重放端点与 BadCase 固化（promote）复用，避免各处重复读目录逻辑。
 */
@Component
public class ObservabilityStore {

    private final ObjectMapper objectMapper;
    private final File replayDir;
    private final File badcaseDir;

    public ObservabilityStore(ObjectMapper objectMapper,
            @Value("${assistant.observability.dir:./data}") String baseDir) {
        this.objectMapper = objectMapper;
        this.replayDir = new File(baseDir, "replay");
        this.badcaseDir = new File(baseDir, "badcase");
    }

    /**
     * 从 replay 中还原指定 traceId 的检索现场（查询文本 + 当时断言）。
     */
    public Optional<ReplaySnapshot> findReplaySnapshot(String traceId) {
        for (File file : listJsonl(replayDir)) {
            for (String line : readLines(file)) {
                try {
                    JsonNode node = objectMapper.readTree(line);
                    if (!traceId.equals(node.path("traceId").asText())) {
                        continue;
                    }
                    if (!"retrieval-assert".equals(node.path("layer").asText())) {
                        continue;
                    }
                    String query = node.path("query").asText();
                    RetrievalAssertion assertion =
                            objectMapper.treeToValue(node.path("assertion"), RetrievalAssertion.class);
                    return Optional.of(new ReplaySnapshot(query, assertion));
                } catch (Exception e) {
                    // 跳过解析失败的行，继续扫描
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 读取指定 traceId 的 BadCase（用于 promote 时回填归因类别）。
     */
    public Optional<BadCaseRecord> findBadCase(String traceId) {
        return listBadCases().stream()
                .filter(b -> traceId.equals(b.traceId()))
                .findFirst();
    }

    public List<BadCaseRecord> listBadCases() {
        List<BadCaseRecord> result = new ArrayList<>();
        for (File file : listJsonl(badcaseDir)) {
            for (String line : readLines(file)) {
                try {
                    result.add(objectMapper.readValue(line, BadCaseRecord.class));
                } catch (Exception e) {
                    // 跳过解析失败的行
                }
            }
        }
        return result;
    }

    private List<File> listJsonl(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jsonl"));
        return files == null ? List.of() : List.of(files);
    }

    private List<String> readLines(File file) {
        try {
            return Files.readAllLines(file.toPath());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 一次检索现场的还原结果。
     */
    public record ReplaySnapshot(String query, RetrievalAssertion assertion) {
    }
}
