<script setup lang="ts">
/**
 * 统一空状态（Step 8 · 仅服务 Dashboard）
 *
 * 职责边界：只渲染空态，**不生成任何推断性文案**（如「你最近心情不错」）。
 * 文案由调用方提供，必须是中性描述。
 */
withDefaults(
  defineProps<{
    description: string
    imageSize?: number
  }>(),
  { imageSize: 60 }
)
</script>

<template>
  <el-empty :description="description" :image-size="imageSize" />
</template>
