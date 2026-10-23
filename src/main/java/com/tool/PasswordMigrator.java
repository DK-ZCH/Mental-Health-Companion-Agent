package com.tool;

import cn.hutool.setting.yaml.YamlUtil;
import com.security.PasswordHasher;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S2-F-4-2：历史明文密码迁移工具（离线批处理，<b>不进 Spring 容器</b>）。
 *
 * <p><b>为什么是独立 main 而不是 {@code CommandLineRunner}</b>：
 * 后者一旦 profile / Bean 条件配置错误，就可能在正常启动应用时误触发迁移 —— 风险不可接受。
 *
 * <p><b>安全门禁（缺一不可）</b>：
 * <ol>
 *   <li><b>默认 dry-run</b>：不传 {@code --execute} 绝不写库；</li>
 *   <li><b>preflight 硬拦截</b>：{@code NULL} / 空串 / malformed BCrypt &gt; 0 ⇒ ABORT（exit 2）；</li>
 *   <li><b>格式语义由 Java 判断</b>，不由 SQL {@code WHERE NOT LIKE} 决定
 *       （SQL 只用于并发保护）；</li>
 *   <li><b>单事务</b>：唯一 {@code commit()} 在 try 末尾，任何异常整体 {@code rollback()}；</li>
 *   <li><b>乐观保护</b>：{@code WHERE id=? AND password=原值}，{@code updateCount != 1} ⇒ 冲突（exit 4）；</li>
 *   <li><b>零硬编码凭据</b>：参数 → 环境变量 → application.yml；日志脱敏；</li>
 *   <li>只 {@code UPDATE password} 一列，<b>无 DELETE / INSERT / DDL</b>。</li>
 * </ol>
 *
 * <p><b>用法</b>：
 * <pre>
 * java -cp "target/classes;&lt;deps&gt;" com.tool.PasswordMigrator [options]
 *   --dry-run        只统计不写库（默认行为）
 *   --execute        真正执行迁移（必须显式给出）
 *   --tables=a,b,c   默认 student,counselor,admin_user（只允许白名单子集）
 *   --url= --user= --password=   缺省依次取环境变量 F42_DB_*、application.yml
 * </pre>
 */
public final class PasswordMigrator {

    /** 表名白名单：即使 --tables 也只允许这些（防 SQL 注入与误操作） */
    private static final Set<String> ALLOWED_TABLES = Set.of("student", "counselor", "admin_user");

    private static final List<String> DEFAULT_TABLES = List.of("student", "counselor", "admin_user");

    /**
     * BCrypt <b>字符串形状</b>（捕获 cost 以便单独校验范围）。
     * <b>必须与 {@code com.security.PasswordHasher} 中的同名常量保持一致</b>
     * —— 工具离线运行，不参与认证，故此处复制而不扩大 PasswordHasher 的 public API。
     *
     * <p><b>已知边界（与 PasswordHasher 一致，刻意不改）</b>：{@code [abxy]?} 使
     * {@code $2$10$<53>}（59 字符，无版本字母）也能通过形状检查；F-4-1 实测该类
     * {@code checkpw} 恒为 false。当前库不存在此形态，登记而不在此处分叉判断。
     */
    private static final Pattern BCRYPT_SHAPE =
            Pattern.compile("^\\$2[abxy]?\\$(\\d{2})\\$[./A-Za-z0-9]{53}$");

    /** BCrypt cost 合法范围（标准实现 4–31；00–03 与 32–99 均不可用） */
    private static final int COST_MIN = 4;
    private static final int COST_MAX = 31;

    /**
     * classpath 名（编译后位于 {@code target/classes/application.yml}）。
     * 注意：{@code YamlUtil.loadByPath} 走 classpath 资源解析，<b>不接受文件系统路径</b>
     * （曾用 {@code src/main/resources/...} 触发 {@code NoResourceException}）。
     */
    private static final String YML_CLASSPATH = "application.yml";

    private static final String YML_HINT = "src/main/resources/application.yml（需先 mvn compile）";

    private static final String USAGE =
            "用法: java -cp <cp> com.tool.PasswordMigrator [--dry-run|--execute] "
                    + "[--tables=student,counselor,admin_user] [--url=... --user=... --password=...]";

