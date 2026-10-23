package com.config;

import com.agent.AgentModel;
import com.agent.AgentService;
import com.agent.AssessmentTools;
import com.agent.SpringAiAgentModel;
import com.service.AssessmentPaperService;
import com.service.AssessmentRecordService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 装配（Phase 4 / Step 11）。
 *
 * <p><b>为什么显式声明 Bean 而不是 {@code @Component} 扫描</b>：
 * {@link AssessmentTools} / {@link AgentService} 保持<b>纯 POJO</b>（无 Spring 注解），
 * 从而可以在单元测试中直接 {@code new} 出来，不受容器生命周期与代理影响。
 *
 * <p><b>{@code ChatModel} 来源</b>：{@code spring-ai-starter-model-deepseek} 自动装配
 * {@code DeepSeekChatModel}（实现 {@code ChatModel}）。此处注入接口而非具体厂商类，
 * 上层不感知 DeepSeek。
 */
@Configuration
public class AgentConfig {

    @Bean
    public AgentModel agentModel(ChatModel chatModel) {
        return new SpringAiAgentModel(ChatClient.builder(chatModel).build());
    }

    @Bean
    public AssessmentTools assessmentTools(AssessmentRecordService recordService,
                                           AssessmentPaperService paperService) {
        return new AssessmentTools(recordService, paperService);
    }

    @Bean
    public AgentService agentService(AgentModel agentModel, AssessmentTools assessmentTools) {
        return new AgentService(agentModel, assessmentTools);
    }
}
