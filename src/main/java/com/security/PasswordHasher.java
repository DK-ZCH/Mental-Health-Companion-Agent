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
 * <p><b>只接受 BCrypt（F-4-3 起）</b>：
 * <ul>
 *   <li>BCrypt（{@code $2} 家族 + 结构合法）→ {@code checkpw}</li>
 *   <li><b>其余一律拒绝</b>：历史明文兼容分支已在 F-4-3 删除，
 *       命中时记录 {@code [SEC][NON-BCRYPT-STORED]} 并返回 {@code false}</li>
 * </ul>
 * 前置条件：DB 已全量 BCrypt（F-4-2 完成，{@code plain=0}）。
 *
 * <p><b>关键安全语义（fail-closed）</b>：
 * <ol>
 *   <li>空凭据（{@code null} / {@code ""}）在最前短路 → {@code false}，不参与任何比较；</li>
 *   <li>判定为 BCrypt 家族后，解析失败 <b>绝不回退明文比较</b>
 *       （否则非法 hash 可被当作明文匹配 = 认证绕过）；</li>
 *   <li>非 BCrypt 值同样 <b>直接拒绝</b>（F-4-3 起无任何明文比对路径）。</li>
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
     * <p>cost 限定 {@code 04–31}（F-4-3 起，与 {@code PasswordMigrator} 口径一致）：
     * 超出范围的 {@code $2a$99$...} 属无效 cost，直接拒绝可省去一次无意义的 BCrypt 计算。
     *
     * <p>结构匹配后仍必须交给 {@code checkpw} 判定。
     */
    private static final Pattern BCRYPT_SHAPE =
            Pattern.compile("^\\$2[abxy]?\\$(?:0[4-9]|[12]\\d|3[01])\\$[./A-Za-z0-9]{53}$");

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
     * 校验密码（<b>仅接受 BCrypt</b>；历史明文兼容分支已由 F-4-3 删除）。
     *
     * @param rawPassword    用户提交的原始密码
     * @param storedPassword 数据库中的存储值（必须是合法 BCrypt）
     * @return 匹配为 {@code true}；任何空值 / 非法哈希 / 非 BCrypt 存储值 / 不匹配均为 {@code false}
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

        // ③ 非 BCrypt 家族 ⇒ 一律拒绝（F-4-3 已切断明文 fallback）
        //    该分支意味着 DB 中存在脏数据（明文/未知格式），属安全事件，需运维重置该账号密码。
        //    ⚠️ 日志只记长度，不记任何内容片段 —— 存储值可能就是明文密码本身。
        logger.warn("[SEC][NON-BCRYPT-STORED] 存储值不是合法 BCrypt，拒绝比对（该账号需重置密码）。storedLen={}",
                storedPassword.length());
        return false;
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
