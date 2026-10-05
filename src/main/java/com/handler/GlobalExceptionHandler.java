package com.handler;

import com.entity.EIException;
import com.security.ForbiddenException;
import com.utils.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理（Phase 2 / Step 4 第二阶段 A1–A4）。
 *
 * <p><b>统一契约</b>（与 {@code docs/PHASE2-T4-EXCEPTION-AUDIT.md} §2 的现状取证对照）：
 *
 * <table border="1">
 *   <caption>异常 → HTTP 状态 / 响应体</caption>
 *   <tr><th>异常</th><th>HTTP</th><th>body</th><th>说明</th></tr>
 *   <tr><td>{@link EIException}（业务异常）</td><td>200</td><td>{@code {code=异常自带 code, msg}}</td>
 *       <td>沿用既有「HTTP 200 + body.code」契约；业务提示需能被前端显示，故不用 4xx</td></tr>
 *   <tr><td>参数 / 请求体错误</td><td>400</td><td>{@code {code=400, msg}}</td><td>不泄漏原始细节，细节进日志</td></tr>
 *   <tr><td>资源不存在</td><td>404</td><td>{@code {code=404, msg}}</td><td>取代原先被拦截器改写的 401 响应体</td></tr>
 *   <tr><td>其他 Spring MVC 标准异常</td><td>其自带状态</td><td>{@code {code=状态码, msg}}</td>
 *       <td>如 405 / 415，保留 Spring 判定的正确状态码</td></tr>
 *   <tr><td>未捕获异常</td><td>500</td><td>{@code {code=500, msg}}</td><td>对外统一提示，堆栈只进日志</td></tr>
 * </table>
 *
 * <p><b>刻意不做的事</b>：
 * <ul>
 *   <li><b>不处理「未登录」</b> —— 仍由 {@code AuthorizationInterceptor} 直接写响应体
 *       （HTTP 200 + {@code body.code=401}）。这是 Step 4 的 D1 决策（保持不改），
 *       改动它需同步修改用户端 3 处 + 管理端 1 处「登录跳转」逻辑（均在 2xx 分支内）。</li>
 *   <li><b>不收敛 {@code code} 语义</b> —— 全仓 163 处 {@code R.error(511, ...)} 保持原样（D3：暂缓，先分类）。</li>
 *   <li><b>不启用参数校验</b> —— 校验注解与 {@code ValidatorUtils} 维持现状（D4）。</li>
 * </ul>
 *
 * @see com.config.InterceptorConfig 拦截器配置（已排除 {@code /error}，消除错误响应体被劫持的问题）
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MSG_BAD_REQUEST = "请求参数错误";
    private static final String MSG_NOT_FOUND = "请求的资源不存在";
    private static final String MSG_SERVER_ERROR = "服务器内部错误，请联系管理员";

    /**
     * 业务异常：沿用「HTTP 200 + body.code」的既有契约。
     *
     * <p>为什么不用 4xx：{@link EIException} 的 msg 是给用户看的业务提示
     * （如「上传文件不能为空」），而两套前端只在 <b>2xx 响应</b>上显示 {@code msg}
     * （非 2xx 一律进 error 分支显示「请求接口失败」）。返回 200 才能保住提示语。
     */
    @ExceptionHandler(EIException.class)
    public ResponseEntity<R> handleEIException(EIException e) {
        logger.warn("业务异常: code={}, msg={}", e.getCode(), e.getMsg());
        return ResponseEntity.ok(R.error(e.getCode(), e.getMsg()));
    }

    /** 参数 / 请求体错误 → HTTP 400 */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<R> handleBadRequest(Exception e) {
        logger.warn("请求参数错误: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.error(HttpStatus.BAD_REQUEST.value(), MSG_BAD_REQUEST));
    }

    /**
     * 授权失败 → <b>HTTP 403</b>（Step 5 第二阶段批 1 的<b>新增行为契约</b>）。
     *
     * <p>与「未认证」严格区分：未认证由拦截器返回 {@code HTTP 200 + body.code=401}（既有契约，不改）；
     * 已认证但无权访问则返回 {@code HTTP 403 + code=403}。
     *
     * @see com.security.OwnershipGuard
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<R> handleForbidden(ForbiddenException e) {
        logger.warn("授权失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.error(HttpStatus.FORBIDDEN.value(), e.getMessage()));
    }

    /** 资源不存在 → HTTP 404（原先该响应体被拦截器写成 {@code code=401}） */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<R> handleNotFound(Exception e) {
        logger.warn("请求的资源不存在: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.error(HttpStatus.NOT_FOUND.value(), MSG_NOT_FOUND));
    }

    /**
     * 兜底：未捕获异常 → HTTP 500。
     *
     * <p>若异常本身实现了 {@link ErrorResponse}（Spring MVC 标准异常，如
     * 405 / 415 / 400），则<b>保留其自带状态码</b>，避免把语义错误误报成 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R> handleException(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            logger.warn("请求处理失败 [{}]: {}", status.value(), e.getMessage());
            return ResponseEntity.status(status).body(R.error(status.value(), e.getMessage()));
        }
        logger.error("未捕获异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), MSG_SERVER_ERROR));
    }
}
