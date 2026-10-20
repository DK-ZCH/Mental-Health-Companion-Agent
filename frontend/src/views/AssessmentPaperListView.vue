<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listPapers, type AssessmentPaperItem } from '@/api/assessment'

const items = ref<AssessmentPaperItem[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listPapers()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function statusType(s?: number): 'success' | 'info' {
  return s === 1 ? 'success' : 'info'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h2>测评试卷</h2>
      <div class="meta">
        <span class="count" data-test="paper-count">共 {{ items.length }} 份</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-empty v-else-if="!items.length && !error" description="暂无试卷" />

    <div v-else class="grid">
      <el-card v-for="p in items" :key="p.id" shadow="hover" :data-test="'paper-card-' + p.id">
        <div class="card-head">
          <h3 class="name" :data-test="'paper-name-' + p.id">{{ p.name || '（未命名）' }}</h3>
          <el-tag :type="statusType(p.status)" size="small" :data-test="'paper-status-' + p.id">
            {{ p.statusLabel || p.status }}
          </el-tag>
        </div>
        <div class="facts">
          <span class="fact">
            <span class="label">总分</span>
            <span class="value">{{ p.totalScore ?? '-' }}</span>
          </span>
          <span class="fact">
            <span class="label">时长</span>
            <span class="value">{{ p.durationMinutes ?? '-' }} 分钟</span>
          </span>
        </div>
      </el-card>
    </div>

    <p class="tip">
      数据来源：<code>GET /api/assessmentPaper/list</code>（公开只读）。本页仅展示试卷信息，
      在线答题暂未开放
    </p>
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
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.name {
  margin: 0;
  font-size: 16px;
}
.facts {
  display: flex;
  gap: 22px;
}
.fact {
  display: flex;
  flex-direction: column;
}
.label {
  color: #909399;
  font-size: 12px;
}
.value {
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}
.tip {
  margin: 14px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
