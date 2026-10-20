<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getRecordByNo,
  listAnswersByRecordNo,
  type AssessmentAnswerItem,
  type AssessmentRecord
} from '@/api/assessment'

const route = useRoute()
const router = useRouter()

const record = ref<AssessmentRecord | null>(null)
const answers = ref<AssessmentAnswerItem[]>([])
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

const recordNo = computed(() => String(route.params.recordNo ?? ''))

/**
 * 数据来源（安全设计）：
 *   记录  ← /assessmentRecord/list（DataScope：A=1 / B=空 ✓）+ 前端按 recordNo 匹配
 *   明细  ← /assessmentAnswer/list?recordNo=（服务端强制本人 ✓，B 拿 A 的 recordNo 得空 ✓）
 * ⚠️ 不使用任何 detail 端点（SEC-DETAIL-IDOR 🔴 P1：缺 OwnershipGuard，B 可读 A）
 */
async function load() {
  loading.value = true
  error.value = ''
  notFound.value = false
  record.value = null
  answers.value = []
  try {
    const hit = await getRecordByNo(recordNo.value)
    if (!hit) {
      notFound.value = true
      return
    }
    record.value = hit
    answers.value = await listAnswersByRecordNo(recordNo.value)
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
      <span class="crumb">我的测评记录 / {{ recordNo }}</span>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="6" animated />

    <el-empty v-else-if="notFound" description="未找到该测评记录" />

    <template v-else-if="record">
      <el-card class="card">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="记录编号">
            <span data-test="record-no">{{ record.recordNo || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="试卷">{{ record.paperName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="得分">
            <span data-test="record-score">{{ record.totalScore ?? '-' }}</span>
            <span class="muted"> / {{ record.paperTotalScore ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ record.submittedAt || record.createdAt || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <div class="sub-head">
        <h3>答题明细</h3>
        <span class="count" data-test="answer-count">共 {{ answers.length }} 题</span>
      </div>

      <el-empty v-if="!answers.length" description="暂无答题明细" />

      <el-card v-for="(a, i) in answers" :key="a.id ?? i" class="q" shadow="never">
        <div class="q-title">
          <span class="idx">{{ i + 1 }}.</span>
          <span>{{ a.questionContent || '（无题干）' }}</span>
          <el-tag size="small" type="info" class="type">{{ a.questionTypeLabel || '' }}</el-tag>
        </div>
        <div class="q-line">
          <span class="label">我的答案：</span>
          <span class="mine">{{ a.studentAnswer || '（未作答）' }}</span>
        </div>
        <div class="q-line">
          <span class="label">正确答案：</span>
          <span class="right">{{ a.questionAnswer || '-' }}</span>
        </div>
        <div class="q-line">
          <span class="label">得分：</span>
          <span>{{ a.score ?? '-' }} / {{ a.questionScore ?? '-' }}</span>
        </div>
        <div v-if="a.questionAnalysis" class="analysis">
          <span class="label">解析：</span>{{ a.questionAnalysis }}
        </div>
      </el-card>
    </template>

    <p class="tip">
      数据来源：<code>GET /api/assessmentRecord/list</code>（按 recordNo 匹配）+
      <code>GET /api/assessmentAnswer/list?recordNo=</code>（服务端强制本人）
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
  margin-bottom: 16px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.sub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.sub-head h3 {
  margin: 0;
  font-size: 16px;
}
.count {
  color: #606266;
  font-size: 13px;
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
.mine {
  color: #e6a23c;
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
