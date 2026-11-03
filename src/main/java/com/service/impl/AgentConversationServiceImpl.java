package com.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dao.AgentConversationDao;
import com.entity.AgentConversationEntity;
import com.security.ForbiddenException;
import com.service.AgentConversationService;
import com.service.AgentMessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 会话服务实现（Phase 4 · Step ⑤）。
 *
 * <p><b>归属校验语义（用户裁定 D2）</b>：不存在 ⇒ 403；不属于当前学生 ⇒ 403。
 * 统一 403 而不是 404，避免通过状态码差异探测会话是否存在。
 */
@Service
public class AgentConversationServiceImpl
        extends ServiceImpl<AgentConversationDao, AgentConversationEntity>
        implements AgentConversationService {

    private static final int TITLE_MAX = 20;

    private final AgentMessageService messageService;

    public AgentConversationServiceImpl(AgentMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public Long create(Integer studentId, String titleSeed) {
        AgentConversationEntity entity = new AgentConversationEntity();
        entity.setStudentId(studentId);
        entity.setTitle(titleSeed == null ? "" : titleSeed.substring(0, Math.min(TITLE_MAX, titleSeed.length())));
        entity.setCreatedAt(new Date());
        save(entity);
        return entity.getId();
    }

    @Override
    public AgentConversationEntity requireOwned(Long id, Integer studentId) {
        AgentConversationEntity entity = getById(id);
        if (entity == null || studentId == null || !studentId.equals(entity.getStudentId())) {
            // 不存在与「不属于我」统一 403：避免通过状态码差异探测会话存在性
            throw new ForbiddenException();
        }
        return entity;
    }

    @Override
    public List<Map<String, Object>> listMine(Integer studentId) {
        List<AgentConversationEntity> rows = lambdaQuery()
                .eq(AgentConversationEntity::getStudentId, studentId)
                .orderByDesc(AgentConversationEntity::getLastMsgAt)
                .orderByDesc(AgentConversationEntity::getId)
                .list();

        // HashMap 而非 Map.of：lastMsgAt 可能为 null（会话创建后从未发消息）
        return rows.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", row.getId());
            item.put("title", row.getTitle());
            item.put("createdAt", row.getCreatedAt());
            item.put("lastMsgAt", row.getLastMsgAt());
            return item;
        }).toList();
    }

    @Override
    @Transactional
    public void deleteOwned(Long id, Integer studentId) {
        requireOwned(id, studentId);
        // 🔴 FK 顺序：必须先删子表消息，再删会话（否则 FK 约束 500）
        messageService.removeByConversation(id);
        removeById(id);
    }
}
