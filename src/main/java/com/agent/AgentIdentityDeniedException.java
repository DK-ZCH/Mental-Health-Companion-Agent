package com.agent;

/**
 * Tool 层身份拒绝的<b>专用可识别子类型</b>（Step 23 定点修正 · 方案 A–C，用户 2026-11-05 批准）。
 *
 * <p><b>为什么继承 {@link IllegalStateException}</b>：现有 5 个只读 Tool 的身份失败
 * 契约就是 {@code IllegalStateException}，且有 24 处既有测试断言该父类、
 * HTTP 异常映射也没有它的专属分支。把身份拒绝改为<b>子类</b>后：
 * <ul>
 *   <li>所有 {@code isInstanceOf(IllegalStateException.class)} 断言仍成立；</li>
 *   <li>HTTP 状态语义不变（仍走兜底 {@code @ExceptionHandler(Exception.class)}）；</li>
 *   <li>调用方可用 {@code catch (AgentIdentityDeniedException e)} 明确区分
 *       「身份拒绝」与「普通数据源故障」，无需靠异常消息或不变的上下文复检猜测。</li>
 * </ul>
 *
 * <p><b>语义</b>：表示安全事件，必须向外传播（fail-closed），
 * 不得被当作普通查询失败降级、不得回喂模型。
 *
 * <p><b>范围</b>：只用于 {@link AgentToolGuard#requireStudent} 与
 * {@link AssessmentTools} 内联等价校验的 4 个抛点；不改变校验条件与错误码字符串。
 */
public class AgentIdentityDeniedException extends IllegalStateException {

    public AgentIdentityDeniedException(String message) {
        super(message);
    }
}
