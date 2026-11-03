package com.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tool 调用观测记录器（Phase 4 · Observability 小批）。
 *
 * <p><b>只记录三项</b>：工具名 / 耗时 / 成功失败。
 * <b>绝不记录</b>：工具入参、返回内容、用户消息、异常 message、异常类名、用户标识。
 *
 * <p><b>为什么如此严格</b>：本平台处理心理相关数据；tool 入参与返回可能包含分数、
 * 留言正文、咨询师信息。观测层只需要回答「调了哪个工具、多久、成功没有」。
 *
 * <p><b>旁路（non-invasive）</b>：本类不改变任何业务/安全语义；
 * 记录失败也不影响调用方（异常由 {@link TimedToolCallback} 原样抛出）。
 *
 * <p><b>内存有界</b>：只保留「每个工具一条聚合」，不保存每次调用的历史。
 */
public class ToolCallRecorder {

    private static final Logger logger = LoggerFactory.getLogger(ToolCallRecorder.class);

    /** 日志前缀：测试用正则断言格式（O6） */
    public static final String LOG_PREFIX = "[AGENT][TOOL]";

    private final Map<String, Counters> counters = new ConcurrentHashMap<>();

    /**
     * 记录一次工具调用。
     *
     * @param toolName      工具名（来自 {@code ToolDefinition.name()}）
     * @param durationNanos 耗时（纳秒）
     * @param success       是否成功
     */
    public void record(String toolName, long durationNanos, boolean success) {
        String name = (toolName == null || toolName.isBlank()) ? "unknown" : toolName;
        long millis = Math.max(0, durationNanos / 1_000_000L);

        counters.computeIfAbsent(name, key -> new Counters()).add(millis, success);

        // 严格三项：name / elapsedMs / ok
        if (success) {
            logger.info("{} name={} elapsedMs={} ok=true", LOG_PREFIX, name, millis);
        } else {
            logger.warn("{} name={} elapsedMs={} ok=false", LOG_PREFIX, name, millis);
        }
    }

    /** 聚合快照（仅内部/测试使用，不暴露 HTTP 端点）*/
    public Map<String, Snapshot> snapshot() {
        Map<String, Snapshot> result = new LinkedHashMap<>();
        counters.forEach((name, c) -> result.put(name, c.snapshot()));
        return result;
    }

    /** 清空（仅测试使用）*/
    public void reset() {
        counters.clear();
    }

    /**
     * 单工具聚合。
     *
     * @param calls    调用次数
     * @param failures 失败次数
     * @param totalMs  累计耗时
     * @param maxMs    最大单次耗时
     */
    public record Snapshot(long calls, long failures, long totalMs, long maxMs) {
    }

    private static final class Counters {
        private final LongAdder calls = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder totalMs = new LongAdder();
        private volatile long maxMs;

        void add(long millis, boolean success) {
            calls.increment();
            if (!success) {
                failures.increment();
            }
            totalMs.add(millis);
            if (millis > maxMs) {
                maxMs = millis;
            }
        }

        Snapshot snapshot() {
            return new Snapshot(calls.sum(), failures.sum(), totalMs.sum(), maxMs);
        }
    }
}
