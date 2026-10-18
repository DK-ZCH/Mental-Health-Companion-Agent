package com.security;

import com.baomidou.mybatisplus.annotation.TableId;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 空更新防护（Phase 2 / <b>T1</b> · 方向 A：<b>参数错误 → HTTP 400</b>）。
 *
 * <p><b>解决的问题</b>：{@code /update} 以 {@code @RequestBody Entity} 接收载荷，
 * 而 MyBatis-Plus 的 {@code updateById} 按 {@code NOT_NULL} 策略<b>只拼接非空字段</b>。
 * 若提交的字段全部为 null（例如只提交 {@code id}，或只提交 {@code @JsonIgnore} 字段），
 * 生成的 SQL 会退化为 {@code UPDATE x SET  WHERE id=?} —— <b>空 SET ⇒ SQL 语法错误 ⇒ 500</b>。
 *
 * <p><b>定位（T1 Phase 1 只读审计结论，分类 ②）</b>：这不是正常业务流缺陷 ——
 * 前端 <b>7/7</b> 个 {@code /update} 调用点均提交完整表单 ⇒ 该形态<b>只能由非 UI 调用产生</b>；
 * 故按「<b>非法 / 不完整客户端请求 ⇒ 应定义参数错误语义</b>」处理。
 * ⚠️ 这<b>不是</b>「看到 500 就机械改成 400」：真正的服务端内部异常仍保持 500 ✓。
 *
 * <p><b>调用位置（重要）</b>：必须位于<b>授权通过之后、DAO 之前</b> ——
 * 以保证「未授权仍 403、授权后参数不足才 400」的既有层级不被颠倒。
 *
 * @see BadRequestException
 */
public final class UpdateGuard {

    private UpdateGuard() {
    }

    /**
     * 断言实体<b>除主键外至少有一个非空字段</b>（与 MyBatis-Plus {@code NOT_NULL} 策略对齐）。
     *
     * <p>主键识别：优先 {@code @TableId}；同时跳过字段名为 {@code id} 的字段（双保险，
     * 避免个别实体未标注注解时把主键误判为「可更新字段」而让本防护失效）。
     *
     * @throws BadRequestException 除主键外全部为 null（提交后将生成空 {@code SET}）
     */
    public static void assertHasUpdatableFields(Object entity) {
        if (entity == null) {
            throw new BadRequestException("请求体不能为空");
        }
        Class<?> type = entity.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                if (field.isAnnotationPresent(TableId.class) || "id".equals(field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    if (field.get(entity) != null) {
                        return;
                    }
                } catch (IllegalAccessException e) {
                    // 读不到就保守放行：本原语只处理「确定为空」的情形，不制造新的拒绝面
                    return;
                }
            }
            type = type.getSuperclass();
        }
        throw new BadRequestException("请求中没有可更新的字段（除主键外至少需要一个字段）");
    }
}
