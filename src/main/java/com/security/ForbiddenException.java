package com.security;

/**
 * 授权失败异常（Phase 2 / Step 5 第二阶段 批 1）。
 *
 * <p>语义：<b>已认证，但无权访问该对象</b> —— 与「未认证」严格区分：
 * <ul>
 *   <li>未认证 → 由 {@code AuthorizationInterceptor} 直接写 {@code HTTP 200 + body.code=401}</li>
 *   <li>已认证但无权 → 本异常 → 由 {@code GlobalExceptionHandler} 统一返回
 *       <b>{@code HTTP 403} + {@code {code:403, msg:"无权访问"}}</b></li>
 * </ul>
 *
 * <p>这是 Step 5 第二阶段的 **新增行为契约**（设计文档 §3.3 已锁定），
 * 刻意不复用 {@code EIException} —— 后者按既有契约返回 HTTP 200 + body.code。
 *
 * @see com.security.OwnershipGuard
 * @see com.handler.GlobalExceptionHandler
 */
public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 对外统一提示语（不暴露对象归属细节，避免信息泄露） */
    public static final String DEFAULT_MESSAGE = "无权访问";

    public ForbiddenException() {
        super(DEFAULT_MESSAGE);
    }

    public ForbiddenException(String message) {
        super(message);
    }
}
