package com.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.entity.AgentConversationEntity;

import java.util.List;
import java.util.Map;

/**
 * Agent 会话服务（Phase 4 · Step ⑤）。
 *
 * <p><b>归属是安全核心</b>：所有按 id 的操作都必须经过 {@link #requireOwned}。
 */
public interface AgentConversationService extends IService<AgentConversationEntity> {

    /** 创建会话；标题取首条消息前 20 字 */
    Long create(Integer studentId, String titleSeed);

    /** 校验存在性与归属；不存在或不属于当前学生 ⇒ 403（防 IDOR + 防存在性预言机） */
    AgentConversationEntity requireOwned(Long id, Integer studentId);

    /** 我的会话列表（按最近消息倒序） */
    List<Map<String, Object>> listMine(Integer studentId);

    /** 删除会话（物理删除，含全部消息）；先做归属校验 */
    void deleteOwned(Long id, Integer studentId);
}
