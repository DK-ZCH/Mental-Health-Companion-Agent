package com.agent;

/**
 * Step 23：UserContext 身份校验失败的<b>专用失败标识</b>。
 *
 * <p><b>为什么需要专用类型</b>：{@link IllegalStateException} 是 Java 通用运行时异常，
 * 现有 Tool 用它表达身份失败，但普通数据源调用未来也可能抛同类型异常。
 * 因此 UserContext 的身份判定<b>不依赖通用异常类型</b>：
 * <ul>
 *   <li>身份前置校验放在所有分段异常捕获之外，失败直接抛本异常；</li>
 *   <li>分段查询只把本异常向外传播，普通异常仅降级对应分段；</li>
 *   <li>Tool 调用前的身份守卫同样在 try 之外，失败自然向外传播。</li>
 * </ul>
 *
 * <p>本异常表示安全事件：整个 UserContext 构建 fail-closed，
 * 不得降级、不得返回部分分段、不得回喂模型。
 */
public final class UserContextIdentityDeniedException extends RuntimeException {

    public UserContextIdentityDeniedException(String message) {
        super(message);
    }

    public UserContextIdentityDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
