package com.agent;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Tool 调用计时/计数包装（Phase 4 · Observability 小批）。
 *
 * <p><b>旁路原则（non-invasive）</b>：
 * <ul>
 *   <li>成功：记录 → <b>原返回值继续返回</b>（不改写、不截断）；</li>
 *   <li>失败：记录 {@code ok=false} → <b>原异常继续抛出</b>（不改类型、不改消息、不吞）。</li>
 * </ul>
 *
 * <p><b>记录内容严格限定</b>：工具名 / 耗时 / 成功失败。
 * 不读取、不传递 {@code toolInput} 与返回值内容到记录器。
 */
public class TimedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolCallRecorder recorder;

    public TimedToolCallback(ToolCallback delegate, ToolCallRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        long startNanos = System.nanoTime();
        boolean success = false;
        try {
            String result = delegate.call(toolInput, toolContext);
            success = true;
            return result;
        } finally {
            // 先记录再让异常继续向外传播（finally 不改变异常类型与消息）
            // 观测失败绝不能影响业务：recorder 自身异常在此吞掉（不记录入参/返回，因此无泄露风险）
            try {
                recorder.record(delegate.getToolDefinition().name(),
                        System.nanoTime() - startNanos, success);
            } catch (RuntimeException ignored) {
                // 观测是旁路能力：fail-silent，不改变 Agent 原有行为
            }
        }
    }
}
