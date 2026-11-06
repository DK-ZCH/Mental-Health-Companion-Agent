package com.regression;

import com.agent.AgentIdentity;
import com.agent.AgentIdentityDeniedException;
import com.agent.AppointmentTools;
import com.agent.CounselorCatalogTools;
import com.agent.FavoriteTools;
import com.agent.MessageTools;
import com.agent.TrendTools;
import com.agent.WrongQuestionTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 / Round 1：T1–T4 的<b>角色域</b>与<b>方法签名契约</b>。
 *
 * <p><b>为什么这是第一优先级测试</b>：Step 11 曾因「只校验 userId、不校验身份域」
 * 导致咨询师读到大学生的心理测评（student.id 与 counselor.id 都是 1/2/3）。
 * 新增 4 个 Tool 若各自实现校验，任何一个写错都会重新打开串读面。
 *
 * <p><b>通过标准</b>：
 * <ul>
 *   <li>counselor / admin_user / 非法上下文 → 四个 Tool <b>全部抛异常</b>（不是返回空结果）；</li>
 *   <li>student 域 → 正常返回；</li>
 *   <li>四个 {@code @Tool} 方法<b>没有任何身份参数</b>（模型无法填写身份）。</li>
 * </ul>
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@DisplayName("Phase4 Tool 角色域与签名契约")
class Phase4ToolScopeTest {

    private static final int STUDENT_1 = 1;

    /** 禁止出现在 Tool 方法签名中的身份类参数名（防止「由模型指定身份」） */
    private static final List<String> FORBIDDEN_PARAM_NAMES =
            List.of("userId", "userid", "userTable", "studentId", "counselorId", "role");

    @Autowired
    private AppointmentTools appointmentTools;

    @Autowired
    private MessageTools messageTools;

    @Autowired
    private WrongQuestionTools wrongQuestionTools;

    @Autowired
    private CounselorCatalogTools counselorCatalogTools;

    @Autowired
    private TrendTools trendTools;

    @Autowired
    private FavoriteTools favoriteTools;

    // ================== A. 角色域拒绝 ==================

    @Test
    @DisplayName("A1. 🔴 counselor 域调用四个 Tool 全部抛异常（不得降级为按 id 查）")
    void counselorDomainDenied() {
        assertAllToolsThrow(context(1, "counselor"), "counselor");
    }

    @Test
    @DisplayName("A2. 🔴 admin_user 域调用四个 Tool 全部抛异常（admin id 不得映射成 studentId）")
    void adminDomainDenied() {
        assertAllToolsThrow(context(1, "admin_user"), "admin_user");
    }

