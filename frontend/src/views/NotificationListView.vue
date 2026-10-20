<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listNotifications, type NotificationItem } from '@/api/content'

const items = ref<NotificationItem[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listNotifications()
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
      <h2>通知公告</h2>
      <div class="meta">
        <span class="count" data-test="notification-count">共 {{ items.length }} 条</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-empty v-else-if="!items.length && !error" description="暂无通知" />

    <el-table v-else :data="items" border stripe data-test="notification-table">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="标题" min-width="220">
        <template #default="{ row }">
          <router-link :to="'/notifications/' + row.id" class="link">
            {{ row.title || '（无标题）' }}
          </router-link>
        </template>
      </el-table-column>
      <el-table-column prop="categoryLabel" label="分类" width="140" />
      <el-table-column prop="publishedAt" label="发布时间" width="180" />
    </el-table>

    <p class="tip">数据来源：<code>GET /api/notification/list</code>（公开接口）</p>
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
.link {
  color: #409eff;
  text-decoration: none;
}
.tip {
  margin: 12px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
