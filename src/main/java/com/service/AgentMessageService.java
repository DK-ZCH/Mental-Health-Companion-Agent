package com.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.entity.AgentMessageEntity;

import java.util.List;
import java.util.Map;

/** Agent 会话消息服务（Phase 4 · Step ⑤） */
public interface AgentMessageService extends IService<AgentMessageEntity> {

    /** 会话内消息（时间正序）；仅含 user / assistant */
    List<Map<String, Object>> listByConversation(Long conversationId);

    /** 删除会话内全部消息（物理删除） */
    void removeByConversation(Long conversationId);
}
