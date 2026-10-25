<script setup lang="ts">
/**
 * `/` 入口分流壳（Phase 3 / Step 8）
 *
 * 未登录 → 访客首页（知识 / 通知 / 老师，全部公开 API）
 * 已登录 → Dashboard（含需登录数据）
 *
 * 【为什么必须在这里分流】
 *   后端未登录返回 `HTTP 200 + body.code=401`，`api/http.ts` 收到后会
 *   清认证态并跳 `/login`。若在未登录状态下误发需登录请求，
 *   正在公开浏览的访客会被直接踢到登录页 —— 破坏公开浏览能力。
 *   ⇒ 因此「先判断登录态，再决定加载哪一组数据」，未登录**绝不进入** Dashboard。
 *
 * 【为什么用 v-if 而不是路由重定向】
 *   保留 `/` 的既有链接语义，避免刷新时出现跳转闪烁。
 */
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import HomeView from './HomeView.vue'
import DashboardView from './DashboardView.vue'

const auth = useAuthStore()
const isLoggedIn = computed(() => auth.isLoggedIn)
</script>

<template>
  <DashboardView v-if="isLoggedIn" />
  <HomeView v-else />
</template>
