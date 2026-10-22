package com.security;

import cn.hutool.crypto.digest.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * 密码哈希门面（S2-F-4-1）。
 *
 * <p><b>职责</b>：全项目密码「写入」与「校验」的唯一入口 ——
 * 业务代码不得直接调用 {@code BCrypt.hashpw/checkpw}，避免 9 个写入点各写一套。
 *
 * <p><b>双格式兼容（迁移窗口）</b>：
 * <ul>
 *   <li>BCrypt（{@code $2} 家族）→ {@code checkpw}</li>
 *   <li>历史明文 → {@code equals}（兜底，正常不应再触发；命中即 WARN）</li>
 * </ul>
 * 明文分支将在独立批次 F-4-3 中删除。
 *
 * <p><b>关键安全语义（fail-closed）</b>：
 * <ol>
 *   <li>空凭据（{@code null} / {@code ""}）在最前短路 → {@code false}，不参与任何比较；</li>
 *   <li>判定为 BCrypt 家族后，解析失败 <b>绝不回退明文比较</b>
 *       （否则非法 hash 可被当作明文匹配 = 认证绕过）；</li>
 *   <li>只有「不像 BCrypt」的值才允许进入明文分支。</li>
 * </ol>
 *
 * <p><b>实测依据（Hutool 5.8.25）</b>：{@code $2a$/$2b$/$2x$/$2y$} 均被 {@code checkpw} 支持；
 * {@code $2$}（59 字符）不支持；非法形态一律返回 {@code false} 而非抛异常；
 * {@code checkpw("123456","123456")} 返回 {@code false} —— 故明文兼容必须显式 {@code equals}，
 * 不能依赖 {@code checkpw} 兜底。
 */
public final class PasswordHasher {

    private static final Logger logger = LoggerFactory.getLogger(PasswordHasher.class);

    /** BCrypt 计算强度（候选值；最终值由目标环境 benchmark 决定，见 S2-F-3 §2.1） */
    private static final int COST = 10;

    /**
     * BCrypt <b>候选结构</b>（仅用于「是否尝试 BCrypt 解析」，<b>不代表</b>哈希合法）：
     * {@code $2[abxy]?$<2位cost>$<53字符salt+hash>}，共 60 字符（{@code $2$} 为 59）。
     *
     * <p>结构匹配后仍必须交给 {@code checkpw} 判定；匹配失败不代表是明文，
     * 只是「不像 BCrypt」。
     */
    private static final Pattern BCRYPT_SHAPE =
            Pattern.compile("^\\$2[abxy]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private PasswordHasher() {
    }

    /**
     * 生成密码哈希（BCrypt，随机盐）。
     *
     * @param rawPassword 原始密码，<b>不得为空</b>
     * @return 60 字符 BCrypt 哈希
     * @throws IllegalArgumentException {@code rawPassword} 为 {@code null} 或空串
     *                                  （不静默生成「空密码哈希」，否则等于制造弱口令）
     */
    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("原始密码不得为空");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(COST));
    }

    /**
     * 校验密码（BCrypt 优先，历史明文兜底）。
     *
     * @param rawPassword    用户提交的原始密码
     * @param storedPassword 数据库中的存储值（BCrypt 哈希或历史明文）
     * @return 匹配为 {@code true}；任何空值 / 非法哈希 / 不匹配均为 {@code false}
     */
    public static boolean matches(String rawPassword, String storedPassword) {
        // ① fail-closed：空凭据一律拒绝，先于任何比较逻辑
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        if (storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }

        // ② BCrypt 家族 → 结构校验 → checkpw；任何失败均 false，绝不回退明文
        if (isBcryptFamily(storedPassword)) {
            if (!BCRYPT_SHAPE.matcher(storedPassword).matches()) {
                logger.warn("[SEC][BCRYPT-MALFORMED] 存储值形似 BCrypt 但结构非法，拒绝比对。storedLen={} prefix4={}",
                        storedPassword.length(), safePrefix(storedPassword));
                return false;
            }
            try {
                return BCrypt.checkpw(rawPassword, storedPassword);
            } catch (RuntimeException e) {
                // 防御：不同实现/版本对非法输入可能抛异常，一次脏数据不得导致登录 500
                logger.warn("[SEC][BCRYPT-PARSE-FAIL] BCrypt 解析失败，拒绝比对。storedLen={} prefix4={} ex={}",
                        storedPassword.length(), safePrefix(storedPassword), e.getClass().getSimpleName());
                return false;
            }
        }

        // ③ 历史明文（兼容窗口，F-4-3 删除）
        boolean matched = storedPassword.equals(rawPassword);
        if (matched) {
            logger.warn("[SEC][PLAINTEXT-PASSWORD-HIT] 命中历史明文密码，该账号尚未迁移为 BCrypt。storedLen={}",
                    storedPassword.length());
        }
        return matched;
    }

    /** BCrypt 家族前缀：{@code $2} / {@code $2a} / {@code $2b} / {@code $2x} / {@code $2y} */
    private static boolean isBcryptFamily(String storedPassword) {
        return storedPassword.startsWith("$2");
    }

    /** 仅取前缀 4 字符用于日志定位，避免泄露存储值本体 */
    private static String safePrefix(String storedPassword) {
        return storedPassword.length() <= 4 ? storedPassword : storedPassword.substring(0, 4);
    }
}
