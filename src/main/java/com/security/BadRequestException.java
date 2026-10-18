package com.security;

/**
 * 请求参数 / 请求体语义错误（Phase 2 / <b>T1</b>）。
 *
 * <p>由 {@code GlobalExceptionHandler} 转成 {@code HTTP 400 + body.code=400}。
 *
 * <p><b>与其它拒绝形态的边界（严格区分）</b>：
 * <ul>
 *   <li><b>未认证</b> → 拦截器 {@code HTTP 200 + body.code=401}（D1 已定，不改）</li>
 *   <li><b>已认证但无权</b> → {@link ForbiddenException} → {@code HTTP 403 + code=403}</li>
 *   <li><b>请求本身不完整 / 不合法</b> → <b>本异常</b> → {@code HTTP 400 + code=400}</li>
 *   <li><b>真正的服务端内部异常</b> → {@code HTTP 500}（兜底，<b>不被本异常替代</b>）</li>
 * </ul>
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
