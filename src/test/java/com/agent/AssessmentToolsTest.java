package com.agent;

import com.service.AssessmentPaperService;
import com.service.AssessmentRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S2 / Phase 4 - Step 11：{@link AssessmentTools} 单元测试（真实 DB，不调用任何 LLM）。
 *
 * <p><b>为什么必须用真实 DB</b>：本类验证的是「身份 → 数据范围」这条链，
 * mock Service 会让「是否真的只查了本人数据」变得不可验证。
 *
 * <p><b>注入 test-only key</b>：仅为让 Spring 上下文可创建
 * （{@code DeepSeekChatModel} 自动装配需要 api-key）；本类<b>不会</b>发起任何真实请求。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@DisplayName("Step 11 AssessmentTools")
class AssessmentToolsTest {

    /** 种子数据：student 1 (a1) 有 1 条测评记录（试卷1，15/100） */
    private static final int STUDENT_A1_ID = 1;
    /** 种子数据：student 2 (a2) 无测评记录 */
    private static final int STUDENT_A2_ID = 2;

    @Autowired
    private AssessmentRecordService recordService;

    @Autowired
    private AssessmentPaperService paperService;

    @Autowired
    private Environment environment;

    private AssessmentTools tools;

    /** 纯 POJO：直接 new，不经容器 —— 这正是 {@code com.config.AgentConfig} 不标 {@code @Component} 的收益 */
    @BeforeEach
    void setUp() {
        tools = new AssessmentTools(recordService, paperService);
    }

    // ================== fail-closed ==================

    @Test
    @DisplayName("1. toolContext == null → 抛异常终止（安全失败不得作为正常结果回喂模型）")
    void nullToolContext_throws() {
        assertThatThrownBy(() -> tools.getMyAssessmentRecords(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity");
    }

    @Test
    @DisplayName("2. ToolContext 中缺少 userId → 抛异常")
    void missingUserId_throws() {
        assertThatThrownBy(() -> tools.getMyAssessmentRecords(new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("3. userId 类型不是 Integer（String / Long）→ 抛异常（不做类型强转兜底）")
    void wrongUserIdType_throws() {
        // 域给合法的 student，确保失败原因是【id 类型】而非「缺域」—— 否则本用例测不到目标分支
        String student = AgentIdentity.TABLE_STUDENT;
        assertThatThrownBy(() -> tools.getMyAssessmentRecords(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, "1",
                        AgentIdentity.CTX_USER_TABLE, student))))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> tools.getMyAssessmentRecords(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, 1L,
                        AgentIdentity.CTX_USER_TABLE, student))))
                .isInstanceOf(IllegalStateException.class);
    }

    // ================== Step 12：二元身份（域）fail-closed ==================

    @Test
    @DisplayName("3.1 【Step 12】有 userId 但缺 userTable → 抛异常（绝不默认当学生）")
    void missingUserTable_throws() {
        assertThatThrownBy(() -> tools.getMyAssessmentRecords(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, STUDENT_A1_ID))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity");
    }

