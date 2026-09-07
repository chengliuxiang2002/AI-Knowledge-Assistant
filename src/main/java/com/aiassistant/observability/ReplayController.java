package com.aiassistant.observability;

import com.aiassistant.advisor.RetrievalAssertAdvisor.RetrievalAssertion;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 日志重放诊断端点：根据 traceId 从 replay jsonl 还原检索现场，用当前代码与
 * 可选覆盖参数（topK / threshold）重新检索+断言，输出"旧断言 vs 新断言 + 证据"对照，
 * 用于定位召回问题与离线验证修复。
 */
@RestController
@RequestMapping("/api/debug")
public class ReplayController {

    private final ObservabilityStore observabilityStore;
    private final VectorStore vectorStore;

    public ReplayController(ObservabilityStore observabilityStore, VectorStore vectorStore) {
        this.observabilityStore = observabilityStore;
        this.vectorStore = vectorStore;
    }

    @GetMapping("/replay")
    public Map<String, Object> replay(@RequestParam String traceId,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) Double threshold) {
        Optional<ObservabilityStore.ReplaySnapshot> snapshotOpt = observabilityStore.findReplaySnapshot(traceId);
        if (snapshotOpt.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("traceId", traceId);
            result.put("found", false);
            result.put("message", "未找到该 traceId 的检索断言行");
            return result;
        }
        ObservabilityStore.ReplaySnapshot snapshot = snapshotOpt.get();

        int k = topK != null ? topK : 4;
        double t = threshold != null ? threshold : 0.0;

        var builder = SearchRequest.builder().query(snapshot.query()).topK(k);
        if (t > 0) {
            builder.similarityThreshold(t);
        }
        List<Document> retrieved = vectorStore.similaritySearch(builder.build());
        RetrievalAssertion newAssertion = RetrievalAssertionEvaluator.evaluate(retrieved, 1, k);

        List<Map<String, Object>> evidence = new ArrayList<>();
        for (Document d : retrieved) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docId", d.getId() == null ? "" : d.getId());
            item.put("score", d.getScore());
            item.put("text", d.getText());
            evidence.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", traceId);
        result.put("found", true);
        result.put("query", snapshot.query());
        result.put("oldAssertion", snapshot.assertion());
        result.put("newAssertion", newAssertion);
        result.put("overrides", Map.of("topK", k, "threshold", t));
        result.put("evidence", evidence);
        return result;
    }
}
