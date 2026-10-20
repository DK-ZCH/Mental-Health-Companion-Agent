<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listAssessmentRecords, type AssessmentRecord } from '@/api/assessment'

const items = ref<AssessmentRecord[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const r = await listAssessmentRecords()
    items.value = r.list
    total.value = r.total
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
      <h2>测评记录</h2>
      <div class="meta">
        <span class="count" data-test="record-count">共 {{ total }} 条</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-empty v-else-if="!items.length && !error" description="暂无测评记录" />

    <el-table v-else :data="items" border stripe data-test="record-table">
      <el-table-column prop="recordNo" label="记录编号" width="170" />
      <el-table-column prop="paperName" label="试卷" min-width="160" />
      <el-table-column label="得分" width="120">
        <template #default="{ row }">
          <span data-test="record-score">{{ row.totalScore ?? '-' }}</span>
          <span class="muted"> / {{ row.paperTotalScore ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="180" />
    </el-table>

    <p class="tip">
      数据来源：<code>GET /api/assessmentRecord/list</code>（需登录 + DataScope：学生仅本人记录）
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
.muted {
  color: #909399;
  font-size: 12px;
}
.tip {
  margin: 12px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
