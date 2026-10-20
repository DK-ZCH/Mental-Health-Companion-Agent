<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listCounselors, type Counselor } from '@/api/counselor'

const items = ref<Counselor[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listCounselors()
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
      <h2>心理老师</h2>
      <div class="meta">
        <span class="count" data-test="counselor-count">共 {{ items.length }} 位</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-empty v-else-if="!items.length && !error" description="暂无老师信息" />

    <el-row v-else :gutter="16">
      <el-col v-for="c in items" :key="c.id" :xs="24" :sm="12" :md="8" class="col">
        <el-card class="item" shadow="hover" :data-test="'counselor-item-' + c.id">
          <div class="top">
            <img v-if="c.avatarUrl" :src="c.avatarUrl" class="avatar" alt="" />
            <div class="who">
              <router-link :to="'/counselors/' + c.id" class="name">
                {{ c.name || '（未命名）' }}
              </router-link>
              <div class="sub">{{ c.genderLabel || '' }}</div>
            </div>
          </div>
          <p class="expertise">{{ c.expertise || '' }}</p>
        </el-card>
      </el-col>
    </el-row>

    <p class="tip">数据来源：<code>GET /api/counselor/list</code>（公开接口；页面仅展示安全字段）</p>
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
.top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  object-fit: cover;
  background: #f5f7fa;
}
.who .name {
  font-weight: 600;
  color: #303133;
  text-decoration: none;
}
.who .name:hover {
  color: #409eff;
}
.who .sub {
  color: #909399;
  font-size: 12px;
}
.expertise {
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
.tip {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
