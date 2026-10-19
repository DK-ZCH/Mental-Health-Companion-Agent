<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listKnowledge, type KnowledgeArticle } from '@/api/content'

const items = ref<KnowledgeArticle[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listKnowledge()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h2>健康知识</h2>
      <div class="meta">
        <span class="count" data-test="knowledge-count">共 {{ items.length }} 条</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-empty v-else-if="!items.length && !error" description="暂无数据" />

    <el-row v-else :gutter="16">
      <el-col v-for="it in items" :key="it.id" :xs="24" :sm="12" :md="8" class="col">
        <el-card class="item" shadow="hover" :data-test="'knowledge-item-' + it.id">
          <div class="title">{{ it.title || '（无标题）' }}</div>
          <div class="time">{{ it.createdAt || '' }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.head h2 {
  margin: 0;
  font-size: 18px;
}
.meta {
  display: flex;
  align-items: center;
  gap: 12px;
}
.count {
  color: #606266;
  font-size: 13px;
}
.err {
  margin-bottom: 12px;
}
.col {
  margin-bottom: 16px;
}
.item .title {
  font-weight: 600;
  margin-bottom: 6px;
}
.item .time {
  color: #909399;
  font-size: 12px;
}
</style>
