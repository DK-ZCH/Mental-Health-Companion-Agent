package com.config;

import com.agent.AgentDictLabels;
import com.agent.AgentModel;
import com.agent.AgentService;
import com.agent.AppointmentTools;
import com.agent.AssessmentTools;
import com.agent.CounselorCatalogTools;
import com.agent.MessageTools;
import com.agent.SpringAiAgentModel;
import com.agent.WrongQuestionTools;
import com.service.AssessmentPaperService;
import com.service.AssessmentQuestionService;
import com.service.AssessmentRecordService;
import com.service.AssessmentWrongQuestionService;
import com.service.CounselingAppointmentService;
import com.service.CounselorMessageService;
import com.service.CounselorService;
import com.service.SysDictItemService;
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

    /** 字典标签翻译（T1 的时段/状态、T4 的性别）；按 dict_code 缓存一次查询 */
    @Bean
    public AgentDictLabels agentDictLabels(SysDictItemService dictItemService) {
        return new AgentDictLabels(dictItemService);
    }

    @Bean
    public AppointmentTools appointmentTools(CounselingAppointmentService appointmentService,
                                             CounselorService counselorService,
                                             AgentDictLabels labels) {
        return new AppointmentTools(appointmentService, counselorService, labels);
    }

    @Bean
    public MessageTools messageTools(CounselorMessageService messageService,
                                     CounselorService counselorService) {
        return new MessageTools(messageService, counselorService);
    }

    @Bean
    public WrongQuestionTools wrongQuestionTools(AssessmentWrongQuestionService wrongQuestionService,
                                                 AssessmentQuestionService questionService,
                                                 AssessmentPaperService paperService) {
        return new WrongQuestionTools(wrongQuestionService, questionService, paperService);
    }

    @Bean
    public CounselorCatalogTools counselorCatalogTools(CounselorService counselorService,
                                                       AgentDictLabels labels) {
        return new CounselorCatalogTools(counselorService, labels);
    }

    /**
     * Agent 服务：注入<b>全部</b>本轮 Tool。
     *
     * <p>新增 Tool 时在此追加一个参数即可；域校验收在 {@code AgentToolGuard}，不在此处过滤。
     */
    @Bean
    public AgentService agentService(AgentModel agentModel,
                                     AssessmentTools assessmentTools,
                                     AppointmentTools appointmentTools,
                                     MessageTools messageTools,
                                     WrongQuestionTools wrongQuestionTools,
                                     CounselorCatalogTools counselorCatalogTools) {
        return new AgentService(agentModel,
                assessmentTools, appointmentTools, messageTools, wrongQuestionTools, counselorCatalogTools);
    }
}
