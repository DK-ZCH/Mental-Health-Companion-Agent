package com.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 试题 options 的安全投影 / 中性化（S2-B）。
 *
 * <p><b>职责边界</b>：本类只做「原始 options 字符串 → 安全 options 字符串」的转换，
 * <b>不判断角色 / 是否匿名 / 是否管理员</b> —— 角色分流一律由调用方（Controller）决定 ✗。
 *
 * <p>输入结构（实测）：{@code [{"code":"A","text":"答案A"}, ...]} 或 {@code []}。
 * 输出规则：
 * <ul>
 *   <li>解析失败 / 非数组 → {@code "[]"}（fail-closed）</li>
 *   <li>元素只保留白名单键 {@code code}/{@code text}；{@code code} 为空则丢弃该元素</li>
 *   <li>{@code codesOnly=true} → {@code text} 一律替换为 {@code "选项"+code}（不输出原始文案）</li>
 *   <li>{@code codesOnly=false} → 仅当 {@code text} 命中「答案泄露检测词」时，
 *       把<b>整条 text</b> 替换为 {@code "选项"+code}（不是子串删除）</li>
 * </ul>
 *
 * <p>检测词是<b>阻断已确认泄露的最小集合</b>，不是自然语言内容审核；无法穷举所有语义，
 * 根治依赖数据治理（DB 修正 + 管理端维护校验，独立任务）。普通描述（如
 * “这是一个关于答案的普通描述”）<b>不应</b>被误伤。
 */
public final class QuestionOptionSanitizer {

    /** 答案泄露检测词：断言“哪一个是正确/答案”的表达 */
    private static final Pattern DISCLOSURE = Pattern.compile(
            "正确|标准答|正解|答案为|答案是|应选|本题|此题|选\\s*[A-Da-d]|√|✔");

    private QuestionOptionSanitizer() {
    }

    /**
     * @param optionsJson 原始 options（数据库 {@code assessment_question.options}）
     * @param codesOnly   是否只输出 code + 中性标签（匿名/无调用方场景）
     * @return 安全投影后的 JSON 字符串
     */
    public static String sanitize(String optionsJson, boolean codesOnly) {
        if (optionsJson == null || optionsJson.trim().isEmpty()) {
            return "[]";
        }

        JSONArray arr;
        try {
            Object parsed = JSON.parse(optionsJson);
            if (!(parsed instanceof JSONArray)) {
                return "[]";
            }
            arr = (JSONArray) parsed;
        } catch (Exception e) {
            return "[]";
        }

        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            Object el = arr.get(i);
            if (!(el instanceof JSONObject)) {
                continue;
            }
            JSONObject o = (JSONObject) el;
            String code = o.getString("code");
            if (code == null || code.trim().isEmpty()) {
                continue;
            }
            code = code.trim();
            String text = o.getString("text");

            String safeText;
            if (codesOnly) {
                safeText = "选项" + code;
            } else if (text == null || text.trim().isEmpty()) {
                safeText = "选项" + code;
            } else if (DISCLOSURE.matcher(text).find()) {
                safeText = "选项" + code;
            } else {
                safeText = text;
            }

            JSONObject n = new JSONObject();
            n.put("code", code);
            n.put("text", safeText);
            out.add(n);
        }
        return JSON.toJSONString(out);
    }
}
