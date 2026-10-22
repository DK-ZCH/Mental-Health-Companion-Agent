package com.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S2-F-4-1：{@link PasswordHasher} 单元测试（纯静态方法，无 Spring / 无 DB）。
 *
 * <p><b>覆盖重点</b>（这些分支无法通过真实 HTTP 构造，只能单测）：
 * <ul>
 *   <li>fail-closed：空凭据一律 false，且先于任何比较；</li>
 *   <li><b>用例 10</b>：{@code $} 开头的历史明文必须可登录（防误杀）；</li>
 *   <li><b>用例 11</b>：形似 BCrypt 的非法值<b>不得</b>回退明文比较（防认证绕过）。</li>
 * </ul>
 *
 * <p>实测依据（Hutool 5.8.25，见 S2-F-4-1 报告）：{@code $2a$/$2b$/$2x$/$2y$} 均支持；
 * {@code $2$} 不支持；非法形态返回 {@code false} 而非抛异常；
 * {@code checkpw("123456","123456")} 为 {@code false} ⇒ 明文兼容必须显式 equals。
 */
@DisplayName("S2-F-4-1 PasswordHasher")
class PasswordHasherTest {

    private static final String RAW = "123456";

    // ================== hash() ==================

    @Test
    @DisplayName("1. hash(null) 抛 IllegalArgumentException（不静默生成空密码哈希）")
    void hash_null_throws() {
        assertThatThrownBy(() -> PasswordHasher.hash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("2. hash(\"\") 抛 IllegalArgumentException（防制造弱口令）")
    void hash_empty_throws() {
        assertThatThrownBy(() -> PasswordHasher.hash(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("3. hash 产出 60 字符、$2 开头、且两次结果不同（随机盐）")
    void hash_producesBcryptWithRandomSalt() {
        String h1 = PasswordHasher.hash(RAW);
        String h2 = PasswordHasher.hash(RAW);

        assertThat(h1).hasSize(60).startsWith("$2");
        assertThat(h2).hasSize(60).startsWith("$2");
        assertThat(h1).as("随机盐：同一密码两次哈希必须不同").isNotEqualTo(h2);
    }

    // ================== matches()：fail-closed ==================

    @Test
    @DisplayName("4. matches(null, 任意) → false（空凭据不参与认证）")
    void matches_nullRaw_false() {
        assertThat(PasswordHasher.matches(null, PasswordHasher.hash(RAW))).isFalse();
        assertThat(PasswordHasher.matches(null, RAW)).isFalse();
    }

    @Test
    @DisplayName("5. matches(\"\", 任意) → false")
    void matches_emptyRaw_false() {
        assertThat(PasswordHasher.matches("", PasswordHasher.hash(RAW))).isFalse();
        assertThat(PasswordHasher.matches("", "")).isFalse();
    }

    @Test
    @DisplayName("6. matches(任意, null) → false（不 NPE）")
    void matches_nullStored_false() {
        assertThat(PasswordHasher.matches(RAW, null)).isFalse();
    }

    @Test
    @DisplayName("7. matches(任意, \"\") → false（空串不得与空输入意外匹配）")
    void matches_emptyStored_false() {
        assertThat(PasswordHasher.matches(RAW, "")).isFalse();
    }

    // ================== matches()：BCrypt 分支 ==================

    @Test
    @DisplayName("8. 正确密码 vs BCrypt → true")
    void matches_bcryptCorrect_true() {
        assertThat(PasswordHasher.matches(RAW, PasswordHasher.hash(RAW))).isTrue();
    }

    @Test
    @DisplayName("9. 错误密码 vs BCrypt → false")
    void matches_bcryptWrong_false() {
        assertThat(PasswordHasher.matches("wrong", PasswordHasher.hash(RAW))).isFalse();
    }

    // ================== matches()：兼容窗口（关键护栏）==================

    @Test
    @DisplayName("10. 【护栏】$ 开头的历史明文仍可登录（不得被误判为 BCrypt）")
    void matches_dollarPrefixedPlaintext_true() {
        // 若判定条件退化为 startsWith("$")，该账号将被永久锁死
        assertThat(PasswordHasher.matches("$password123", "$password123")).isTrue();
        assertThat(PasswordHasher.matches("$2abc", "$2abc"))
                .as("$2 开头但非 BCrypt 结构：属家族 ⇒ 拒绝（不回退明文）")
                .isFalse();
    }

    @Test
    @DisplayName("11. 【护栏】非法 BCrypt 形态不得回退明文比较（防认证绕过）")
    void matches_malformedBcrypt_noPlaintextFallback() {
        assertThat(PasswordHasher.matches("$2a$10$xxxx", "$2a$10$xxxx"))
                .as("输入与存储值完全相同，但命中 BCrypt 家族 ⇒ 必须 false")
                .isFalse();
        assertThat(PasswordHasher.matches("$2a$10$" + "!".repeat(53), "$2a$10$" + "!".repeat(53)))
                .as("长度 60 但含非法字符 ⇒ 仍必须 false")
                .isFalse();
    }

    @Test
    @DisplayName("12. BCrypt 与明文互不串通：明文密码不得匹配 BCrypt 存储值")
    void matches_crossFormat_false() {
        String hash = PasswordHasher.hash(RAW);
        assertThat(PasswordHasher.matches(hash, hash))
                .as("把哈希当密码提交，不得通过")
                .isFalse();
        assertThat(PasswordHasher.matches(hash, RAW))
                .as("BCrypt 哈希串 vs 明文存储值，不得匹配")
                .isFalse();
    }

    @Test
    @DisplayName("13. 非 BCrypt 家族的 $ 前缀（$1$/$5$/$6$）按明文处理")
    void matches_nonBcryptDollarFamilies_treatedAsPlaintext() {
        assertThat(PasswordHasher.matches("123456", "$1$abc")).isFalse();
        assertThat(PasswordHasher.matches("$1$abc", "$1$abc"))
                .as("本项目无此格式；按明文等值处理")
                .isTrue();
    }
}
