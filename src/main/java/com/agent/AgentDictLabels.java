package com.agent;

import com.entity.SysDictItemEntity;
import com.service.SysDictItemService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字典码 → 中文标签（Tool 层）。
 *
 * <p><b>为什么需要它</b>：Tool 返回的是给 LLM 看的契约。
 * 若返回 {@code status=2}、{@code timeSlot=3}、{@code gender=1}，模型只能<b>猜</b>含义，
 * 极易编造（例：把「拒绝」说成「已同意」）。
 * 因此字典翻译必须在<b>代码里确定性完成</b>，而不是交给模型推理。
 *
 * <p><b>为什么不直接复用 {@code DictionaryService}</b>：那是 Web 层的视图转换器，
 * 依赖 {@code HttpServletRequest}（语言/请求上下文）；Tool 层没有请求语义，
 * 直接按 {@code dict_code} 查表更贴合「Tool 只依赖 Service」的边界。
 *
 * <p><b>缓存</b>：按 {@code dict_code} 缓存一次查询结果（字典是低频变更的配置数据）。
 * ⚠️ 代价：进程内不感知运行时字典修改；如需热更新应改为 TTL 缓存（当前不做）。
 *
 * <p><b>兜底</b>：字典缺失时返回<b>原码字符串</b>（如 {@code "3"}），
 * 不返回 {@code null}、不编造标签 —— 让模型至少看到真实取值而不是空白。
 */
public class AgentDictLabels {

    private final SysDictItemService dictItemService;
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    public AgentDictLabels(SysDictItemService dictItemService) {
        this.dictItemService = dictItemService;
    }

    /**
     * @param dictCode 字典类型（如 {@code appointment_status} / {@code time_slot} / {@code gender}）
     * @param code     字典项编码
     * @return 中文标签；字典缺失时返回原码字符串；{@code code} 为 {@code null} 时返回 {@code null}
     */
    public String label(String dictCode, Integer code) {
        if (code == null) {
            return null;
        }
        Map<String, String> items = cache.computeIfAbsent(dictCode, this::load);
        return items.getOrDefault(String.valueOf(code), String.valueOf(code));
    }

    private Map<String, String> load(String dictCode) {
        Map<String, String> items = new HashMap<>();
        List<SysDictItemEntity> rows = dictItemService.lambdaQuery()
                .eq(SysDictItemEntity::getDictCode, dictCode)
                .list();
        for (SysDictItemEntity row : rows) {
            if (row.getItemCode() != null && row.getItemName() != null) {
                items.put(String.valueOf(row.getItemCode()), row.getItemName());
            }
        }
        return items;
    }
}
