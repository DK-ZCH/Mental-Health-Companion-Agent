package com.agent;

/**
 * 危机转介资源（Phase 4 / Round 3 · 用户 Q2 裁定：<b>第一版无号码版本</b>）。
 *
 * <p><b>为什么必须是独立对象，而不是写进 {@link SafetyRules} 的常量</b>：
 * <ul>
 *   <li>规则回答「<b>何时</b>触发」，资源回答「<b>触发后给什么</b>」，两者变更节奏不同；</li>
 *   <li>资源必须<b>可追溯</b>（谁确认、何时确认、来源在哪），规则不需要；</li>
 *   <li>把号码写进规则常量，等于让「改安全文案」和「改安全规则」混在同一次代码变更里。</li>
 * </ul>
 *
 * <p><b>用户 Q2 的硬约束</b>：
 * <pre>
 * ❌ 不默认 120（也不写 110 / 911 等任何号码）
 * ❌ 不猜部署地区
 * ❌ 不编造热线
 * ✅ 资源未确认时，只给【无号码的行为指引】
 * </pre>
 * 项目当前<b>没有部署地区契约</b>，因此 {@link #unconfigured()} 是唯一合法的初始状态。
 *
 * <p><b>为什么不是「未确认就不给任何提示」</b>：用户裁定第一版仍要提供
 * 无号码的行为指引 —— 它比「什么都不说」更有用，同时不引入任何未经核实的信息。
 */
public final class SafetyResources {

    /** 资源已确认（地区 + 至少一条已验证资源）*/
    public static final String STATUS_CONFIGURED = "CONFIGURED";

    /** 资源未确认（当前唯一合法状态）*/
    public static final String STATUS_UNCONFIGURED = "UNCONFIGURED";

    private final String region;
    private final String emergencyMedical;
    private final String crisisHotline;
    private final String schoolCenter;
    private final String verifiedBy;
    private final String verifiedAt;
    private final String source;

    private SafetyResources(String region, String emergencyMedical, String crisisHotline,
                            String schoolCenter, String verifiedBy, String verifiedAt, String source) {
        this.region = region;
        this.emergencyMedical = emergencyMedical;
        this.crisisHotline = crisisHotline;
        this.schoolCenter = schoolCenter;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = verifiedAt;
        this.source = source;
    }

    /**
     * 未确认状态：所有字段为空。
     *
     * <p>这是当前<b>唯一</b>被允许的构造方式 —— 新增真实资源必须走
     * {@link #configured} 并填写 {@code verifiedBy / verifiedAt / source}。
     */
    public static SafetyResources unconfigured() {
        return new SafetyResources(null, null, null, null, null, null, null);
    }

    /**
     * 已确认资源。
     *
     * <p>四个追溯字段<b>不允许为空</b>：没有来源与确认人的号码，不属于「已确认」。
     *
     * @throws IllegalArgumentException 任一追溯字段为空，或既无紧急医疗也无热线
     */
    public static SafetyResources configured(String region, String emergencyMedical,
                                             String crisisHotline, String schoolCenter,
                                             String verifiedBy, String verifiedAt, String source) {
        requireText(region, "region");
        requireText(verifiedBy, "verifiedBy");
        requireText(verifiedAt, "verifiedAt");
        requireText(source, "source");
        if (isBlank(emergencyMedical) && isBlank(crisisHotline)) {
            throw new IllegalArgumentException("至少需要一条已确认资源（emergencyMedical 或 crisisHotline）");
        }
        return new SafetyResources(region, emergencyMedical, crisisHotline, schoolCenter,
                verifiedBy, verifiedAt, source);
    }

    /** 是否已确认（地区 + 至少一条资源）*/
    public boolean isConfigured() {
        return !isBlank(region) && (!isBlank(emergencyMedical) || !isBlank(crisisHotline));
    }

    /** 资源状态（进响应体，便于区分「无号码」与「忘了配」）*/
    public String status() {
        return isConfigured() ? STATUS_CONFIGURED : STATUS_UNCONFIGURED;
    }

    /**
     * 追加到模型回答之后的资源文本。
     *
     * <p><b>未确认时不得出现任何号码</b>（含 120）——
     * 因为项目没有部署地区契约，写具体号码就是在替用户假设地区。
     */
    public String guidanceText() {
        if (!isConfigured()) {
            return """
                    ——
                    如果你现在有立即的危险，请联系当地的紧急医疗服务，或前往最近的医院急诊。
                    尽量【不要独处】，请联系一位你信任的人陪着你。
                    如果你在学校，也可以联系学校的心理中心（若学校提供）。
                    """.stripTrailing();
        }

        StringBuilder sb = new StringBuilder("——\n");
        sb.append("如果你现在有立即的危险：\n");
        if (!isBlank(emergencyMedical)) {
            sb.append("· 紧急医疗：").append(emergencyMedical).append('\n');
        }
        if (!isBlank(crisisHotline)) {
            sb.append("· 心理援助热线：").append(crisisHotline).append('\n');
        }
        if (!isBlank(schoolCenter)) {
            sb.append("· 学校心理中心：").append(schoolCenter).append('\n');
        }
        sb.append("尽量【不要独处】，请联系一位你信任的人陪着你。");
        return sb.toString();
    }

    private static void requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " 不得为空（未经确认的资源不得标记为已配置）");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