    @Test
    @DisplayName("3.2 【Step 12】userTable 为空白 → 抛异常")
    void blankUserTable_throws() {
        for (String blank : new String[]{"", "   ", "\t"}) {
            assertThatThrownBy(() -> tools.getMyAssessmentRecords(
                    new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, STUDENT_A1_ID,
                            AgentIdentity.CTX_USER_TABLE, blank))))
                    .as("空白域不得被 trim 后当作合法值：'%s'", blank)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("3.3 【Step 12】userTable 类型不是 String → 抛异常（不做类型强转兜底）")
    void wrongUserTableType_throws() {
        assertThatThrownBy(() -> tools.getMyAssessmentRecords(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, STUDENT_A1_ID,
                        AgentIdentity.CTX_USER_TABLE, 1))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("3.4 🔴【Step 12 核心】userId 合法 + userTable=counselor → 抛异常（防伪造/错配域）")
    void counselorDomain_throws_evenWithValidUserId() {
        // 这是 Step 11 串读缺陷的直接复现入口：
        // counselor id=1 与 student id=1 是【不同的两个人】，
        // 若只校验 userId 而不校验域，就会读到学生 1 的测评记录。
        assertThatThrownBy(() -> tools.getMyAssessmentRecords(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, STUDENT_A1_ID,
                        AgentIdentity.CTX_USER_TABLE, "counselor"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("domain");
    }

    @Test
    @DisplayName("3.5 🔴【Step 12 核心】userId 合法 + userTable=admin_user → 抛异常（管理员默认拒绝）")
    void adminDomain_throws_evenWithValidUserId() {
        assertThatThrownBy(() -> tools.getMyAssessmentRecords(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, STUDENT_A1_ID,
                        AgentIdentity.CTX_USER_TABLE, "admin_user"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("domain");
    }

    @Test
    @DisplayName("3.6 【Step 12】未知域（未来新增 user_type）→ 抛异常（白名单而非黑名单）")
    void unknownDomain_throws() {
        assertThatThrownBy(() -> tools.getMyAssessmentRecords(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, STUDENT_A1_ID,
                        AgentIdentity.CTX_USER_TABLE, "some_future_role"))))
                .isInstanceOf(IllegalStateException.class);
    }

    // ================== 正常查询 ==================

    @Test
    @DisplayName("4. 学生 a1 + student 域 → 1 条记录，试卷1 / 15 分 / 满分 100，未截断")
    void studentA1_seesOwnRecord() {
        AssessmentRecordToolResult result = tools.getMyAssessmentRecords(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, STUDENT_A1_ID,
                        AgentIdentity.CTX_USER_TABLE, AgentIdentity.TABLE_STUDENT)));

        assertThat(result.error()).isNull();
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.truncated()).isFalse();
        assertThat(result.records()).hasSize(1);

        AssessmentRecordView view = result.records().get(0);
        assertThat(view.paperName()).isEqualTo("考试试卷1");
        assertThat(view.score()).isEqualTo(15);
        assertThat(view.paperTotalScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("5. 学生 a2 + student 域 → 空列表 + total=0（不是报错，也不编造）")
    void studentA2_seesNothing() {
        AssessmentRecordToolResult result = tools.getMyAssessmentRecords(
                new ToolContext(Map.of(AgentIdentity.CTX_USER_ID, STUDENT_A2_ID,
                        AgentIdentity.CTX_USER_TABLE, AgentIdentity.TABLE_STUDENT)));

        assertThat(result.error()).isNull();
        assertThat(result.total()).isZero();
        assertThat(result.records()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    // ================== 结构护栏 ==================

    @Test
    @DisplayName("6. 【护栏】Tool 方法签名中不存在任何业务参数（userId 只能来自 ToolContext）")
    void toolMethodsExposeNoBusinessParameters() {
        Method[] toolMethods = Arrays.stream(AssessmentTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .toArray(Method[]::new);

        assertThat(toolMethods).as("至少应有一个 @Tool 方法").isNotEmpty();

        for (Method method : toolMethods) {
            assertThat(method.getParameterTypes())
                    .as("@Tool 方法 %s 的参数只允许 ToolContext（否则模型可填写业务参数）", method.getName())
                    .containsOnly(ToolContext.class);
        }
    }

    @Test
    @DisplayName("7. 【护栏】返回视图不含 id / studentId / 时间 / 内部编号等字段")
    void viewHasNoSensitiveFields() {
        Set<String> names = Arrays.stream(AssessmentRecordView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(names)
                .as("LLM-safe 视图必须坚持最小必要字段")
                .doesNotContain("id", "studentId", "createdAt", "submittedAt", "recordNo");
    }

    @Test
    @DisplayName("8. 【配置护栏】throw-exception-on-error 必须为 true —— 否则安全异常会被回喂模型")
    void toolExceptionPropagationEnabled() {
        // 若该配置被删除或改为 false，本类用例 1–3 仍会「抛异常」通过，
        // 但运行时 Spring AI 会在 Tool 边界把异常转成 ToolResult 回喂模型，
        // 于是安全失败对外表现为 200 + code=0 —— 必须由本断言守住。
        assertThat(environment.getProperty("spring.ai.tools.throw-exception-on-error", Boolean.class))
                .as("缺少该配置时，Tool 抛出的安全异常不会终止 Agent 调用")
                .isTrue();
    }
}
