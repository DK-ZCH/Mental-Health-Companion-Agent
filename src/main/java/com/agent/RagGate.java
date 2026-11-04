package com.agent;

/**
 * Phase 4 / Step ⑦：RAG Eligibility Gate（设计冻结 §二A，用户 2026-11-03）。
 *
 * <p><b>职责</b>：在检索<b>之前</b>用<b>确定性规则</b>判定一条 Query 是否允许注入知识上下文。
 * 与 {@link SafetyRules} 同一工程范式：常量类 + 代码评审 + 测试 —— 不用 LLM 判意图、
 * 不新增模型调用、不扩张为 NLP 意图分类器（同义覆盖不足为已知限制，见设计文档）。
 *
 * <p><b>四态标签，行为只有两种</b>：ALLOW_RAG ⇒ 注入；其余三态 ⇒ 不注入。
 * 标签存在价值 = 可审计、可测试（S15–S21）。
 *
 * <p><b>优先级：R2 &gt; R1 &gt; R3 &gt; ALLOW_RAG</b> ——
 * 「我的测评怎么样？顺便判断我是不是抑郁症」不得因命中 R1 绕过诊断边界。
 *
 * <p><b>误判代价不对称</b>：误 DENY 只降回答质量；误 ALLOW 把知识上下文交给人身诊断请求
 * （L2 信任声明仍兜底，但属软约束）⇒ 规则保守方向 = 宁可 DENY。
 *
 * <p><b>🔴 「第一人称」本身不是诊断信号</b>（用户冻结）：
 * 第一人称经历 + 只询问应对方法 ⇒ ALLOW_RAG；明确要求对自身状态诊断/确诊/疾病归因 ⇒ DENY_RAG。
 */
public class RagGate {

    /** Gate 判定结果（标签；除 ALLOW_RAG 外都不注入） */
    public enum Decision {
        /** 公共知识问题：检索并注入（Memory 可共存） */
        ALLOW_RAG,
        /** 自身诊断请求：不注入（System 第 10/11 条边界照常生效；回答引导求助） */
        DENY_RAG,
        /** 个人业务数据查询：不注入；Tool 由 LLM 工具循环自主，照常可用 */
        DEFER_TO_TOOL,
        /** 纯会话指代：不注入；Memory 照常 */
        MEMORY_ONLY
    }

    /** R2：针对自身状态的诊断/确诊/疾病归因请求（优先级最高） */
    private static final String[] SELF_DIAGNOSIS_PATTERNS = {
            "我是不是", "我是得了", "我得了", "我患有", "帮我确诊", "帮我判断我是不是",
            "判断我是不是", "我这个情况是不是", "我是不是有病"
    };

    /** R1：既有 Tool 域的个人业务数据指称 */
    private static final String[] TOOL_DOMAIN_PATTERNS = {
            "我的预约", "我的测评", "我的留言", "我的收藏", "我的趋势", "我的错题"
    };

    /** R3：会话指代 */
    private static final String[] MEMORY_REFERENCE_PATTERNS = {
            "我刚才", "我之前说", "你还记得", "记得我"
    };

    /** R3 排除条件：公共知识诉求标记（出现 ⇒ Memory + RAG 允许共存） */
    private static final String[] KNOWLEDGE_SEEK_MARKERS = {
            "怎么", "如何", "办法", "方法", "缓解", "改善", "怎么办", "为什么", "什么是", "建议"
    };

    /**
     * 判定一条 Query 的 RAG 资格。实现为纯函数（输入→输出，无状态、无 IO），
     * 与 {@code SafetyRules#evaluate} 同范式，可被 M6 变异证伪。
     */
    public Decision evaluate(String query) {
        String q = query == null ? "" : query;
        // R2 最高优先：诊断边界不得被 R1 前缀绕过
        if (containsAny(q, SELF_DIAGNOSIS_PATTERNS)) {
            return Decision.DENY_RAG;
        }
        if (containsAny(q, TOOL_DOMAIN_PATTERNS)) {
            return Decision.DEFER_TO_TOOL;
        }
        boolean memoryReference = containsAny(q, MEMORY_REFERENCE_PATTERNS);
        boolean knowledgeSeek = containsAny(q, KNOWLEDGE_SEEK_MARKERS);
        if (memoryReference && !knowledgeSeek) {
            return Decision.MEMORY_ONLY;
        }
        return Decision.ALLOW_RAG;
    }

    private static boolean containsAny(String text, String[] patterns) {
        for (String p : patterns) {
            if (text.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
