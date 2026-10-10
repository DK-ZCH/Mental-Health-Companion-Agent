package com.regression;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.entity.AuthTokenEntity;
import com.entity.AdminUserEntity;
import com.entity.CounselorFavoriteEntity;
import com.entity.CounselorEntity;
import com.entity.CounselorMessageEntity;
import com.entity.StudentEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.AuthTokenService;
import com.service.AdminUserService;
import com.service.CounselorFavoriteService;
import com.service.CounselorMessageService;
import com.security.CurrentUserProvider;
import com.security.ForbiddenException;
import com.service.CounselorService;
import com.service.StudentService;

import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 2 / Step 5 第二阶段：身份授权回归（批 1 + 批 2 + 批 3B + 批 4-A/4-B + D7）。
 *
 * <p>批 1 = 8 个 {@code /info/{id}} 的归属校验（用例 1–8）；
 * 批 2 = 密码重置越权 {@code /resetPassword?id=}（用例 9–11）。
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
    /** 学生 a1 在 {@code student} 表中的 id（种子数据） */
    private static final int STUDENT_A1_ID = 1;
    /** 心理老师 a1 在 {@code counselor} 表中的 id（种子数据） */
    private static final int COUNSELOR_A1_ID = 1;

    @Autowired
    private MockMvc mockMvc;

    /** 用于「密码前后快照」比对（零新增依赖 —— 复用已有 Service 读取实体） */
    @Autowired
    private StudentService yonghuService;

    @Autowired
    private CounselorService xinlilaoshiService;

    /** 批 3B：写路径归属断言所需 */
    @Autowired
    private CounselorMessageService xinlilaoshiLiuyanService;

    @Autowired
    private CounselorFavoriteService xinlilaoshiCollectionService;

    @Autowired
    private AuthTokenService tokenService;

    /** D7：断言被下线的管理端账号重置端点确实不再可调用 */
    @Autowired
    private AdminUserService usersService;

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

    // ================== 9–11. 批 2：密码重置越权（P0-3） ==================

    @Test
    @DisplayName("9. 管理员重置密码仍可用（管理端按钮的真实调用路径），且不产生意外变更")
    void passwordReset_adminCanStillResetWithoutUnexpectedChange() throws Exception {
        // 安全性关键：种子密码本就是 123456，因此「重置」在数据层是幂等的。
        // 若某天种子数据变了，下面这条断言会【先】失败 —— 从而避免测试真的改掉任何人的密码。
        assertThat(studentPassword(3)).as("前置条件：student 3 初始密码应为 123456").isEqualTo("123456");
        assertThat(counselorPassword(3)).as("前置条件：counselor 3 初始密码应为 123456").isEqualTo("123456");

        assertAllowed(perform("/yonghu/resetPassword?id=3", adminToken), "管理员重置学生密码");
        assertAllowed(perform("/xinlilaoshi/resetPassword?id=3", adminToken), "管理员重置老师密码");

        assertThat(studentPassword(3)).as("重置后密码仍应为 123456（无意外变更）").isEqualTo("123456");
        assertThat(counselorPassword(3)).as("重置后密码仍应为 123456（无意外变更）").isEqualTo("123456");
    }

    @Test
    @DisplayName("10. 学生/老师重置他人密码：应 403，且数据库中的目标密码【不发生变化】")
    void passwordReset_isDeniedAndTargetDataUnchanged() throws Exception {
        String studentBefore = studentPassword(2);
        String counselorBefore = counselorPassword(2);

        assertDenied(perform("/yonghu/resetPassword?id=2", studentToken), "学生重置他人（学生）密码");
        assertDenied(perform("/yonghu/resetPassword?id=2", counselorToken), "老师重置他人（学生）密码");
        assertDenied(perform("/xinlilaoshi/resetPassword?id=2", studentToken), "学生重置他人（老师）密码");
        assertDenied(perform("/xinlilaoshi/resetPassword?id=2", counselorToken), "老师重置他人（老师）密码");

        // 关键：不仅断言「请求被拒绝」，还要断言「目标数据确实没有被改动」
        assertThat(studentPassword(2)).as("student 2 的密码不得因越权请求而变化").isEqualTo(studentBefore);
        assertThat(counselorPassword(2)).as("counselor 2 的密码不得因越权请求而变化").isEqualTo(counselorBefore);
    }

    @Test
    @DisplayName("11. 无 Token 调用密码重置：仍是既有契约 HTTP 200 + code=401，且不改动任何密码")
    void passwordReset_withoutTokenKeeps401Contract() throws Exception {
        String before = studentPassword(3);

        MvcResult result = perform("/yonghu/resetPassword?id=3", null);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).as("未认证仍是 HTTP 200").isEqualTo(200);
        assertThat(body.path("code").asInt()).as("未认证 body.code 应为 401").isEqualTo(UNAUTHENTICATED_BODY_CODE);
        assertThat(studentPassword(3)).as("未认证请求不得改动任何密码").isEqualTo(before);
    }

    /** 读取学生当前密码（作为「前后快照」） */
    private String studentPassword(int id) {
        StudentEntity entity = yonghuService.getById(id);
        return entity == null ? null : entity.getPassword();
    }

    /** 读取心理老师当前密码（作为「前后快照」） */
    private String counselorPassword(int id) {
        CounselorEntity entity = xinlilaoshiService.getById(id);
        return entity == null ? null : entity.getPassword();
    }

    // ================== 12–16. 批 3B：写路径归属授权 ==================

    @Test
    @DisplayName("12. 学生修改【自己】的记录：成功，且客户端伪造的 studentId 被忽略（服务端强制本人）")
    void studentCanUpdateOwnRecord_andForgedOwnerIsIgnored() throws Exception {
        // 留言 id2 属 student 1（a1 自己）；请求体里故意伪造 studentId=2
        assertThat(messageOwner(2)).as("前置条件：留言 id2 应属 student 1").isEqualTo(STUDENT_A1_ID);

        assertAllowed(postJson("/xinlilaoshiLiuyan/update", studentToken, "{\"id\":2,\"studentId\":2}"),
                "学生修改自己的留言");

        assertThat(messageOwner(2))
                .as("客户端伪造 studentId=2 必须被忽略：库中归属应仍为当前学生 %d", STUDENT_A1_ID)
                .isEqualTo(STUDENT_A1_ID);
    }

    @Test
    @DisplayName("13. 学生修改【他人】的记录：403，且数据库不变")
    void studentCannotUpdateOthersRecord() throws Exception {
        int beforeMessageOwner = messageOwner(1);
        int beforeFavoriteOwner = favoriteOwner(1);
        assertThat(beforeMessageOwner).as("前置条件：留言 id1 应属 student 2").isEqualTo(2);
        assertThat(beforeFavoriteOwner).as("前置条件：收藏 id1 应属 student 3").isEqualTo(3);

        assertDenied(postJson("/xinlilaoshiLiuyan/update", studentToken, "{\"id\":1,\"studentId\":1}"),
                "学生修改他人留言");
        assertDenied(postJson("/xinlilaoshiCollection/update", studentToken, "{\"id\":1,\"studentId\":1}"),
                "学生修改他人收藏");

        assertThat(messageOwner(1)).as("越权请求不得改动他人留言").isEqualTo(beforeMessageOwner);
        assertThat(favoriteOwner(1)).as("越权请求不得改动他人收藏").isEqualTo(beforeFavoriteOwner);
    }

    @Test
    @DisplayName("14. 管理员代目标学生修改：其显式 studentId 被保留（不得被覆盖成管理员自己）")
    void adminTargetStudentIsPreserved() throws Exception {
        // 用测试自建记录，避免污染种子数据（跑完即删）
        CounselorMessageEntity fixture = new CounselorMessageEntity();
        fixture.setStudentId(STUDENT_A1_ID);
        fixture.setCounselorId(1);
        xinlilaoshiLiuyanService.save(fixture);
        Integer fixtureId = fixture.getId();
        try {
            assertAllowed(postJson("/xinlilaoshiLiuyan/update", adminToken,
                            "{\"id\":" + fixtureId + ",\"studentId\":3}"),
                    "管理员代表目标学生修改");

            assertThat(messageOwner(fixtureId))
                    .as("管理员显式指定的目标学生(3)必须被保留 —— 这正是 /update 的注释覆盖不可机械恢复的原因")
                    .isEqualTo(3);
        } finally {
            xinlilaoshiLiuyanService.removeById(fixtureId);
        }
    }

    @Test
    @DisplayName("15. 两个 /add：客户端伪造 studentId → 数据库仍写入 Session 当前学生")
    void addEndpointsAlwaysStampCurrentStudent() throws Exception {
        // 学生 a1 伪造 studentId=3 提交留言与收藏（服务端应强制写为 1）
        assertAllowed(postJson("/xinlilaoshiLiuyan/add", studentToken,
                "{\"studentId\":3,\"counselorId\":3}"), "伪造归属提交留言");
        assertAllowed(postJson("/xinlilaoshiCollection/add", studentToken,
                "{\"studentId\":3,\"counselorId\":3,\"favoriteType\":2}"), "伪造归属提交收藏");

        QueryWrapper<CounselorMessageEntity> messageQuery = new QueryWrapper<CounselorMessageEntity>()
                .eq("student_id", STUDENT_A1_ID).eq("counselor_id", 3);
        QueryWrapper<CounselorFavoriteEntity> favoriteQuery = new QueryWrapper<CounselorFavoriteEntity>()
                .eq("student_id", STUDENT_A1_ID).eq("counselor_id", 3).eq("favorite_type", 2);
        try {
            // 种子数据中不存在 (当前学生, 老师3) 的留言/收藏 —— 若伪造生效，这里会查不到记录
            assertThat(xinlilaoshiLiuyanService.getOne(messageQuery))
                    .as("留言应以 Session 当前学生 %d 落库（伪造的 studentId=3 被忽略）", STUDENT_A1_ID)
                    .isNotNull();
            assertThat(xinlilaoshiCollectionService.getOne(favoriteQuery))
                    .as("收藏应以 Session 当前学生 %d 落库（伪造的 studentId=3 被忽略）", STUDENT_A1_ID)
                    .isNotNull();
        } finally {
            xinlilaoshiLiuyanService.remove(messageQuery);
            xinlilaoshiCollectionService.remove(favoriteQuery);
        }
    }

    @Test
    @DisplayName("16. 未知角色：读与写一律默认拒绝（fail-closed，不得默认全量）")
    void unknownRoleIsDenied() throws Exception {
        // 真实 token 表驱动的未知角色（跑完即删）；用于证明「未登记角色」不会获得任何权限
        String unknownToken = "batch3b-unknown-role-token";
        tokenService.save(new AuthTokenEntity(STUDENT_A1_ID, "a1", "student", "未知角色", unknownToken,
                new Date(System.currentTimeMillis() + 3_600_000L)));
        try {
            assertDenied(postJson("/xinlilaoshiLiuyan/update", unknownToken, "{\"id\":2,\"studentId\":1}"),
                    "未知角色执行写操作");
            assertDenied(perform("/xinlilaoshiLiuyan/info/2", unknownToken), "未知角色执行读操作");
        } finally {
            tokenService.remove(new QueryWrapper<AuthTokenEntity>().eq("token", unknownToken));
        }
    }

    // ================== 17–19. 批 4：读范围收敛（DataScope fail-closed） ==================

    @Test
    @DisplayName("17. 学生伪造 studentId 查询 page：仍只返回自己的记录（矩阵 3）")
    void studentPageScopeCannotBeForged() throws Exception {
        JsonNode body = getJson("/xinlilaoshiLiuyan/page?page=1&limit=10&studentId=2", studentToken);
        assertThat(body.path("code").asInt()).as("列表查询应成功").isZero();

        JsonNode data = body.path("data");
        assertThat(data.path("total").asInt())
                .as("学生 a1 的留言应为 2 条（其余属他人）—— 同时防空跑")
                .isEqualTo(2);
        for (JsonNode row : data.path("list")) {
            assertThat(row.path("studentId").asInt())
                    .as("伪造 studentId=2 不得放大读范围：返回行必须属于当前学生 %d", STUDENT_A1_ID)
                    .isEqualTo(STUDENT_A1_ID);
        }
    }

    @Test
    @DisplayName("18. 心理老师查询 page：只返回自己服务范围内的记录（矩阵 11）")
    void counselorPageIsScopedToSelf() throws Exception {
        JsonNode body = getJson("/xinlilaoshiLiuyan/page?page=1&limit=20", counselorToken);
        assertThat(body.path("code").asInt()).as("列表查询应成功").isZero();

        JsonNode data = body.path("data");
        assertThat(data.path("total").asInt())
                .as("前置：老师 a1 应有可见留言（防空跑）")
                .isGreaterThan(0);
        for (JsonNode row : data.path("list")) {
            assertThat(row.path("counselorId").asInt())
                    .as("老师侧读范围应收敛到 counselorId=%d", COUNSELOR_A1_ID)
                    .isEqualTo(COUNSELOR_A1_ID);
        }
    }

    @Test
    @DisplayName("19. 未知角色的 page：403（矩阵 12 —— 不得默认全量）")
    void unknownRolePageIsDenied() throws Exception {
        String unknownToken = "batch4-unknown-role-token";
        tokenService.save(new AuthTokenEntity(STUDENT_A1_ID, "a1", "student", "未知角色", unknownToken,
                new Date(System.currentTimeMillis() + 3_600_000L)));
        try {
            // 改造前：未知角色会跳过全部分支 → 不加任何过滤 → 返回全量（fail-open 缺口）
            assertDenied(perform("/xinlilaoshiLiuyan/page?page=1&limit=10", unknownToken),
                    "未知角色查询列表");
        } finally {
            tokenService.remove(new QueryWrapper<AuthTokenEntity>().eq("token", unknownToken));
        }
    }

    @Test
    @DisplayName("20. D7：匿名密码重置端点 /users/resetPass 已下线（404，且不产生任何密码变更）")
    void adminAccountAnonymousResetEndpointIsRemoved() throws Exception {
        String before = usersPassword("admin");
        assertThat(before).as("前置：admin 账号应存在").isNotNull();

        MvcResult result = perform("/users/resetPass?username=admin", null);
        assertThat(result.getResponse().getStatus())
                .as("该端点已下线，应为 404（而不是 200 或 403）")
                .isEqualTo(404);

        assertThat(usersPassword("admin"))
                .as("请求不得产生任何密码变更 —— 证明端点确实不再存在，而不只是改了返回码")
                .isEqualTo(before);

        // 对照：同名「忘记密码」端点（用户端）按 D3 保持不变 →
        //      证明上面的 404 是「该路径被删除」，而非「所有 /resetPass 都 404」或全局 404 行为
        MvcResult contrast = perform("/yonghu/resetPass?username=not-exist-user-xyz", null);
        assertThat(contrast.getResponse().getStatus())
                .as("对照：/yonghu/resetPass 属「忘记密码」流程，按 D3 保持存在")
                .isNotEqualTo(404);
        assertThat(usersPassword("admin")).as("对照请求也不得改动任何数据").isEqualTo(before);
    }

    // ================== 21–24. 批 4-B：自助资料更新（业务规则 Q2） ==================

    @Test
    @DisplayName("21. 学生改【自己】资料：成功；学号/性别/身份证号等固定字段被服务端恢复")
    void studentSelfProfileUpdate_preservesImmutableFields() throws Exception {
        StudentEntity before = yonghuService.getById(STUDENT_A1_ID);
        assertThat(before.getUsername()).as("前置：学号应存在（防空跑）").isNotNull();
        assertThat(before.getGender()).as("前置：性别应存在（防空跑）").isNotNull();

        assertAllowed(postJson("/yonghu/update", studentToken,
                "{\"id\":" + STUDENT_A1_ID + ",\"name\":\"" + before.getName()
                        + "\",\"username\":\"HACKED-NO\",\"gender\":9,\"idCardNo\":\"HACKED-ID-NO\"}"),
                "学生修改自己的资料");

        StudentEntity after = yonghuService.getById(STUDENT_A1_ID);
        assertThat(after.getUsername()).as("学号不得被自助修改").isEqualTo(before.getUsername());
        assertThat(after.getGender()).as("性别不得被自助修改").isEqualTo(before.getGender());
        assertThat(after.getIdCardNo()).as("身份证号不得被自助修改").isEqualTo(before.getIdCardNo());
    }

    @Test
    @DisplayName("22. 学生改【他人】资料：403，且目标记录（含密码）完全未变")
    void studentCannotUpdateOthersProfile() throws Exception {
        StudentEntity before = yonghuService.getById(2);

        assertDenied(postJson("/yonghu/update", studentToken,
                        "{\"id\":2,\"username\":\"HACKED-2\",\"password\":\"hacked-pass\"}"),
                "学生修改他人资料");

        StudentEntity after = yonghuService.getById(2);
        assertThat(after.getUsername()).as("他人学号不得被改动").isEqualTo(before.getUsername());
        assertThat(after.getPassword()).as("他人密码不得被改动（改造前此路径可绕过批 2 的密码保护）")
                .isEqualTo(before.getPassword());
    }

    @Test
    @DisplayName("23. 老师：改自己资料可成功（描述等非固定信息），改学生资料 403；工号/性别被恢复")
    void counselorSelfOnly_andCannotTouchStudentProfile() throws Exception {
        CounselorEntity before = xinlilaoshiService.getById(1);
        assertThat(before.getUsername()).as("前置：工号应存在（防空跑）").isNotNull();

        // 老师改【自己】：可改（含 expertise/resume/introduction 等非固定信息）
        assertAllowed(postJson("/xinlilaoshi/update", counselorToken,
                "{\"id\":1,\"name\":\"" + before.getName() + "\",\"resume\":\""
                        + before.getResume() + "\",\"username\":\"HACKED-3\",\"gender\":9}"),
                "老师修改自己的资料");

        CounselorEntity after = xinlilaoshiService.getById(1);
        assertThat(after.getUsername()).as("工号不得被自助修改").isEqualTo(before.getUsername());
        assertThat(after.getGender()).as("性别不得被自助修改").isEqualTo(before.getGender());

        // 老师改【学生】资料：拒绝（Q1 取证：老师对业务数据没有写入口，回复留言由管理员在管理端完成）
        StudentEntity studentBefore = yonghuService.getById(1);
        assertDenied(postJson("/yonghu/update", counselorToken,
                        "{\"id\":1,\"password\":\"hacked-by-counselor\"}"),
                "老师修改学生资料");
        assertThat(yonghuService.getById(1).getPassword())
                .as("学生密码不得被老师改动").isEqualTo(studentBefore.getPassword());
    }

    @Test
    @DisplayName("24. 管理员改学生资料：仍可执行（保持管理端既有编辑能力）")
    void adminCanStillEditStudentProfile() throws Exception {
        StudentEntity before = yonghuService.getById(2);

        assertAllowed(postJson("/yonghu/update", adminToken,
                "{\"id\":2,\"name\":\"" + before.getName() + "\"}"), "管理员编辑学生资料");

        StudentEntity after = yonghuService.getById(2);
        assertThat(after.getUsername()).as("管理员路径保持现状：未提交的字段不应变化")
                .isEqualTo(before.getUsername());
    }

    // ================== 25. 批 5 / A1：身份读取收敛的契约控制 ==================
    //
    // 审阅指出的覆盖缺口：既有 38 项回归**只覆盖正常登录路径**，无法证明「role 缺失」时的行为。
    // 本用例即针对该缺口的回归控制：若有人让 currentRole() 返回字符串 "null"、抛异常、
    // 或改变取值域，本用例必须失败。

    @Test
    @DisplayName("25. A1：currentRole 契约 —— role 缺失时返回真正的 null（不得返回字符串 \"null\"）")
    void currentRoleContractOnMissingRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(CurrentUserProvider.currentRole(request))
                .as("Session 无 role 时必须返回真正的 null —— 这是逐条确认后的语义收敛，不是机械替换的副产品")
                .isNull();

        request.getSession().setAttribute("role", "学生");
        assertThat(CurrentUserProvider.currentRole(request)).as("正常角色原样返回").isEqualTo("学生");

        request.getSession().setAttribute("role", 123);
        assertThat(CurrentUserProvider.currentRole(request))
                .as("非字符串取值按既有 String.valueOf 语义转字符串（行为不变）")
                .isEqualTo("123");

        request.getSession().removeAttribute("role");
        assertThat(CurrentUserProvider.currentRole(request)).as("移除后仍返回 null").isNull();
    }

    @Test
    @DisplayName("26. A2：userId 契约 —— 读自己允许缺失（null），写路径缺失必须 fail-closed 拒绝")
    void currentUserIdContract() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(CurrentUserProvider.currentUserIdOrNull(request)).as("允许缺失：返回 null").isNull();
        assertThatThrownBy(() -> CurrentUserProvider.requireCurrentUserId(request))
                .as("写路径缺失必须拒绝 —— 绝不能返回 null，否则归属赋值被 MP 跳过、客户端提交的归属值就会生效")
                .isInstanceOf(ForbiddenException.class);

        request.getSession().setAttribute("userId", 7);
        assertThat(CurrentUserProvider.currentUserIdOrNull(request)).isEqualTo(7);
        assertThat(CurrentUserProvider.requireCurrentUserId(request)).isEqualTo(7);
    }

    @Test
    @DisplayName("27. A2：「身份不完整」状态本身【不可构造】—— auth_token.user_id 由 schema 保证非空")
    void incompleteIdentityIsNotConstructible() {
        // 这条用例回答了「为什么 requireCurrentUserId 的 403 分支在真实链路中不可达」：
        //   身份进入 Session 的唯一来源 = AuthorizationInterceptor ← auth_token 行；
        //   而 auth_token.user_id 为 NOT NULL 且无默认值 → 无法构造出 user_id 为空的登录态。
        //
        // ⚠️ 它同时是一个**不变量守卫**：若有人把该约束放开，本用例必须失败 ——
        //    那时 requireCurrentUserId 的 fail-closed 分支就变成可达路径了。
        String token = "batch5-a2-null-userid-token";

        assertThatThrownBy(() -> tokenService.save(new AuthTokenEntity(null, "a1", "student", "学生", token,
                new Date(System.currentTimeMillis() + 3_600_000L))))
                .as("schema 必须拒绝 user_id 为空的 token")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(tokenService.getOne(new QueryWrapper<AuthTokenEntity>().eq("token", token)))
                .as("失败的插入不得留下任何 token 行")
                .isNull();
    }

    @Test
    @DisplayName("21. 管理端账号 分页/列表：仅管理员可访问（Step 5b-A）")
    void adminUserListIsAdminOnly() throws Exception {
        // 正面：管理员可访问 —— 防空跑，同时证明下面的 403 来自「角色不符」而非接口坏掉
        assertAllowed(perform("/users/page?page=1&limit=5", adminToken), "管理员访问管理端账号分页");
        // 注：/users/list 对【管理员】返回 500 —— 这是【既有故障】，与本批授权改动无关（已实证）：
        //     · 服务端异常为 BadSqlGrammarException：AdminUserDao.xml 中别名是 u，而控制器传 "user" 前缀
        //     · git 对比 HEAD 证明方法体一字未改（本批只加了 HttpServletRequest 参数与通过性守卫）
        //     · 该端点【无任何真实 API 调用方】：学生端 0 处；管理端唯一命中是 router-static.js 的
        //       模块导入路径 '@/views/modules/users/list'（不是 API 调用）⇒ 故从未被发现
        //     故本用例只断言「非管理员被拒」（守卫在方法体之前生效，不受该故障影响），
        //     该 500 已登记为独立观察项，不在本批顺手修改。

        // 反面：学生 / 心理老师一律拒绝（与 /users/info/{id} 同策略）
        assertDenied(perform("/users/page?page=1&limit=5", studentToken), "学生访问管理端账号分页");
        assertDenied(perform("/users/list", studentToken), "学生访问管理端账号列表");
        assertDenied(perform("/users/page?page=1&limit=5", counselorToken), "心理老师访问管理端账号分页");
        assertDenied(perform("/users/list", counselorToken), "心理老师访问管理端账号列表");
    }

    private String usersPassword(String username) {
        AdminUserEntity user = usersService.getOne(new QueryWrapper<AdminUserEntity>().eq("username", username));
        return user == null ? null : user.getPassword();
    }

    private int messageOwner(int id) {
        CounselorMessageEntity entity = xinlilaoshiLiuyanService.getById(id);
        return entity == null ? -1 : entity.getStudentId();
    }

    private int favoriteOwner(int id) {
        CounselorFavoriteEntity entity = xinlilaoshiCollectionService.getById(id);
        return entity == null ? -1 : entity.getStudentId();
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

    /** 发 JSON POST（写路径用例用） */
    private MvcResult postJson(String uri, String token, String json) throws Exception {
        MockHttpServletRequestBuilder builder = post(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (token != null) {
            builder = builder.header("Token", token);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode getJson(String uri, String token) throws Exception {
        return objectMapper.readTree(perform(uri, token).getResponse().getContentAsString());
    }
}
