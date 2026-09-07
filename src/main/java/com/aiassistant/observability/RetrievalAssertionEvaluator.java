package com.aiassistant.observability;

import com.aiassistant.advisor.RetrievalAssertAdvisor.AssertStatus;
import com.aiassistant.advisor.RetrievalAssertAdvisor.RetrievalAssertion;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 检索节点断言计算器：把"非空 + 注入条数区间"的判断逻辑从 Advisor 中抽出，
 * 供 Advisor（在线）与 ReplayController（离线重放）复用，保证两处口径一致。
 */
public final class RetrievalAssertionEvaluator {

    private RetrievalAssertionEvaluator() {
    }

    public static RetrievalAssertion evaluate(List<Document> retrieved, int minInjected, int maxInjected) {
        int count = retrieved.size();
        boolean nonEmpty = count > 0;

        String message;
        AssertStatus status;
        if (!nonEmpty) {
            message = "检索为空，未命中任何文档（知识库无相关内容或全部被相似度阈值过滤）";
            status = AssertStatus.FAIL;
        } else if (count < minInjected) {
            message = "注入条数 " + count + " 低于下限 " + minInjected + "，召回可能不足";
            status = AssertStatus.WARN;
        } else if (count > maxInjected) {
            message = "注入条数 " + count + " 超过上限 " + maxInjected + "，可能引入噪声";
            status = AssertStatus.WARN;
        } else {
            message = "检索正常";
            status = AssertStatus.PASS;
        }

        // 当前实现中检索结果即注入结果，二者一致；引入 rerank/二次过滤后二者会分化，故字段拆分保留。
        return new RetrievalAssertion(status, nonEmpty, count, count, minInjected, maxInjected, message);
    }
}