    private PasswordMigrator() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** @return 退出码：0 成功 / 2 preflight 拦截 / 3 复核失败 / 4 乐观锁冲突 / 1 异常 */
    static int run(String[] args) {
        Config cfg;
        try {
            cfg = Config.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("[FATAL] 参数错误: " + e.getMessage());
            System.out.println(USAGE);
            return 1;
        }

        System.out.println("=== S2-F-4-2 密码迁移工具 ===");
        System.out.println("  模式      : " + (cfg.execute ? "**EXECUTE（会写库）**" : "DRY-RUN（不写库）"));
        System.out.println("  目标表    : " + cfg.tables);

        try (Connection conn = Connector.open(cfg)) {
            System.out.println("  连接      : " + Connector.describe(cfg));
            System.out.println();

            // ① preflight（读全表，由 Java 做格式语义分类）
            Preflight before = preflight(conn, cfg.tables);
            printPreflight("PREFLIGHT", before);

            if (before.hasBlocking()) {
                printBlocking(before);
                System.out.println();
                System.out.println(">>> ABORT：存在必须人工处置的数据，未做任何写入。");
                return 2;
            }
            System.out.println(">>> preflight PASS");
            System.out.println();

            if (!cfg.execute) {
                System.out.println(">>> DRY-RUN：计划迁移 " + before.candidates.size() + " 行，本次未写库。");
                System.out.println("    如需执行，请显式加 --execute");
                return 0;
            }

            if (before.candidates.isEmpty()) {
                System.out.println(">>> 已全为 BCrypt，无需迁移（幂等退出）。");
                return 0;
            }

            // ② 单事务迁移
            System.out.println("=== MIGRATION（单事务）===");
            boolean committed = migrate(conn, before.candidates);
            if (!committed) {
                // migrate 内部已 rollback 并抛异常，不会走到这里；保留以防结构被改坏
                return 1;
            }
            System.out.println();

            // ③ 迁移后复核（硬指标：plain == 0 && malformed == 0）
            Preflight after = preflight(conn, cfg.tables);
            printPreflight("POST-VERIFY", after);
            if (!after.isFullyMigrated()) {
                System.out.println(">>> FAIL：迁移后仍有非 BCrypt 数据（plain="
                        + after.candidates.size() + " malformed=" + after.malformedCount() + "）");
                return 3;
            }
            System.out.println(">>> POST-VERIFY PASS：三表全部为合法 BCrypt。");
            return 0;

        } catch (OptimisticLockException e) {
            System.err.println("[CONFLICT] " + e.getMessage());
            System.err.println(">>> 已整体 ROLLBACK，数据库保持迁移前状态。exit=4");
            return 4;
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return 1;
        }
    }

    // ==================== ① preflight ====================