    @Test
    @DisplayName("A3. 🔴 上下文缺失/类型错误/域空白一律抛异常")
    void invalidContextDenied() {
        assertAllToolsThrow(null, "toolContext=null");
        assertAllToolsThrow(new ToolContext(Map.of()), "缺少两个键");
        assertAllToolsThrow(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, "1",
                        AgentIdentity.CTX_USER_TABLE, "student")),
                "userId 是 String");
        assertAllToolsThrow(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, 1,
                        AgentIdentity.CTX_USER_TABLE, "   ")),
                "userTable 为空白");
    }

    @Test
    @DisplayName("A3b. 【Step 23 定点修正】真实 Tool 身份拒绝路径抛专用子类型（父类契约不变）")
    void identityDenialUsesDedicatedSubtype() {
        ToolContext denied = context(1, "counselor");
        ToolContext missing = new ToolContext(Map.of());

        // AgentToolGuard.requireStudent 的真实抛点：域不符 + 上下文非法
        assertThatThrownBy(() -> messageTools.getMyMessages(denied))
                .isInstanceOf(AgentIdentityDeniedException.class)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> appointmentTools.getMyAppointments(missing))
                .isInstanceOf(AgentIdentityDeniedException.class)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> wrongQuestionTools.getMyWrongQuestionSummary(denied))
                .isInstanceOf(AgentIdentityDeniedException.class);
        assertThatThrownBy(() -> counselorCatalogTools.listAvailableCounselors(denied))
                .isInstanceOf(AgentIdentityDeniedException.class);
        assertThatThrownBy(() -> trendTools.getMyAssessmentTrend(denied))
                .isInstanceOf(AgentIdentityDeniedException.class);
        assertThatThrownBy(() -> favoriteTools.getMyFavorites(denied))
                .isInstanceOf(AgentIdentityDeniedException.class);
    }

    @Test
    @DisplayName("A4. ✅ student 域调用四个 Tool 均正常返回（不抛异常）")
    void studentDomainAllowed() {
        ToolContext context = context(STUDENT_1, "student");

        assertThatCode(() -> appointmentTools.getMyAppointments(context)).doesNotThrowAnyException();
        assertThatCode(() -> messageTools.getMyMessages(context)).doesNotThrowAnyException();
        assertThatCode(() -> wrongQuestionTools.getMyWrongQuestionSummary(context)).doesNotThrowAnyException();
        assertThatCode(() -> counselorCatalogTools.listAvailableCounselors(context)).doesNotThrowAnyException();
        assertThatCode(() -> trendTools.getMyAssessmentTrend(context)).doesNotThrowAnyException();
        assertThatCode(() -> favoriteTools.getMyFavorites(context)).doesNotThrowAnyException();
    }

    // ================== G. 签名契约 ==================

    @Test
    @DisplayName("G1. 🔴 四个 @Tool 方法签名只接受 ToolContext，无任何身份参数")
    void signaturesCarryNoIdentityParameter() throws Exception {
        assertSignature(AppointmentTools.class.getMethod("getMyAppointments", ToolContext.class));
        assertSignature(MessageTools.class.getMethod("getMyMessages", ToolContext.class));
        assertSignature(WrongQuestionTools.class.getMethod("getMyWrongQuestionSummary", ToolContext.class));
        assertSignature(CounselorCatalogTools.class.getMethod("listAvailableCounselors", ToolContext.class));
        assertSignature(TrendTools.class.getMethod("getMyAssessmentTrend", ToolContext.class));
        assertSignature(FavoriteTools.class.getMethod("getMyFavorites", ToolContext.class));
    }

    @Test
    @DisplayName("G2. 四个 Tool 类均为纯 POJO（无 Spring 注解），由 AgentConfig 装配")
    void toolsArePlainPojos() {
        for (Class<?> type : List.of(AppointmentTools.class, MessageTools.class,
                WrongQuestionTools.class, CounselorCatalogTools.class,
                TrendTools.class, FavoriteTools.class)) {
            assertThat(type.getAnnotations())
                    .as("%s 不应带 Spring 注解（保持可 new 出来做单测）", type.getSimpleName())
                    .isEmpty();
        }
    }

    // ================== helpers ==================

    private static ToolContext context(int userId, String userTable) {
        return new ToolContext(Map.of(
                AgentIdentity.CTX_USER_ID, userId,
                AgentIdentity.CTX_USER_TABLE, userTable));
    }

    /** 四个 Tool 必须同时拒绝；只拒绝一两个说明校验被漏写 */
    private void assertAllToolsThrow(ToolContext context, String label) {
        assertThatThrownBy(() -> appointmentTools.getMyAppointments(context))
                .as("%s → T1 必须抛异常（fail-closed）", label)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> messageTools.getMyMessages(context))
                .as("%s → T2 必须抛异常（fail-closed）", label)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> wrongQuestionTools.getMyWrongQuestionSummary(context))
                .as("%s → T3 必须抛异常（fail-closed）", label)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> counselorCatalogTools.listAvailableCounselors(context))
                .as("%s → T4 必须抛异常（fail-closed）", label)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> trendTools.getMyAssessmentTrend(context))
                .as("%s → T5 必须抛异常（fail-closed）", label)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> favoriteTools.getMyFavorites(context))
                .as("%s → T6 必须抛异常（fail-closed）", label)
                .isInstanceOf(IllegalStateException.class);
    }

    private void assertSignature(Method method) {
        assertThat(method.isAnnotationPresent(Tool.class))
                .as("%s 必须标注 @Tool", method.getName())
                .isTrue();

        Parameter[] parameters = method.getParameters();
        assertThat(parameters)
                .as("%s 只应接受一个 ToolContext 参数", method.getName())
                .hasSize(1);
        assertThat(parameters[0].getType())
                .as("%s 的参数类型必须是 ToolContext（框架注入，不进模型视野）", method.getName())
                .isEqualTo(ToolContext.class);

        for (Parameter parameter : parameters) {
            String name = parameter.getName();
            assertThat(FORBIDDEN_PARAM_NAMES)
                    .as("%s 的参数名 %s 不得是身份字段", method.getName(), name)
                    .doesNotContain(name);
        }
    }
}
