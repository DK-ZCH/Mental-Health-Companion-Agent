package com.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 4 / P0-AUTH-TOKEN-LOG：登录令牌不得以明文进入日志（用户 2026-11-04 批准收口）。
 *
 * <p><b>证据链背景（docs/DEBT-ASSESSMENT.md + P0 证据补充）</b>：
 * <pre>
 * Q1 实锤：com.dao.AuthTokenDao.updateById 的参数行出现真实登录令牌明文
 * Q2 确认：mapper namespace = com.dao.AuthTokenDao ⇒ 受 logging.level.com.dao: debug 控制
 * Q3 验证：降至 warn 后探针 0 命中，还原 DEBUG 后复现（排除「SQL 未执行」替代解释）
 * </pre>
 *
 * <p><b>测试凭证纪律</b>：断言使用的 token 是<b>本测试运行时由服务端新生成的随机凭证</b>
 * （非任何真实用户凭证的复制）；它同时是被测对象 —— 出现在日志中即为本测试的红灯信号。
 *
 * <p><b>正向控制</b>：登录成功 + 带 token 的认证请求返回 200 ⇒ {@code auth_token} 的
 * 写入与查询 SQL <b>确实执行</b> —— 探针 0 命中只能归因于日志级别抑制，不是「SQL 没跑」。
 *
 * <p><b>先红预期</b>：当前 {@code com.dao.AuthTokenDao: debug} —— token 会出现在捕获输出，本类转红；
 * 配置降至 warn 后转绿；还原变异（临时改回 debug）应再次转红（证明探针真实约束配置）。
 *
 * <p><b>捕获范围说明</b>：{@code OutputCaptureExtension} 捕获测试期间 console（logback
 * ConsoleAppender → stdout/stderr），与生产 {@code app.log} 重定向同源。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-only")
@AutoConfigureMockMvc
@ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@DisplayName("P0-AUTH-TOKEN-LOG：登录令牌明文不得进入日志")
class Phase4P0AuthTokenLogTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("T1. 登录令牌（本次运行时生成）不得出现在日志输出中；登录功能本身正常")
    void tokenNeverAppearsInLogs(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        // ① 正向控制：登录功能正常（token 非空且为本次新生成凭证）
        MvcResult result = mockMvc.perform(post("/student/login")
                        .param("username", "a1")
                        .param("password", "123456"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String token = body.path("token").asText();
        assertThat(token).as("正向控制：登录应签发随机令牌").isNotBlank().hasSize(32);

        // ② P0 断言：该令牌不得以明文出现在日志输出中（当前 DEBUG 配置下先红）
        assertThat(output.toString())
                .as("登录令牌明文不得进入日志（Q1 已证实 updateById 参数行泄漏；"
                        + "修复 = com.dao.AuthTokenDao 降至 warn）")
                .doesNotContain(token);
    }

    @Test
    @DisplayName("T2. 认证查询路径同样不泄漏：带 token 的请求成功，且 token 不出现在日志输出中")
    void authQueryPathDoesNotLeak(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(post("/student/login")
                        .param("username", "a1")
                        .param("password", "123456"))
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("token").asText();
        assertThat(token).isNotBlank();

        // 正向控制：带 token 的认证请求走 AuthTokenDao.selectList（WHERE token=?）且返回 200
        // ⇒ 查询 SQL 确实执行（功能性证明），探针 0 命中只能归因于级别抑制
        MvcResult authed = mockMvc.perform(get("/student/session").header("token", token)).andReturn();
        assertThat(authed.getResponse().getStatus())
                .as("正向控制：认证请求应成功（证明 auth_token 查询执行）")
                .isEqualTo(200);

        // P0 断言：该路径的 token 参数不得出现在日志输出中
        assertThat(output.toString())
                .as("认证查询路径的令牌明文不得进入日志")
                .doesNotContain(token);
    }
}
