/**
 * Step 25 前端计划状态：关键状态转换测试（审查修正 2026-11-06）。
 *
 * 零新增依赖：Node 内置 node:test + 项目已有 esbuild 把 TS 纯函数模块编译为 ESM。
 * 运行：node --test frontend/tests/planState.test.mjs
 *
 * 覆盖用户点名的前后端状态一致性问题：
 *  ① 请求失败 → 不再显示可确认按钮
 *  ② 倒计时归零 → 草案自动转 EXPIRED，按钮隐藏
 *  ③ 未过期草稿 → 可确认
 *  ④ 服务端响应优先于前端推导（组件以 result.plan.state 覆盖）
 */
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { transform } from 'esbuild'

const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(here, '../src/utils/planState.ts'), 'utf8')
// 移除 type-only import（运行时无依赖），其余 TS 语法由 esbuild 转译
const js = (await transform(source.replace(/^import type .*$/m, ''), {
  loader: 'ts',
  format: 'esm'
})).code
const mod = await import('data:text/javascript;base64,' + Buffer.from(js).toString('base64'))
const { remainingSeconds, planStateOnTick, planStateOnFailure, canConfirm } = mod

test('T1. remainingSeconds：正常、边界、超时、无截止时间', () => {
  assert.equal(remainingSeconds(10_000, 7_000), 3)
  assert.equal(remainingSeconds(7_500, 7_000), 1)   // 向上取整，不提前显示 0
  assert.equal(remainingSeconds(7_000, 7_000), 0)
  assert.equal(remainingSeconds(1_000, 7_000), 0)   // 超时下限 0
  assert.equal(remainingSeconds(null, 7_000), 0)
})

test('T2. 🔴 请求失败：清空可确认状态（不再显示失效确认按钮）', () => {
  assert.equal(planStateOnFailure(), null)
  assert.equal(canConfirm(planStateOnFailure(), 300), false)
})

test('T3. 🔴 倒计时归零：DRAFT 自动转 EXPIRED，确认按钮隐藏', () => {
  assert.equal(planStateOnTick('DRAFT', 1), 'DRAFT')
  assert.equal(planStateOnTick('DRAFT', 0), 'EXPIRED')
  assert.equal(canConfirm('DRAFT', 0), false)
  assert.equal(canConfirm(planStateOnTick('DRAFT', 0), 0), false)
})

test('T4. 未过期草稿：仍可确认；其它状态一律不可确认', () => {
  assert.equal(canConfirm('DRAFT', 300), true)
  assert.equal(canConfirm('DRAFT', 1), true)
  for (const state of ['CONFIRMED', 'DISCARDED', 'EXPIRED', 'NO_DRAFT', null]) {
    assert.equal(canConfirm(state, 300), false, `state=${state} 不得可确认`)
  }
})

test('T5. tick 不得把已确认/已放弃/无草稿状态误改为 EXPIRED', () => {
  for (const state of ['CONFIRMED', 'DISCARDED', 'NO_DRAFT', null]) {
    assert.equal(planStateOnTick(state, 0), state)
  }
})

test('T6. 服务端响应优先：真实流程以 result.plan.state 覆盖前端推导', () => {
  // 前端倒计时归零推导为 EXPIRED；若服务端仍返回 CONFIRMED，组件赋值后应以服务端为准
  let view = planStateOnTick('DRAFT', 0)
  assert.equal(view, 'EXPIRED')
  view = 'CONFIRMED' // 等价于组件中 planState.value = result.plan.state
  assert.equal(view, 'CONFIRMED')
  assert.equal(canConfirm(view, 0), false)
})
