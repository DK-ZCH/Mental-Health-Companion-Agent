package com.agent;

import com.entity.CounselorEntity;
import com.service.CounselorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * T4：可预约咨询师名录（Phase 4 / Round 1）。
 *
 * <p><b>数据形态</b>：<b>公开资源</b> —— 与其他三个 Tool 的访问模式刻意不同：
 * <pre>
 * T1/T2/T3：身份 → WHERE student_id = 我      （行级归属过滤）
 * T4      ：身份 → 【只校验域】→ 全表公开字段  （域白名单，不做行级过滤）
 * </pre>
 * 这正是本批要证明的第二类 Tool：并非所有能力都以「用户行归属」为前提。
 *
 * <p>🔴 <b>为什么必须显式 select 白名单列</b>：
 * {@code CounselorEntity} 含 {@code password / phone / email / username / avatarUrl}。
 * 若用 {@code list()} 或 {@code listByIds()} 全量取回再投影，
 * PII 与<b>密码哈希</b>已经进入内存与可能的日志 —— 属于「先泄露再遮挡」。
 * 因此这里在 SQL 层就只取 6 列。
 *
 * <p><b>为什么仍要限制身份域</b>：名录本身是公开数据，
 * 但「Agent 能为谁调用这个能力」是<b>另一层决策</b>。
 * 当前仅学生域（咨询师/管理员在 Agent 中的能力尚未定义，见 Step 12 裁定 A）。
 */
public class CounselorCatalogTools {

    private static final Logger logger = LoggerFactory.getLogger(CounselorCatalogTools.class);

    /** 单次返回上限（工程约束）*/
    static final int MAX_RECORDS = 10;

    private final CounselorService counselorService;
    private final AgentDictLabels labels;

    public CounselorCatalogTools(CounselorService counselorService, AgentDictLabels labels) {
        this.counselorService = counselorService;
        this.labels = labels;
    }

    /**
     * 列出可预约的咨询师（公开字段）。
     *
     * <p>⚠️ 返回<b>不含</b> {@code phone / email / username / password / avatarUrl}。
     */
    @Tool(description = "列出平台上可预约的心理咨询师，"
            + "返回姓名、性别、擅长领域与简介，用于向学生推荐合适的咨询师。"
            + "不返回任何联系方式。无需任何参数。")
    public CounselorToolResult listAvailableCounselors(ToolContext toolContext) {
        AgentToolGuard.requireStudent(toolContext);

        long total = counselorService.count();
        if (total == 0L) {
            logger.debug("[AGENT][TOOL] listAvailableCounselors total=0");
            return CounselorToolResult.ok(List.of(), 0L);
        }

        // 🔒 SQL 层白名单：绝不 select phone / email / username / password / avatar_url
        List<CounselorEntity> rows = counselorService.lambdaQuery()
                .select(CounselorEntity::getId,
                        CounselorEntity::getName,
                        CounselorEntity::getGender,
                        CounselorEntity::getExpertise,
                        CounselorEntity::getResume,
                        CounselorEntity::getIntroduction)
                .orderByAsc(CounselorEntity::getId)
                .last("LIMIT " + MAX_RECORDS)
                .list();

        List<CounselorToolResult.Item> items = new ArrayList<>(rows.size());
        for (CounselorEntity row : rows) {
            items.add(new CounselorToolResult.Item(
                    row.getId(),
                    row.getName(),
                    labels.label("gender", row.getGender()),
                    row.getExpertise(),
                    row.getResume(),
                    row.getIntroduction()
            ));
        }

        logger.debug("[AGENT][TOOL] listAvailableCounselors total={} returned={}", total, items.size());
        return CounselorToolResult.ok(items, total);
    }
}
