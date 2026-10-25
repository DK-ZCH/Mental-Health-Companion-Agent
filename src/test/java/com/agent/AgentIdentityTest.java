package com.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 / Step 12：{@link AgentIdentity} 单元测试（纯内存，不启动容器、不连 DB、不调 LLM）。
 *
 * <p><b>本类验证「解析层」</b>：把 {@code ToolContext} 里的原始数据转成可信的二元身份。
 * 「某身份域是否被允许调用某工具」由 {@code AssessmentToolsTest} 验证（那是 Tool 的职责）。
 *
 * <p><b>核心不变式</b>：任何缺失 / 类型污染 / 空白域 → {@code null}
 * （调用方必须 fail-closed，绝不允许默认值兜底）。
 */
@DisplayName("Step 12 AgentIdentity")
class AgentIdentityTest {

    private static final String STUDENT = AgentIdentity.TABLE_STUDENT;

    // ================== 正常解析 ==================

    @Test
    @DisplayName("1. 合法二元身份（student）→ 解析成功")
    void validStudent() {
        AgentIdentity identity = AgentIdentity.from(ctx(1, STUDENT));

        assertThat(identity).isNotNull();
        assertThat(identity.userId()).isEqualTo(1);
        assertThat(identity.userTable()).isEqualTo(STUDENT);
        assertThat(identity.isStudent()).isTrue();
    }

    @Test
    @DisplayName("2. 非 student 域在【解析层】也可成功 —— 域是否允许由 Tool 判断，不在本层拒绝")
    void nonStudentDomainStillParses() {
        // 解析与授权是两件事：本层只保证「数据形态合法」，
        // 避免把业务规则（哪个域能用哪个工具）塞进值对象。
        AgentIdentity counselor = AgentIdentity.from(ctx(1, "counselor"));
        assertThat(counselor).isNotNull();
        assertThat(counselor.userTable()).isEqualTo("counselor");
        assertThat(counselor.isStudent()).isFalse();

        AgentIdentity admin = AgentIdentity.from(ctx(1, "admin_user"));
        assertThat(admin).isNotNull();
        assertThat(admin.isStudent()).isFalse();
    }

    @Test
    @DisplayName("3. userTable 前后含空白 → trim 后正常解析")
    void userTableTrimmed() {
        AgentIdentity identity = AgentIdentity.from(ctx(7, "  student  "));

        assertThat(identity).isNotNull();
        assertThat(identity.userTable()).isEqualTo(STUDENT);
    }

    // ================== fail-closed（返回 null） ==================

    @Test
    @DisplayName("4. toolContext == null → null")
    void nullContext() {
        assertThat(AgentIdentity.from(null)).isNull();
    }

    @Test
    @DisplayName("5. 空 Map（无任何键）→ null")
    void emptyContext() {
        // ⚠️ 不测 `new ToolContext(null)`：Spring AI 构造器内部调用
        //    Collections.unmodifiableMap(m)，传 null 会直接 NPE ⇒ 该状态【不可达】。
        //    AgentIdentity.from 中 `context == null` 仅为防御性判断（框架行为若变化也不致 NPE）。
        assertThat(AgentIdentity.from(new ToolContext(Map.of()))).isNull();
    }

    @Test
    @DisplayName("6. 缺少 userId → null（不得默认任何 id）")
    void missingUserId() {
        assertThat(AgentIdentity.from(new ToolContext(Map.of()))).isNull();
        assertThat(AgentIdentity.from(new ToolContext(Map.of(
                AgentIdentity.CTX_USER_TABLE, STUDENT)))).isNull();
    }

    @Test
    @DisplayName("7. 缺少 userTable → null（不得默认当学生）")
    void missingUserTable() {
        assertThat(AgentIdentity.from(new ToolContext(Map.of(
                AgentIdentity.CTX_USER_ID, 1)))).isNull();
    }

    @Test
    @DisplayName("8. userId 类型污染（String / Long / null）→ null（不做强转兜底）")
    void wrongUserIdType() {
        assertThat(AgentIdentity.from(ctx("1", STUDENT))).isNull();
        assertThat(AgentIdentity.from(ctx(1L, STUDENT))).isNull();
        assertThat(AgentIdentity.from(ctx(null, STUDENT))).isNull();
    }

    @Test
    @DisplayName("9. userTable 类型污染（Integer / null）→ null")
    void wrongUserTableType() {
        assertThat(AgentIdentity.from(ctx(1, 1))).isNull();
        assertThat(AgentIdentity.from(ctx(1, null))).isNull();
    }

    @Test
    @DisplayName("10. userTable 为空白（\"\" / 空格 / Tab）→ null")
    void blankUserTable() {
        for (String blank : new String[]{"", "   ", "\t", "\n"}) {
            assertThat(AgentIdentity.from(ctx(1, blank)))
                    .as("空白域不得被当作合法值：'%s'", blank)
                    .isNull();
        }
    }

    // ================== 泄露护栏 ==================

    @Test
    @DisplayName("11. 【护栏】toString 不得输出 userId（防止对象被误打日志时泄露标识）")
    void toStringHidesUserId() {
        AgentIdentity identity = AgentIdentity.from(ctx(123456, STUDENT));

        assertThat(identity).isNotNull();
        assertThat(identity.toString())
                .as("record 默认 toString 会打印全部字段，必须被覆盖")
                .doesNotContain("123456");
        assertThat(identity.toString()).contains(STUDENT);
        // 单测里的显式 getter 仍可拿到（对象本身不隐藏，只是不主动打印）
        assertThat(identity.userId()).isEqualTo(123456);
    }

    // ================== helpers ==================

    /** 构造只含必要键的 ToolContext（允许 value 为 null，故用 HashMap 而非 Map.of） */
    private static ToolContext ctx(Object userId, Object userTable) {
        Map<String, Object> map = new HashMap<>();
        map.put(AgentIdentity.CTX_USER_ID, userId);
        map.put(AgentIdentity.CTX_USER_TABLE, userTable);
        return new ToolContext(map);
    }
}
