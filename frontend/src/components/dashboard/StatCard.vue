<script setup lang="ts">
/**
 * 单个指标卡（Step 8 · 仅服务 Dashboard）
 *
 * 职责边界：**只负责视觉呈现**，不取数、不计算、不做业务判断。
 * `tone` 只影响颜色，不代表任何心理/健康语义。
 */
withDefaults(
  defineProps<{
    label: string
    value: string | number
    hint?: string
    tone?: 'ok' | 'idle' | 'warn'
  }>(),
  { tone: 'idle', hint: '' }
)
</script>

<template>
  <div class="stat" :class="'tone-' + tone" :data-test="'stat-' + label">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value">{{ value }}</div>
    <div v-if="hint" class="stat-hint">{{ hint }}</div>
  </div>
</template>

<style scoped>
.stat {
  background: #fff;
  border: 1px solid #ebeef5;
  border-left: 3px solid #dcdfe6;
  border-radius: 6px;
  padding: 14px 16px;
  min-height: 82px;
}
.stat.tone-ok {
  border-left-color: #67c23a;
}
.stat.tone-warn {
  border-left-color: #e6a23c;
}
.stat.tone-idle {
  border-left-color: #dcdfe6;
}
.stat-label {
  color: #909399;
  font-size: 13px;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}
.stat-hint {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}
</style>
