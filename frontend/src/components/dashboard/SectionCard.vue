<script setup lang="ts">
/**
 * Dashboard 区块外壳（Step 8 · 仅服务 Dashboard）
 *
 * 职责：统一「标题 + 右上操作 + 内容 / 骨架 / 错误+重试」的结构。
 * 不职责：不取数、不做业务判断、不吞掉 401/403（由 api/http.ts 统一处理）。
 */
withDefaults(
  defineProps<{
    title: string
    count?: number | string
    moreTo?: string
    moreText?: string
    loading?: boolean
    error?: string
  }>(),
  { moreText: '查看更多 →', loading: false, error: '' }
)

defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="block">
    <div class="block-head">
      <h2>{{ title }}</h2>
      <div class="right">
        <span v-if="count !== undefined && count !== null" class="count" :data-test="'count-' + title">
          共 {{ count }} 条
        </span>
        <router-link v-if="moreTo" :to="moreTo" class="more" :data-test="'more-' + title">
          {{ moreText }}
        </router-link>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="3" animated />

    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false">
      <el-button size="small" class="retry" @click="$emit('retry')">重试</el-button>
    </el-alert>

    <slot v-else />
  </section>
</template>

<style scoped>
.block {
  margin-bottom: 24px;
}
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.block-head h2 {
  margin: 0;
  font-size: 18px;
}
.right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.count {
  color: #606266;
  font-size: 13px;
}
.more {
  color: #409eff;
  font-size: 13px;
  text-decoration: none;
}
.retry {
  margin-top: 8px;
}
</style>
