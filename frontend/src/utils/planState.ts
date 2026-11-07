import type { PlanState } from '@/api/agent'

/**
 * Step 25：计划视图状态的确定性决策（审查修正 2026-11-06）。
 *
 * <p>刻意抽成零副作用纯函数：前端状态交互可脱离 Vue / 浏览器环境做自动化测试，
 * 且不需要引入新的测试框架或依赖。组件只负责计时与绑定。
 *
 * <p><b>边界</b>：前端推导只影响 UI（是否显示确认按钮、显示剩余秒数），
 * 不决定是否生效；最终状态一律以服务端响应的 {@code plan.state} 为准。
 */

/** 剩余有效秒数（向上取整，下限 0）；无截止时间 ⇒ 0。 */
export function remainingSeconds(expiresAt: number | null, now: number): number {
  if (expiresAt == null) return 0
  return Math.max(0, Math.ceil((expiresAt - now) / 1000))
}

/**
 * 每秒推进时的状态推导：仅「草稿 + 已到期」转为 EXPIRED，其余保持。
 * 服务端尚未校验前，这只是 UI 层的保守提示，不是状态变更。
 */
export function planStateOnTick(
  state: PlanState | null,
  remaining: number
): PlanState | null {
  if (state === 'DRAFT' && remaining <= 0) return 'EXPIRED'
  return state
}

/**
 * 请求失败后的状态：服务端是否仍持有草稿未知，
 * 因此保守清空本地可确认态，不再显示确认按钮，也不擅自发 DISCARD。
 */
export function planStateOnFailure(): null {
  return null
}

/** 是否显示「确认 / 放弃」按钮：只有未过期的草稿可确认。 */
export function canConfirm(state: PlanState | null, remaining: number): boolean {
  return state === 'DRAFT' && remaining > 0
}
