<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  listKnowledge,
  listNotifications,
  type KnowledgeArticle,
  type NotificationItem
} from '@/api/content'
import { listCounselors, type Counselor } from '@/api/counselor'

/** 首页 IA（R1）：知识 / 通知 / 老师 三块，全部复用既有公开 API（零新 API） */
const knowledge = ref<KnowledgeArticle[]>([])
const notifications = ref<NotificationItem[]>([])
const counselors = ref<Counselor[]>([])

const loading = ref(false)
const error = ref('')

const PREVIEW = 3

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [k, n, c] = await Promise.all([listKnowledge(), listNotifications(), listCounselors()])
    knowledge.value = k
    notifications.value = n
    counselors.value = c
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
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else>
      <!-- 知识 -->
      <section class="block">
        <div class="block-head">
          <h2>健康知识</h2>
          <div class="right">
            <span class="count" data-test="knowledge-count">共 {{ knowledge.length }} 条</span>
            <router-link to="/knowledge" class="more" data-test="more-knowledge">
              查看更多 →
            </router-link>
          </div>
        </div>
        <el-empty v-if="!knowledge.length" description="暂无内容" :image-size="60" />
        <el-row v-else :gutter="16">
          <el-col v-for="it in knowledge.slice(0, PREVIEW)" :key="it.id" :xs="24" :sm="12" :md="8">
            <router-link :to="'/knowledge/' + it.id" class="card-link">
              <el-card class="item" shadow="hover" :data-test="'knowledge-item-' + it.id">
                <div class="title">{{ it.title || '（无标题）' }}</div>
                <div class="sub">
                  <span>{{ it.categoryLabel || '' }}</span>
                  <span class="time">{{ it.publishedAt || it.createdAt || '' }}</span>
                </div>
              </el-card>
            </router-link>
          </el-col>
        </el-row>
      </section>

      <!-- 通知 -->
      <section class="block">
        <div class="block-head">
          <h2>通知公告</h2>
          <div class="right">
            <span class="count" data-test="notification-count">共 {{ notifications.length }} 条</span>
            <router-link to="/notifications" class="more" data-test="more-notifications">
              查看更多 →
            </router-link>
          </div>
        </div>
        <el-empty v-if="!notifications.length" description="暂无通知" :image-size="60" />
        <el-card v-else shadow="never" class="list-card">
          <div v-for="n in notifications.slice(0, PREVIEW)" :key="n.id" class="row">
            <router-link :to="'/notifications/' + n.id" class="row-title" :data-test="'notification-item-' + n.id">
              {{ n.title || '（无标题）' }}
            </router-link>
            <span class="time">{{ n.publishedAt || n.createdAt || '' }}</span>
          </div>
        </el-card>
      </section>

      <!-- 老师 -->
      <section class="block">
        <div class="block-head">
          <h2>心理老师</h2>
          <div class="right">
            <span class="count" data-test="counselor-count">共 {{ counselors.length }} 位</span>
            <router-link to="/counselors" class="more" data-test="more-counselors">
              查看更多 →
            </router-link>
          </div>
        </div>
        <el-empty v-if="!counselors.length" description="暂无老师信息" :image-size="60" />
        <el-row v-else :gutter="16">
          <el-col v-for="c in counselors.slice(0, PREVIEW)" :key="c.id" :xs="24" :sm="12" :md="8">
            <router-link :to="'/counselors/' + c.id" class="card-link">
              <el-card class="item" shadow="hover" :data-test="'counselor-item-' + c.id">
                <div class="top">
                  <img v-if="c.avatarUrl" :src="c.avatarUrl" class="avatar" alt="" />
                  <div>
                    <div class="title">{{ c.name || '（未命名）' }}</div>
                    <div class="sub">{{ c.genderLabel || '' }}</div>
                  </div>
                </div>
                <p class="expertise">{{ c.expertise || '' }}</p>
              </el-card>
            </router-link>
          </el-col>
        </el-row>
      </section>
    </template>

    <p class="tip">
      数据来源：<code>GET /api/knowledgeArticle/list</code> ·
      <code>GET /api/notification/list</code> · <code>GET /api/counselor/list</code>（均为公开接口）
    </p>
  </div>
</template>

<style scoped>
.err {
  margin-bottom: 12px;
}
.block {
  margin-bottom: 24px;
}
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.block-head h2 {
  margin: 0;
  font-size: 18px;
}
.right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.count {
  color: #606266;
  font-size: 13px;
}
.more {
  color: #409eff;
  font-size: 13px;
  text-decoration: none;
}
.card-link {
  text-decoration: none;
  color: inherit;
}
.item {
  margin-bottom: 12px;
}
.item .title {
  font-weight: 600;
  margin-bottom: 6px;
}
.sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #909399;
  font-size: 12px;
}
.time {
  color: #909399;
  font-size: 12px;
}
.list-card {
  padding: 4px 0;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px;
  border-bottom: 1px dashed #ebeef5;
}
.row:last-child {
  border-bottom: none;
}
.row-title {
  color: #303133;
  text-decoration: none;
  font-size: 14px;
}
.row-title:hover {
  color: #409eff;
}
.top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  background: #f5f7fa;
}
.expertise {
  margin: 0;
  color: #606266;
  font-size: 13px;
}
.tip {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
