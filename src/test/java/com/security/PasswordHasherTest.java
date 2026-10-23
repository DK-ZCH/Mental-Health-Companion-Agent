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
 *   <li><b>用例 11</b>：形似 BCrypt 的非法值<b>不得</b>回退明文比较（防认证绕过）；</li>
 *   <li><b>用例 14</b>：【F-4-3 核心】普通明文存储值即使完全相同也必须拒绝；</li>
 *   <li><b>用例 15/16</b>：cost 限定 04–31，越界直接拒绝、边界内交由 checkpw。</li>
 * </ul>
 *
 * <p>实测依据（Hutool 5.8.25，见 S2-F-4-1 报告）：{@code $2a$/$2b$/$2x$/$2y$} 均支持；
 * {@code $2$} 不支持；非法形态返回 {@code false} 而非抛异常；
 * {@code checkpw("123456","123456")} 为 {@code false} ⇒ 明文兼容必须显式 equals。
 */
@DisplayName("S2-F-4-1 / F-4-3 PasswordHasher")
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
    @DisplayName("10. 【护栏】$ 开头的历史明文不再可登录（F-4-3 已切断明文分支）")
    void matches_dollarPrefixedPlaintext_false() {
        // F-4-1 时此处为 true（兼容窗口）；F-4-3 起 fallback 已删除 ⇒ 必须 false
        assertThat(PasswordHasher.matches("$password123", "$password123"))
                .as("明文 fallback 已删除：即使完全相同也不得通过")
                .isFalse();
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
    @DisplayName("13. 非 BCrypt 家族的 $ 前缀（$1$/$5$/$6$）同样拒绝")
    void matches_nonBcryptDollarFamilies_false() {
        assertThat(PasswordHasher.matches("123456", "$1$abc")).isFalse();
        assertThat(PasswordHasher.matches("$1$abc", "$1$abc"))
                .as("F-4-3 起无明文分支：即使是其他 KDF 格式也不得通过")
                .isFalse();
    }

    // ================== F-4-3：明文 fallback 已删除 ==================

    @Test
    @DisplayName("14. 【F-4-3 核心】普通明文存储值即使完全相同也必须拒绝")
    void matches_plaintextStored_false() {
        assertThat(PasswordHasher.matches("123456", "123456"))
                .as("DB 中若残留明文，登录必须失败（不再静默兼容）")
                .isFalse();
        assertThat(PasswordHasher.matches("admin", "admin")).isFalse();
        assertThat(PasswordHasher.matches(RAW, RAW.toUpperCase())).isFalse();
    }

    @Test
    @DisplayName("15. 【F-4-3】cost 超出 04–31 视为结构非法 ⇒ false（不做无意义 BCrypt 计算）")
    void matches_costOutOfRange_false() {
        String body = "$" + "A".repeat(53);
        assertThat(PasswordHasher.matches(RAW, "$2a$99" + body)).as("cost=99 越界").isFalse();
        assertThat(PasswordHasher.matches(RAW, "$2a$03" + body)).as("cost=03 越界").isFalse();
        assertThat(PasswordHasher.matches(RAW, "$2a$32" + body)).as("cost=32 越界").isFalse();
    }

    @Test
    @DisplayName("16. 【F-4-3】cost 在 04–31 边界内：结构通过后交由 checkpw 判定（不抛异常）")
    void matches_costBoundaryWithinRange_noException() {
        String body = "$" + "A".repeat(53);
        // 结构合法但内容非真实哈希 ⇒ checkpw 必然 false；此处只验证「不抛异常、不误判通过」
        assertThat(PasswordHasher.matches(RAW, "$2a$04" + body)).isFalse();
        assertThat(PasswordHasher.matches(RAW, "$2a$31" + body)).isFalse();
        // 真实哈希（cost=10）仍正常通过
        assertThat(PasswordHasher.matches(RAW, PasswordHasher.hash(RAW))).isTrue();

        // ⚠️ 实现细节（已实测，Hutool 5.8.25）：cost=31 不会真的跑 2^31 轮 ——
        //    jBCrypt 的 rounds = 1 << log_rounds，int 左移溢出后为 -2147483648（负数），
        //    内层循环条件立即为假 ⇒ 该分支恒返回 false 且耗时约 0ms（实测 0ms，cost=10 为 83ms）。
        //    ⇒ 本用例不会给测试套件埋下慢用例；
        //    ⇒ cost=31 在本实现下并非「可用但慢」，而是「空转返回 false」，属已登记的已知边界。
    }
}
