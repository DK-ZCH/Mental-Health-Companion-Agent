package com.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 预约确认凭证（Phase 4 / Round 4 · 用户 Q2 裁定：<b>signed capability</b>）。
 *
 * <p><b>它不是「一次性 token」</b>：签名是无状态的，服务端能验证
 * 「未被篡改 / 未过期 / 属于当前用户 / 是这个操作」，
 * 但<b>无法</b>知道它此前是否已被使用。
 * 因此重复提交必须由<b>业务查重 + DB 唯一键</b>拒绝 —— 不能指望本类。
 *
 * <p><b>为什么 payload 不存裸 userId</b>：token 会进入模型上下文与客户端文本；
 * 存 {@code HMAC(secret, "uid:" + userId)} 既能校验「是不是同一个人」，又不泄露用户标识。
 *
 * <p><b>为什么把 cid/date/slot 放进 token</b>：确认时 {@code token} 是<b>唯一参数</b>，
 * 模型无法在「预览」与「确认」之间替换老师/日期/时段 —— 参数漂移在结构上不可能发生。
 *
 * <p><b>密钥</b>：{@code agent.confirm.secret}；未配置时生成随机运行时密钥并 WARN。
 * ⚠️ <b>WARN 不得包含 secret 本身</b>，也不得把它写进任何日志/异常。
 */
public class AppointmentConfirmToken {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentConfirmToken.class);

    /** 动作绑定：防止把预约凭证复用到其它写操作 */
    public static final String ACTION_CREATE = "CREATE_APPOINTMENT";

    private static final String VERSION = "v1";
    private static final String HMAC_ALG = "HmacSHA256";
    private static final int UID_HASH_LEN = 16;

    /** 默认有效期：5 分钟 */
    public static final int DEFAULT_TTL_SECONDS = 300;

    private final byte[] secret;
    private final ObjectMapper objectMapper;
    private final int ttlSeconds;

    public AppointmentConfirmToken(String configuredSecret, ObjectMapper objectMapper, int ttlSeconds) {
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds <= 0 ? 0 : ttlSeconds;
        if (configuredSecret == null || configuredSecret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            // 🔴 只说明后果，不打印密钥
            logger.warn("[AGENT][CONFIRM] agent.confirm.secret 未配置，已生成随机运行时 secret；"
                    + "重启后已有 confirmation token 将失效。");
        } else {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public int ttlSeconds() {
        return ttlSeconds;
    }

    /**
     * 签发凭证。
     *
     * @param userId      当前登录学生（写进 uidHash，不落明文）
     * @param counselorId 目标咨询师
     * @param date        预约日期
     * @param timeSlot    时段（1..8）
     */
    public String issue(int userId, int counselorId, LocalDate date, int timeSlot) {
        long now = Instant.now().getEpochSecond();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("v", 1);
        payload.put("act", ACTION_CREATE);
        payload.put("uid", uidHash(userId));
        payload.put("cid", counselorId);
        payload.put("d", date.toString());
        payload.put("s", timeSlot);
        payload.put("iat", now);
        payload.put("exp", now + ttlSeconds);
        payload.put("n", nonce());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // 不记录 payload（含 uidHash），仅记录异常类型
            throw new IllegalStateException("confirmation token 签发失败", e);
        }
        String body = encode(json.getBytes(StandardCharsets.UTF_8));
        return VERSION + "." + body + "." + encode(hmac(VERSION + "." + body));
    }

    /**
     * 校验凭证。
     *
     * @throws TokenException 任一环节失败（原因只用于日志，不返回给用户）
     */
    public Payload verify(String token, int userId) {
        if (token == null || token.isBlank()) {
            throw new TokenException("empty");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new TokenException("format");
        }

        byte[] expected = hmac(VERSION + "." + parts[1]);
        byte[] actual;
        try {
            actual = decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new TokenException("format");
        }
        // 常量时间比较，避免签名被逐字节试探
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new TokenException("signature");
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(decode(parts[1]));
        } catch (Exception e) {
            throw new TokenException("payload");
        }

        if (!ACTION_CREATE.equals(payload.path("act").asText())) {
            throw new TokenException("action");
        }
        if (!uidHash(userId).equals(payload.path("uid").asText())) {
            throw new TokenException("user");
        }
        long exp = payload.path("exp").asLong(0);
        if (exp <= Instant.now().getEpochSecond()) {
            throw new TokenException("expired");
        }

        try {
            return new Payload(
                    payload.path("cid").asInt(),
                    LocalDate.parse(payload.path("d").asText()),
                    payload.path("s").asInt());
        } catch (Exception e) {
            throw new TokenException("payload");
        }
    }

    private String uidHash(int userId) {
        String hex = toHex(hmac("uid:" + userId));
        return hex.substring(0, Math.min(UID_HASH_LEN, hex.length()));
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret, HMAC_ALG));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static String nonce() {
        byte[] bytes = new byte[8];
        new SecureRandom().nextBytes(bytes);
        return encode(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** 校验通过后从凭证里读出的字段（模型无法改写） */
    public record Payload(int counselorId, LocalDate date, int timeSlot) {
    }

    /** 校验失败（原因只写日志，不返回给用户） */
    public static class TokenException extends RuntimeException {
        private final String reason;

        public TokenException(String reason) {
            super(reason);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }
}
