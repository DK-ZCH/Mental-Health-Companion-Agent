<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listKnowledge, type KnowledgeArticle } from '@/api/content'
import { findById } from '@/api/http'

const route = useRoute()
const router = useRouter()

const item = ref<KnowledgeArticle | null>(null)
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

const id = computed(() => String(route.params.id ?? ''))

async function load() {
  loading.value = true
  error.value = ''
  notFound.value = false
  item.value = null
  try {
    // G1 过渡策略：公开 /list 全表 + 前端按 id 匹配（/info/{id} 需登录 ✗）
    const list = await listKnowledge()
    const hit = findById(list, id.value)
    if (hit) item.value = hit
    else notFound.value = true
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
      <el-button size="small" @click="router.back()">返回</el-button>
      <span class="crumb">健康知识 / {{ id }}</span>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="6" animated />

    <el-empty v-else-if="notFound" description="未找到该内容" />

    <el-card v-else-if="item" class="card">
      <h2 class="title" data-test="knowledge-detail-title">{{ item.title || '（无标题）' }}</h2>
      <div class="meta">
        <el-tag v-if="item.categoryLabel" size="small">{{ item.categoryLabel }}</el-tag>
        <span class="time">{{ item.publishedAt || item.createdAt || '' }}</span>
      </div>
      <img v-if="item.coverUrl" :src="item.coverUrl" class="cover" alt="" />
      <p class="content" data-test="knowledge-detail-content">{{ item.content || '' }}</p>
    </el-card>

    <p class="tip">
      说明：详情数据来自公开接口 <code>GET /api/knowledgeArticle/list</code>（G1 过渡策略，不额外暴露后端信息）
    </p>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.crumb {
  color: #909399;
  font-size: 12px;
}
.err {
  margin-bottom: 12px;
}
.card {
  max-width: 860px;
}
.title {
  margin: 0 0 10px;
  font-size: 20px;
}
.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.time {
  color: #909399;
  font-size: 12px;
}
.cover {
  width: 100%;
  max-height: 280px;
  object-fit: cover;
  border-radius: 4px;
  margin-bottom: 12px;
  background: #f5f7fa;
}
.content {
  margin: 0;
  line-height: 1.8;
  white-space: pre-wrap;
  color: #303133;
}
.tip {
  margin: 12px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
