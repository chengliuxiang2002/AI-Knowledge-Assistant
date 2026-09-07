package com.aiassistant.observability;

import com.aiassistant.advisor.RetrievalAssertAdvisor.AssertStatus;
import com.aiassistant.advisor.RetrievalAssertAdvisor.RetrievalAssertion;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * BadCase 固化与回归端点，完成"断言 → 上报 → 重放 → 固化 → 回归"闭环的最后两步。
 * <ul>
 *   <li>{@code POST /promote}：把指定 traceId 的 BadCase 固化为 golden 回归用例（期望 PASS）；</li>
 *   <li>{@code GET /golden}：列出全部 golden 用例；</li>
 *   <li>{@code POST /regression}：批量重放 golden 用例，统计通过率。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/debug")
public class PromoteController {

    private final ObservabilityStore observabilityStore;
    private final GoldenCaseStore goldenCaseStore;
    private final VectorStore vectorStore;

    public PromoteController(ObservabilityStore observabilityStore,
            GoldenCaseStore goldenCaseStore,
            VectorStore vectorStore) {
        this.observabilityStore = observabilityStore;
        this.goldenCaseStore = goldenCaseStore;
        this.vectorStore = vectorStore;
    }

    /**
     * 固化 BadCase 为 golden 回归用例。
     */
    @PostMapping("/promote")
    public Map<String, Object> promote(@RequestParam String traceId,
            @RequestParam(required = false) String note) {
        Optional<ObservabilityStore.ReplaySnapshot> snapshotOpt = observabilityStore.findReplaySnapshot(traceId);
        if (snapshotOpt.isEmpty()) {
            return Map.of("traceId", traceId, "promoted", false,
                    "message", "未找到该 traceId 的检索断言行");
        }
        ObservabilityStore.ReplaySnapshot snapshot = snapshotOpt.get();
        String category = observabilityStore.findBadCase(traceId)
                .map(BadCaseRecord::category)
                .orElse("manual");

        GoldenCase goldenCase = new GoldenCase(
                UUID.randomUUID().toString(),
                traceId,
                snapshot.query(),
                category,
                AssertStatus.PASS.name(),
                note == null ? "" : note,
                Instant.now());
        GoldenCase saved = goldenCaseStore.save(goldenCase);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", traceId);
        result.put("promoted", true);
        result.put("golden", saved);
        return result;
    }

    @GetMapping("/golden")
    public List<GoldenCase> listGolden() {
        return goldenCaseStore.findAll();
    }

    /**
     * 批量回归：对每个 golden 用例用当前代码重检索 + 断言，判断是否达到期望状态。
     */
    @PostMapping("/regression")
    public Map<String, Object> regression(@RequestParam(required = false) Integer topK) {
        List<GoldenCase> cases = goldenCaseStore.findAll();
        int k = topK != null ? topK : 4;

        int passed = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (GoldenCase gc : cases) {
            List<Document> retrieved = vectorStore.similaritySearch(
                    SearchRequest.builder().query(gc.query()).topK(k).build());
            RetrievalAssertion assertion = RetrievalAssertionEvaluator.evaluate(retrieved, 1, k);
            boolean ok = gc.expectedStatus().equals(assertion.status().name());
            if (ok) {
                passed++;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("goldenId", gc.id());
            item.put("query", gc.query());
            item.put("expectedStatus", gc.expectedStatus());
            item.put("actualStatus", assertion.status().name());
            item.put("passed", ok);
            item.put("assertion", assertion);
            results.add(item);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", cases.size());
        summary.put("passed", passed);
        summary.put("failed", cases.size() - passed);
        summary.put("passRate", cases.isEmpty() ? 1.0 : (double) passed / cases.size());
        summary.put("topK", k);
        summary.put("results", results);
        return summary;
    }
}
