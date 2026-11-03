package com.config;

import com.agent.AgentDictLabels;
import com.agent.AgentModel;
import com.agent.AgentService;
import com.agent.AppointmentBookingTools;
import com.agent.AppointmentConfirmToken;
import com.agent.AppointmentTools;
import com.agent.AppointmentWriteService;
import com.agent.AssessmentTools;
import com.agent.CounselorCatalogTools;
import com.agent.FavoriteTools;
import com.agent.MessageTools;
import com.agent.SafetyResources;
import com.agent.SafetyRules;
import com.agent.SpringAiAgentModel;
import com.agent.TrendTools;
import com.agent.WrongQuestionTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.AssessmentPaperService;
import com.service.AssessmentQuestionService;
import com.service.CounselingAppointmentService;
import com.service.AssessmentRecordService;
import com.service.AssessmentWrongQuestionService;
import com.service.CounselingAppointmentService;
import com.service.CounselorFavoriteService;
import com.service.CounselorMessageService;
import com.service.CounselorService;
import com.service.SysDictItemService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
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
    /** T5：测评趋势（只读，基于本人记录 + 试卷满分归一化）*/
    @Bean
    public TrendTools trendTools(AssessmentRecordService recordService,
                                 AssessmentPaperService paperService) {
        return new TrendTools(recordService, paperService);
    }

    /** T6：我的收藏（只读，按 counselorId 聚合，白名单字段）*/
    @Bean
    public FavoriteTools favoriteTools(CounselorFavoriteService favoriteService,
                                       CounselorService counselorService,
                                       AgentDictLabels labels) {
        return new FavoriteTools(favoriteService, counselorService, labels);
    }

    /**
     * 安全规则（Round 3 · 用户 Q3 裁定：常量类，不进 DB 字典）。
     *
     * <p>规则变更必须走「代码 + 评审 + 测试」—— 不给线上留「静默放宽安全规则」的入口。
     */
    @Bean
    public SafetyRules safetyRules() {
        return new SafetyRules();
    }

    /**
     * 危机转介资源（Round 3 · 用户 Q2 裁定：第一版无号码）。
     *
     * <p>⚠️ <b>当前是 {@code unconfigured()}</b>：不含任何号码（含 120）。
     * 新增真实资源时，必须改用 {@code SafetyResources.configured(...)}
     * 并填写 {@code verifiedBy / verifiedAt / source}；<b>不得</b>在此处直接硬编码号码。
     */
    @Bean
    public SafetyResources safetyResources() {
        return SafetyResources.unconfigured();
    }

    /**
     * 预约写入边界（Round 4）：Tool 不直接操作 Mapper。
     */
    @Bean
    public AppointmentWriteService appointmentWriteService(CounselingAppointmentService appointmentService) {
        return new AppointmentWriteService(appointmentService);
    }

    /**
     * 确认凭证签发/校验（Round 4）。
     *
     * <p>⚠️ {@code agent.confirm.secret} 未配置时生成随机运行时密钥并 WARN
     * —— <b>WARN 不含密钥本身</b>；重启后旧凭证失效属可接受降级。
     */
    @Bean
    public AppointmentConfirmToken appointmentConfirmToken(
            ObjectMapper objectMapper,
            @Value("${agent.confirm.secret:}") String secret,
            @Value("${agent.confirm.ttl-seconds:300}") int ttlSeconds) {
        return new AppointmentConfirmToken(secret, objectMapper, ttlSeconds);
    }

    @Bean
    public AppointmentBookingTools appointmentBookingTools(AppointmentWriteService appointmentWriteService,
                                                          AppointmentConfirmToken appointmentConfirmToken,
                                                          CounselorService counselorService,
                                                          AgentDictLabels labels) {
        return new AppointmentBookingTools(appointmentWriteService, appointmentConfirmToken,
                counselorService, labels);
    }

    @Bean
    public AgentService agentService(AgentModel agentModel,
                                     SafetyRules safetyRules,
                                     SafetyResources safetyResources,
                                     AssessmentTools assessmentTools,
                                     AppointmentTools appointmentTools,
                                     MessageTools messageTools,
                                     WrongQuestionTools wrongQuestionTools,
                                     CounselorCatalogTools counselorCatalogTools,
                                     TrendTools trendTools,
                                     FavoriteTools favoriteTools,
                                     AppointmentBookingTools appointmentBookingTools) {
        return new AgentService(agentModel, safetyRules, safetyResources,
                assessmentTools, appointmentTools, messageTools, wrongQuestionTools, counselorCatalogTools,
                trendTools, favoriteTools, appointmentBookingTools);
    }
}
