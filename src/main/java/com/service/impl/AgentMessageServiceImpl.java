package com.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dao.AgentMessageDao;
import com.entity.AgentMessageEntity;
import com.service.AgentMessageService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Agent 会话消息服务实现（Phase 4 · Step ⑤） */
@Service
public class AgentMessageServiceImpl
        extends ServiceImpl<AgentMessageDao, AgentMessageEntity>
        implements AgentMessageService {

    @Override
    public List<Map<String, Object>> listByConversation(Long conversationId) {
        List<AgentMessageEntity> rows = lambdaQuery()
                .eq(AgentMessageEntity::getConversationId, conversationId)
                .orderByAsc(AgentMessageEntity::getId)
                .list();

        return rows.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", row.getId());
            item.put("role", row.getRole());
            item.put("content", row.getContent());
            item.put("createdAt", row.getCreatedAt());
            return item;
        }).toList();
    }

    @Override
    public void removeByConversation(Long conversationId) {
        lambdaUpdate()
                .eq(AgentMessageEntity::getConversationId, conversationId)
                .remove();
    }
}
