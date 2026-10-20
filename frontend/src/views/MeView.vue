<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getCurrentStudent, type CurrentStudent } from '@/api/student'

const me = ref<CurrentStudent | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    me.value = await getCurrentStudent()
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
      <h2>我的信息</h2>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-card v-else-if="me" class="card">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户名">
          <span data-test="me-username">{{ me.username || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="姓名">
          <span data-test="me-name">{{ me.name || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="性别">{{ me.gender || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机">{{ me.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ me.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ me.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <p class="tip">数据来源：<code>GET /api/student/session</code>（需登录，令牌由拦截器自动注入）</p>
    </el-card>

    <el-empty v-else-if="!error" description="未获取到用户信息" />
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
.err {
  margin-bottom: 12px;
}
.card {
  max-width: 760px;
}
.tip {
  margin: 12px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
