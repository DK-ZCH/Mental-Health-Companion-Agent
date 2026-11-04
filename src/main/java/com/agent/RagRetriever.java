package com.agent;

import java.util.List;

/**
 * Phase 4 / Step ⑦（⑦a）：知识检索接口 —— <b>可替换</b>是本设计的硬要求。
 *
 * <p><b>双阶段路线</b>（设计冻结 §五，用户裁定 D2）：
 * <ul>
 *   <li>⑦a：{@link KeywordKnowledgeRetriever}（零新依赖）—— 跑通链路 + 信任边界；</li>
 *   <li>⑦b：向量实现（SimpleVectorStore + embedding）—— <b>必须</b>先有 ⑦a 的
 *       「同义表达漏检」失败证据，才允许立项；届时只需提供本接口的新实现并替换装配。</li>
 * </ul>
 *
 * <p><b>信任边界</b>：实现只做「文本进、文本出」，<b>无工具执行权、无身份、无副作用</b>；
 * 返回片段将由 {@code AgentService} 以独立 {@code KNOWLEDGE_CONTEXT} 消息段注入
 * （永不进入 System 正文）。
 */
public interface RagRetriever {

    /** 检索到的单篇片段（content 已按 ⑦a 上限截断） */
    record RagSnippet(long id, String title, String content) {
    }

    /**
     * 按用户 Query 检索语料。返回有序片段（topK 由实现保证 ≤2）；
     * 无命中 ⇒ 返回空列表（调用方据此<b>不注入</b>任何 context 段）。
     */
    List<RagSnippet> retrieve(String query);
}
