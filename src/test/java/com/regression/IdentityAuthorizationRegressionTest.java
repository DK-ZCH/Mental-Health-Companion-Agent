package com.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 2 / Step 5 第二阶段 <b>批 1</b>：身份授权回归（8 个 {@code /info/{id}} 的归属校验）。
 *
 * <p><b>背景</b>：第一阶段取证实测证实 —— 学生 a1 可通过 {@code /info/{id}} 读取
 * student 2 / student 3 的留言与收藏（水平越权 / IDOR）。本用例固化修复后的契约。
 *
 * <p><b>契约</b>（设计文档 §3.3 已锁定，属<b>新增行为契约</b>）：
 * <pre>
 * 未认证            → HTTP 200 + body.code=401   （既有契约，本批不动）
 * 已认证但无权       → HTTP 403 + body.code=403 + msg="无权访问"
 * </pre>
 *
 * <p><b>为什么必须同时断言成功路径</b>：403 是新增契约，只测「越权失败」无法证明
 * 正常业务没被误伤（例如把「学生读自己」或「管理员读他人」也拒掉）。因此本类同时覆盖：
 * 学生读自己 ✓ / 管理员读他人 ✓ / 心理老师读自己的服务对象 ✓ /
 * <b>6 个内容类端点完全不受影响</b> ✓。
 *
 * <p><b>种子数据</b>（{@code mental_health_companion_agent.sql}）：
 * <pre>
 * student: 1=a1, 2=a2, 3=a3          counselor: 1=a1, 2=a2, 3=a3        admin_user: 6=admin
 * counselor_message:      id2(学生1,老师1) id3(学生3,老师1) id1(学生2,老师2)
 * counselor_favorite:     id3(学生1,老师1) id1(学生3,老师2)
 * counseling_appointment: id1(学生1,老师2) id3(学生1,老师1)
 * assessment_record:      id1(学生1)
 * </pre>
 * 注意：{@code a1} 在同一 id 上<b>既是学生 1 也是心理老师 1</b>（两套 token 分别登录）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdentityAuthorizationRegressionTest {

    /** 期望被拒绝：HTTP 403 + body.code=403 */
    private static final int FORBIDDEN = 403;
    /** 未认证契约：HTTP 200 + body.code=401（既有，不改） */
    private static final int UNAUTHENTICATED_BODY_CODE = 401;
    /** 统一拒绝提示语 */
    private static final String DENY_MSG = "无权访问";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 学生 a1（student id = 1）的 token */
    private String studentToken;
    /** 心理老师 a1（counselor id = 1）的 token —— 与 studentToken 是不同身份的 token */
    private String counselorToken;
    /** 管理员 admin 的 token */
    private String adminToken;

    @BeforeEach
    void loginAllIdentities() throws Exception {
        JsonNode student = getJson("/yonghu/login?username=a1&password=123456", null);
        assertThat(student.path("code").asInt()).as("学生 a1 登录").isZero();
        studentToken = student.path("token").asText();

        JsonNode counselor = getJson("/xinlilaoshi/login?username=a1&password=123456", null);
        assertThat(counselor.path("code").asInt()).as("心理老师 a1 登录").isZero();
        counselorToken = counselor.path("token").asText();

        JsonNode admin = objectMapper.readTree(mockMvc.perform(post("/users/login")
                        .param("username", "admin").param("password", "admin"))
                .andReturn().getResponse().getContentAsString());
        assertThat(admin.path("code").asInt()).as("管理员登录").isZero();
        adminToken = admin.path("token").asText();

        assertThat(studentToken).as("三种身份的 token 应互不相同").isNotEqualTo(counselorToken);
    }

    // ================== 1. 成功路径（403 是新增契约，必须证明没误伤） ==================

    @Test
    @DisplayName("1. 学生读【自己的】对象：留言/收藏/预约/测评/本人资料 均应放行")
    void studentCanReadOwnObjects() throws Exception {
        assertAllowed(perform("/xinlilaoshiLiuyan/info/2", studentToken), "自己的留言");
        assertAllowed(perform("/xinlilaoshiCollection/info/3", studentToken), "自己的收藏");
        assertAllowed(perform("/xinlilaoshiOrder/info/1", studentToken), "自己的预约");
        assertAllowed(perform("/examrecord/info/1", studentToken), "自己的测评记录");
        assertAllowed(perform("/yonghu/info/1", studentToken), "本人学生资料");
    }

    @Test
    @DisplayName("2. 管理员读【他人的】对象：应放行（B 类：管理员本就可查全量）")
    void adminCanReadOthersObjects() throws Exception {
        assertAllowed(perform("/xinlilaoshiLiuyan/info/1", adminToken), "他人留言（属 student 2）");
        assertAllowed(perform("/xinlilaoshiCollection/info/1", adminToken), "他人收藏（属 student 3）");
        assertAllowed(perform("/yonghu/info/2", adminToken), "他人学生资料（student 2）");
        assertAllowed(perform("/users/info/1", adminToken), "管理端账号");
    }

    @Test
    @DisplayName("3. 心理老师读【自己服务对象】的对象：应放行；读他人服务对象：应拒绝")
    void counselorScopeByCounselorId() throws Exception {
        // 留言 id3 → (学生3, 老师1) = a1 的 counselor 身份 ✓
        assertAllowed(perform("/xinlilaoshiLiuyan/info/3", counselorToken), "自己服务对象的留言");
        // 预约 id3 → (学生1, 老师1) ✓
        assertAllowed(perform("/xinlilaoshiOrder/info/3", counselorToken), "自己服务对象的预约");
        // 留言 id1 → (学生2, 老师2) ✗
        assertDenied(perform("/xinlilaoshiLiuyan/info/1", counselorToken), "他人服务对象的留言");
        // 预约 id1 → (学生1, 老师2) ✗
        assertDenied(perform("/xinlilaoshiOrder/info/1", counselorToken), "他人服务对象的预约");
    }

    @Test
    @DisplayName("4. 6 个内容类端点【不受本批影响】：仍是 200 + code=0")
    void contentEndpointsAreUnaffected() throws Exception {
        String[] contentEndpoints = {
                "/exampaper/info/1",
                "/examquestion/info/1",
                "/jiankangzhishi/info/1",
                "/tongzhi/info/1",
                "/dictionary/info/1",
                "/config/info/1"
        };
        for (String uri : contentEndpoints) {
            assertAllowed(perform(uri, studentToken), "内容类端点 " + uri);
        }
    }

    // ================== 2. 拒绝路径（本批要堵的越权） ==================

    @Test
    @DisplayName("5. 学生读【他人的】对象：应 403（本批修复的 IDOR）")
    void studentCannotReadOthersObjects() throws Exception {
        assertDenied(perform("/xinlilaoshiLiuyan/info/1", studentToken), "他人留言（属 student 2）");
        assertDenied(perform("/xinlilaoshiCollection/info/1", studentToken), "他人收藏（属 student 3）");
        assertDenied(perform("/yonghu/info/2", studentToken), "他人学生资料（student 2）");
    }

    @Test
    @DisplayName("6. 学生/老师读【管理端账号】:应 403（仅管理员可访问）")
    void adminOnlyEndpointIsDeniedForNonAdmin() throws Exception {
        assertDenied(perform("/users/info/1", studentToken), "学生读管理端账号");
        assertDenied(perform("/users/info/1", counselorToken), "心理老师读管理端账号");
    }

    @Test
    @DisplayName("7. 记录不存在时也应 403（避免用 200/403 差异探测 id 是否存在）")
    void nonExistentRecordIsDenied() throws Exception {
        assertDenied(perform("/xinlilaoshiLiuyan/info/999999", studentToken), "不存在的留言");
        assertDenied(perform("/yonghu/info/999999", studentToken), "不存在的学生");
    }

    @Test
    @DisplayName("8. 无 Token：仍是既有契约 HTTP 200 + body.code=401（本批不改 401）")
    void withoutTokenStillReturnsExisting401Contract() throws Exception {
        MvcResult result = perform("/xinlilaoshiLiuyan/info/2", null);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus())
                .as("未认证仍是 HTTP 200（D1 已锁定：本批不改 HTTP 401）")
                .isEqualTo(200);
        assertThat(body.path("code").asInt())
                .as("未认证 body.code 应为 401")
                .isEqualTo(UNAUTHENTICATED_BODY_CODE);
    }

    // ================== 工具方法 ==================

    private void assertAllowed(MvcResult result, String label) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus())
                .as("%s 应放行（HTTP 200），实际 body=%s", label, body)
                .isEqualTo(200);
        assertThat(body.path("code").asInt())
                .as("%s 应放行（code=0）", label)
                .isZero();
    }

    /** 断言「已认证但无权」的新契约：HTTP 403 + body.code=403 + msg="无权访问" */
    private void assertDenied(MvcResult result, String label) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus())
                .as("%s 应被拒绝（HTTP 403），实际 body=%s", label, body)
                .isEqualTo(FORBIDDEN);
        assertThat(body.path("code").asInt())
                .as("%s 的 body.code 应为 403（而非 401 —— 401 只表示未认证）", label)
                .isEqualTo(FORBIDDEN);
        assertThat(body.path("msg").asText())
                .as("%s 的提示语应统一为「%s」", label, DENY_MSG)
                .isEqualTo(DENY_MSG);
    }

    private MvcResult perform(String uri, String token) throws Exception {
        MockHttpServletRequestBuilder builder = get(uri);
        if (token != null) {
            builder = builder.header("Token", token);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode getJson(String uri, String token) throws Exception {
        return objectMapper.readTree(perform(uri, token).getResponse().getContentAsString());
    }
}
