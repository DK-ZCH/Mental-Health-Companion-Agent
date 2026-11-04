package com.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 债 1：{@link PhoneMaskingPolicy} 单元测试（纯静态方法，无 Spring / 无 DB）。
 *
 * <p><b>验收口径（docs/BATCH-DEBT1-PHONE-MASK-PLAN.md §四冻结）</b>：
 * 正例 S1–S5 必须全部命中遮蔽；反例 R1–R8 必须零误伤，两者同时成立才算通过。
 * 反例覆盖用户点名的全部数字形式：日期/时间/分数/范围/时长/编号/小数/全角。
 */
@DisplayName("债 1 / PhoneMaskingPolicy：高置信号码遮蔽（S1–S5 命中 · R1–R8 零误伤）")
class PhoneMaskingPolicyTest {

    // ================== S 组：必须遮蔽 ==================

    @Test
    @DisplayName("S1. 400 号段（带分隔）被遮蔽，前后文原样保留")
    void s1_400_hotline_masked() {
        String out = PhoneMaskingPolicy.mask("可拨打 400-161-9995 咨询");
        assertThat(out).doesNotContain("400-161-9995")
                .contains(PhoneMaskingPolicy.MASK_TEXT)
                .contains("可拨打")
                .contains("咨询");
    }

    @Test
    @DisplayName("S2. 11 位手机号（无分隔）被遮蔽")
    void s2_mobile_masked() {
        String out = PhoneMaskingPolicy.mask("热线电话 13812345678");
        assertThat(out).doesNotContain("13812345678")
                .contains(PhoneMaskingPolicy.MASK_TEXT);
    }

    @Test
    @DisplayName("S3. 带分隔座机被遮蔽")
    void s3_landline_masked() {
        String out = PhoneMaskingPolicy.mask("办公室 010-12345678");
        assertThat(out).doesNotContain("010-12345678")
                .contains(PhoneMaskingPolicy.MASK_TEXT);
    }

    @Test
    @DisplayName("S4. 全角号码先规范化再判定（仅长度不构成遮蔽理由，但形似电话必须命中）")
    void s4_fullwidth_phone_masked() {
        String out = PhoneMaskingPolicy.mask("热线４００－１６１－９９９５");
        assertThat(out).doesNotContain("４００－１６１－９９９５")
                .contains(PhoneMaskingPolicy.MASK_TEXT);
    }

    @Test
    @DisplayName("S5. 一句话多个号码逐处替换（文案相同 · 前后文保留）")
    void s5_multiple_numbers_each_masked() {
        String out = PhoneMaskingPolicy.mask("可拨打 400-161-9995 或 13812345678 咨询");
        assertThat(out).doesNotContain("400-161-9995")
                .doesNotContain("13812345678")
                .contains("或")
                .contains("可拨打")
                .contains("咨询");
        long n = out.split(java.util.regex.Pattern.quote(PhoneMaskingPolicy.MASK_TEXT), -1).length - 1;
        assertThat(n).isEqualTo(2);
    }

    // ================== R 组：必须零误伤 ==================

    @Test
    @DisplayName("R1. 日期零误伤：2026-11-04 / 2026年11月4日 原样")
    void r1_dates_untouched() {
        assertThat(PhoneMaskingPolicy.mask("截止 2026-11-04 提交"))
                .isEqualTo("截止 2026-11-04 提交");
        assertThat(PhoneMaskingPolicy.mask("2026年11月4日开学"))
                .isEqualTo("2026年11月4日开学");
    }

    @Test
    @DisplayName("R2. 时间零误伤：14:30 / 15:20:44 原样")
    void r2_times_untouched() {
        assertThat(PhoneMaskingPolicy.mask("下午 14:30 上课"))
                .isEqualTo("下午 14:30 上课");
        assertThat(PhoneMaskingPolicy.mask("考试时长 15:20:44 计时"))
                .isEqualTo("考试时长 15:20:44 计时");
    }

    @Test
    @DisplayName("R3. 分数零误伤：15/100 · 88 分 · 0.85 原样")
    void r3_scores_untouched() {
        assertThat(PhoneMaskingPolicy.mask("得分 15/100"))
                .isEqualTo("得分 15/100");
        assertThat(PhoneMaskingPolicy.mask("考了 88 分"))
                .isEqualTo("考了 88 分");
        assertThat(PhoneMaskingPolicy.mask("匹配度 0.85"))
                .isEqualTo("匹配度 0.85");
    }

    @Test
    @DisplayName("R4. 范围零误伤：20–30 分钟 · 80–100 分 原样")
    void r4_ranges_untouched() {
        assertThat(PhoneMaskingPolicy.mask("需要 20–30 分钟"))
                .isEqualTo("需要 20–30 分钟");
        assertThat(PhoneMaskingPolicy.mask("分数段 80–100 分"))
                .isEqualTo("分数段 80–100 分");
    }

    @Test
    @DisplayName("R5. 时长/数量零误伤：20 分钟 · 120 分钟 原样")
    void r5_durations_untouched() {
        assertThat(PhoneMaskingPolicy.mask("冥想 20 分钟"))
                .isEqualTo("冥想 20 分钟");
        assertThat(PhoneMaskingPolicy.mask("预计 120 分钟完成"))
                .isEqualTo("预计 120 分钟完成");
    }

    @Test
    @DisplayName("R6. 编号零误伤：ABC1234567 · 订单号 1234567 · S-07-01 原样")
    void r6_ids_untouched() {
        assertThat(PhoneMaskingPolicy.mask("编号 ABC1234567"))
                .isEqualTo("编号 ABC1234567");
        assertThat(PhoneMaskingPolicy.mask("订单号 1234567 已受理"))
                .isEqualTo("订单号 1234567 已受理");
        assertThat(PhoneMaskingPolicy.mask("规则 S-07-01 生效"))
                .isEqualTo("规则 S-07-01 生效");
    }

    @Test
    @DisplayName("R7. 小数/版本号零误伤：3.14159 · 1.1.8 原样")
    void r7_decimals_untouched() {
        assertThat(PhoneMaskingPolicy.mask("圆周率约 3.14159"))
                .isEqualTo("圆周率约 3.14159");
        assertThat(PhoneMaskingPolicy.mask("当前版本 1.1.8"))
                .isEqualTo("当前版本 1.1.8");
    }

    @Test
    @DisplayName("R8. 不形似电话的全角数字零误伤（仅长度不遮蔽的冻结决策）")
    void r8_fullwidth_non_phone_untouched() {
        assertThat(PhoneMaskingPolicy.mask("冥想 ２０分钟"))
                .isEqualTo("冥想 ２０分钟");
        assertThat(PhoneMaskingPolicy.mask("分数段 ８０–１００分"))
                .isEqualTo("分数段 ８０–１００分");
    }

    // ================== 契约边界 ==================

    @Test
    @DisplayName("契约. null / 空串原样透传（AgentService 现有 null 处理不变）")
    void null_and_empty_passthrough() {
        assertThat(PhoneMaskingPolicy.mask(null)).isNull();
        assertThat(PhoneMaskingPolicy.mask("")).isEmpty();
        assertThat(PhoneMaskingPolicy.mask("普通回答，无任何数字"))
                .isEqualTo("普通回答，无任何数字");
    }
}