    /**
     * 读取<b>全部行</b>并分类 —— <b>不使用 SQL 的 {@code NOT LIKE '$2%'} 作为格式判断</b>。
     * SQL 只负责「取数据」，格式语义由 Java 判断。
     */
    static Preflight preflight(Connection conn, List<String> tables) throws SQLException {
        Preflight pf = new Preflight();
        for (String table : tables) {
            TableStat st = new TableStat(table);
            String sql = "SELECT id, password FROM " + table + " ORDER BY id";
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    String p = rs.getString(2);
                    st.total++;
                    if (p == null) {
                        st.nullCount++;
                        pf.blocking.add(table + " id=" + id + " → password IS NULL");
                    } else if (p.isEmpty()) {
                        st.emptyCount++;
                        pf.blocking.add(table + " id=" + id + " → password 为空串");
                    } else if (p.startsWith("$2")) {
                        // 两级判断：形状 → cost 范围（cost 不在 4–31 的值无法被标准实现验证）
                        Matcher m = BCRYPT_SHAPE.matcher(p);
                        if (!m.matches()) {
                            st.malformed++;
                            pf.blocking.add(table + " id=" + id
                                    + " → 形似 BCrypt 但结构非法 (len=" + p.length() + ")");
                        } else {
                            int cost = Integer.parseInt(m.group(1));
                            if (cost < COST_MIN || cost > COST_MAX) {
                                st.malformed++;
                                pf.blocking.add(table + " id=" + id
                                        + " → BCrypt cost 非法: " + cost
                                        + "（合法范围 " + COST_MIN + "–" + COST_MAX + "）");
                            } else {
                                st.bcryptCount++;
                            }
                        }
                    } else {
                        st.plainCount++;
                        pf.candidates.add(new Candidate(table, id, p));
                    }
                }
            }
            pf.stats.put(table, st);
        }
        return pf;
    }

    // ==================== ② 单事务迁移 ====================

    /**
     * 单事务迁移：<b>唯一 commit 在 try 末尾</b>；任何异常 → rollback 后抛出。
     *
     * <p>刻意<b>不</b>在循环体内 commit、<b>不</b>支持批提交（本批 5 行不需部分提交语义）。
     *
     * @return true 表示已提交；异常路径不会返回
     */
    static boolean migrate(Connection conn, List<Candidate> candidates) throws SQLException {
        int total = candidates.size();
        int done = 0;
        boolean oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (Candidate c : candidates) {
                String hash = PasswordHasher.hash(c.plaintext);   // 60 字符，cost 与运行时一致
                String sql = "UPDATE " + c.table
                        + " SET password = ? WHERE id = ? AND password = ?";   // 乐观保护
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, hash);
                    ps.setLong(2, c.id);
                    ps.setString(3, c.plaintext);
                    int n = ps.executeUpdate();
                    if (n != 1) {
                        throw new OptimisticLockException(
                                c.table + " id=" + c.id + " updateCount=" + n
                                        + "（该行在 preflight 与 UPDATE 之间被修改）");
                    }
                }
                done++;
                System.out.println("  [" + done + "/" + total + "] " + c.table + " id=" + c.id + " → BCrypt");
            }
            conn.commit();          // ← 唯一 COMMIT 点
            System.out.println("  COMMIT 成功：" + done + " 行已迁移（单事务）");
            return true;
        } catch (Exception e) {
            try {
                conn.rollback();    // ← 覆盖所有异常
                // 措辞：不是"0 行变更"（UPDATE 可能已执行过 N 次），而是"N 次执行 + 0 持久化"
                System.out.println("  ROLLBACK 完成：" + done + " 次 UPDATE 已执行，最终持久化变更 = 0，"
                        + "数据库保持迁移前状态");
            } catch (SQLException re) {
                System.err.println("  [严重] ROLLBACK 失败: " + re.getMessage());
            }
            if (e instanceof SQLException) {
                throw (SQLException) e;
            }
            if (e instanceof OptimisticLockException) {
                throw (OptimisticLockException) e;
            }
            throw new SQLException("迁移失败: " + e.getMessage(), e);
        } finally {
            try {
                conn.setAutoCommit(oldAutoCommit);
            } catch (SQLException ignored) {
                // 连接即将关闭，恢复失败无影响
            }
        }
    }

    // ==================== 输出 ====================

    private static void printPreflight(String title, Preflight pf) {
        System.out.println("=== " + title + " ===");
        for (Map.Entry<String, TableStat> e : pf.stats.entrySet()) {
            TableStat s = e.getValue();
            System.out.printf("  %-11s total=%d  null=%d  empty=%d  bcrypt=%d  malformed=%d  plain=%d%n",
                    s.table, s.total, s.nullCount, s.emptyCount, s.bcryptCount, s.malformed, s.plainCount);
        }
        System.out.println("  待迁移候选(plain 合计) = " + pf.candidates.size());
    }

    private static void printBlocking(Preflight pf) {
        System.out.println();
        System.out.println("=== BLOCKING（必须人工处置）===");
        for (String b : pf.blocking) {
            System.out.println("  ✗ " + b);
        }
    }

    // ==================== 数据结构 ====================

    /** 待迁移行：保存原明文用于乐观保护 */
    static final class Candidate {
        final String table;
        final long id;
        final String plaintext;

        Candidate(String table, long id, String plaintext) {
            this.table = table;
            this.id = id;
            this.plaintext = plaintext;
        }
    }

    static final class TableStat {
        final String table;
        int total;
        int nullCount;
        int emptyCount;
        int bcryptCount;
        int malformed;
        int plainCount;

        TableStat(String table) {
            this.table = table;
        }
    }

    static final class Preflight {
        final Map<String, TableStat> stats = new LinkedHashMap<>();
        final List<Candidate> candidates = new ArrayList<>();
        final List<String> blocking = new ArrayList<>();

        boolean hasBlocking() {
            return !blocking.isEmpty();
        }

        boolean isFullyMigrated() {
            return candidates.isEmpty() && blocking.isEmpty();
        }

        /**
         * malformed 的<b>唯一状态源是 {@link TableStat#malformed}</b>，此处按表汇总。
         * <p>（不设 {@code Preflight.malformed} 字段 —— 曾因只累加 TableStat 而让该字段恒为 0，
         * 导致 POST-VERIFY 的诊断信息失真。）
         */
        int malformedCount() {
            int sum = 0;
            for (TableStat st : stats.values()) {
                sum += st.malformed;
            }
            return sum;
        }
    }

    /** 乐观保护冲突：updateCount != 1 */
    static final class OptimisticLockException extends RuntimeException {
        OptimisticLockException(String message) {
            super(message);
        }
    }

    // ==================== 配置 ====================

    static final class Config {
        boolean execute;
        List<String> tables = DEFAULT_TABLES;
        String url;
        String user;
        String password;

        static Config parse(String[] args) {
            Config c = new Config();
            for (String a : args) {
                if (a == null || a.isEmpty()) {
                    continue;
                }
                if ("--execute".equals(a)) {
                    c.execute = true;
                } else if ("--dry-run".equals(a)) {
                    c.execute = false;
                } else if (a.startsWith("--tables=")) {
                    c.tables = parseTables(a.substring("--tables=".length()));
                } else if (a.startsWith("--url=")) {
                    c.url = a.substring("--url=".length());
                } else if (a.startsWith("--user=")) {
                    c.user = a.substring("--user=".length());
                } else if (a.startsWith("--password=")) {
                    c.password = a.substring("--password=".length());
                } else {
                    throw new IllegalArgumentException("未知参数: " + a);
                }
            }
            return c;
        }

        /** 只允许白名单子集：表名会被拼进 SQL，必须严格校验 */
        private static List<String> parseTables(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                throw new IllegalArgumentException("--tables 不能为空");
            }
            List<String> list = new ArrayList<>();
            for (String t : raw.split(",")) {
                String name = t.trim();
                if (!ALLOWED_TABLES.contains(name)) {
                    throw new IllegalArgumentException("表名不在白名单: " + name + "（允许: " + ALLOWED_TABLES + "）");
                }
                if (!list.contains(name)) {
                    list.add(name);
                }
            }
            return list;
        }

    }

    // ==================== 连接解析（不硬编码） ====================

    static final class Connector {

        /** 优先级：命令行参数 → 环境变量 F42_DB_* → application.yml */
        static Connection open(Config cfg) throws SQLException {
            String url = firstNonBlank(cfg.url, System.getenv("F42_DB_URL"), yml("url"));
            String user = firstNonBlank(cfg.user, System.getenv("F42_DB_USER"), yml("username"));
            String pwd = firstNonBlank(cfg.password, System.getenv("F42_DB_PASSWORD"), yml("password"));

            if (url == null || user == null) {
                throw new SQLException("无法解析数据库连接（请用 --url/--user，或设置 F42_DB_URL/F42_DB_USER，"
                        + "或确保 " + YML_HINT + " 在 classpath 中）");
            }
            cfg.url = url;
            cfg.user = user;
            cfg.password = pwd;
            return DriverManager.getConnection(url, user, pwd == null ? "" : pwd);
        }

        /** 日志脱敏：不打印口令（JDBC URL 若含 password 参数亦打码） */
        static String describe(Config cfg) {
            String safeUrl = cfg.url == null ? "(unknown)"
                    : cfg.url.replaceAll("(?i)password=[^&;]*", "password=***");
            return safeUrl + "  (user=" + cfg.user + ")";
        }

        private static String firstNonBlank(String... vals) {
            for (String v : vals) {
                if (v != null && !v.trim().isEmpty()) {
                    return v.trim();
                }
            }
            return null;
        }

        /** 读取 application.yml 的 spring.datasource.*；失败返回 null（不抛错，交由上层判定） */
        private static String yml(String key) {
            try {
                // Hutool 返回 Dict（Map<String,Object> 实现），逐层用通配符取，避免泛型不匹配
                Object root = YamlUtil.loadByPath(YML_CLASSPATH);
                Object ds = child(child(root, "spring"), "datasource");
                Object v = child(ds, key);
                return v == null ? null : String.valueOf(v);
            } catch (Exception e) {
                return null;
            }
        }

        private static Object child(Object node, String key) {
            return (node instanceof Map) ? ((Map<?, ?>) node).get(key) : null;
        }

        private Connector() {
        }
    }
}
