package com.agent;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 带「读取侧窗口」的 {@link ChatMemory}（Phase 4 · Step ⑤）。
 *
 * <p><b>为什么不用 {@code MessageWindowChatMemory}</b>（Spike 事实 #5，T9 实测）：
 * 1.1.8 的它只在<b>写入侧</b>裁剪（add → trim → saveAll），
 * {@code get()} 读取时<b>不裁剪</b> ⇒ 库里已超过窗口的历史会整段进入上下文。
 *
 * <p><b>本实现的语义（与用户冻结原则一致）</b>：
 * <ul>
 *   <li>{@code add}：把新消息<b>追加</b>进完整历史（<b>不裁剪</b>）⇒
 *       {@code agent_message} 始终是完整聊天历史；</li>
 *   <li>{@code get}：返回<b>最近 maxMessages 条</b> ⇒ 模型上下文只拿窗口；</li>
 *   <li>{@code clear}：删除会话全部消息。</li>
 * </ul>
 *
 * <p>⇒ 「窗口从 20 改 30」只是改这里的 maxMessages，数据库历史不受影响。
 */
public class AgentWindowChatMemory implements ChatMemory {

    private final ChatMemoryRepository repository;
    private final int maxMessages;

    public AgentWindowChatMemory(ChatMemoryRepository repository, int maxMessages) {
        this.repository = repository;
        this.maxMessages = maxMessages <= 0 ? 1 : maxMessages;
    }

    @Override
    public void add(String conversationId, Message message) {
        add(conversationId, List.of(message));
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        // 完整历史追加：不裁剪（窗口只在读取侧生效）
        List<Message> all = new ArrayList<>(repository.findByConversationId(conversationId));
        all.addAll(messages);
        repository.saveAll(conversationId, all);
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> all = repository.findByConversationId(conversationId);
        if (all.size() <= maxMessages) {
            return all;
        }
        return all.subList(all.size() - maxMessages, all.size());
    }

    @Override
    public void clear(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }
}
