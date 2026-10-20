<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listWrongQuestions, type WrongQuestionItem } from '@/api/assessment'

const items = ref<WrongQuestionItem[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listWrongQuestions()
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
      <h2>我的错题</h2>
      <div class="meta">
        <span class="count" data-test="wrong-count">共 {{ items.length }} 题</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="5" animated />

    <el-empty v-else-if="!items.length && !error" description="暂无错题" />

    <template v-else>
      <el-card v-for="(q, i) in items" :key="q.id ?? i" class="q" shadow="never">
        <div class="q-title">
          <span class="idx">{{ i + 1 }}.</span>
          <span data-test="wrong-question-content">{{ q.questionContent || '（无题干）' }}</span>
          <el-tag size="small" type="info" class="type">{{ q.questionTypeLabel || '' }}</el-tag>
        </div>
        <div class="q-line">
          <span class="label">我的答案：</span>
          <span class="mine">{{ q.studentAnswer || '（未作答）' }}</span>
        </div>
        <div class="q-line">
          <span class="label">正确答案：</span>
          <span class="right">{{ q.questionAnswer || '-' }}</span>
        </div>
        <div class="q-line">
          <span class="label">试卷：</span>
          <span>{{ q.paperName || '-' }}</span>
          <span class="label sep">答错时间：</span>
          <span>{{ q.answeredAt || '-' }}</span>
        </div>
        <div v-if="q.questionAnalysis" class="analysis">
          <span class="label">解析：</span>{{ q.questionAnalysis }}
        </div>
      </el-card>
    </template>

    <p class="tip">
      数据来源：<code>GET /api/assessmentWrongQuestion/list</code>（服务端强制本人；身份只来自令牌）
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
.q {
  margin-bottom: 12px;
}
.q-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 8px;
}
.idx {
  color: #909399;
}
.type {
  margin-left: auto;
}
.q-line {
  font-size: 13px;
  color: #303133;
  margin-bottom: 4px;
}
.label {
  color: #909399;
}
.label.sep {
  margin-left: 16px;
}
.mine {
  color: #f56c6c;
}
.right {
  color: #67c23a;
}
.analysis {
  font-size: 13px;
  color: #606266;
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #ebeef5;
}
.tip {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
