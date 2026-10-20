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

    <el-empty v-else-if="!items.length && !error" description="暂无内容" />

    <el-row v-else :gutter="16">
      <el-col v-for="it in items" :key="it.id" :xs="24" :sm="12" :md="8" class="col">
        <el-card class="item" shadow="hover" :data-test="'knowledge-item-' + it.id">
          <img v-if="it.coverUrl" :src="it.coverUrl" class="cover" alt="" />
          <router-link :to="'/knowledge/' + it.id" class="title link">
            {{ it.title || '（无标题）' }}
          </router-link>
          <div class="sub">
            <span>{{ it.categoryLabel || '' }}</span>
            <span class="time">{{ it.publishedAt || it.createdAt || '' }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <p class="tip">数据来源：<code>GET /api/knowledgeArticle/list</code>（公开接口）</p>
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
.cover {
  width: 100%;
  height: 140px;
  object-fit: cover;
  border-radius: 4px;
  margin-bottom: 8px;
  background: #f5f7fa;
}
.item .title {
  font-weight: 600;
  margin-bottom: 6px;
}
.item .link {
  display: block;
  color: #303133;
  text-decoration: none;
}
.item .link:hover {
  color: #409eff;
}
.sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #909399;
  font-size: 12px;
}
.tip {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
