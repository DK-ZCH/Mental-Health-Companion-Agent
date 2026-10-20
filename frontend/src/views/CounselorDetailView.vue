<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listCounselors, type Counselor } from '@/api/counselor'
import { findById } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const item = ref<Counselor | null>(null)
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
    // G1 过渡策略：公开 /list 全表 + 前端按 id 匹配
    const list = await listCounselors()
    const hit = findById(list, id.value)
    if (hit) item.value = hit
    else notFound.value = true
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/**
 * 收藏 / 预约 / 留言 —— **仅入口状态，不调用任何写 API** ✗
 * （R11/R12/R13 属后续批次；避免"按钮看起来能用但后端写权限/参数/归属未完成"的假完成）
 */
function action(_kind: '收藏' | '预约' | '留言') {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  ElMessage.info('该功能将在后续批次开放')
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <el-button size="small" @click="router.back()">返回</el-button>
      <span class="crumb">心理老师 / {{ id }}</span>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="6" animated />

    <el-empty v-else-if="notFound" description="未找到该老师" />

    <el-card v-else-if="item" class="card">
      <div class="top">
        <img v-if="item.avatarUrl" :src="item.avatarUrl" class="avatar" alt="" />
        <div>
          <h2 class="name" data-test="counselor-detail-name">{{ item.name || '（未命名）' }}</h2>
          <div class="sub">{{ item.genderLabel || '' }}</div>
        </div>
      </div>

      <el-descriptions :column="1" border class="desc">
        <el-descriptions-item label="擅长领域">{{ item.expertise || '-' }}</el-descriptions-item>
        <el-descriptions-item label="从业经历">{{ item.resume || '-' }}</el-descriptions-item>
        <el-descriptions-item label="个人简介">{{ item.introduction || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="ops">
        <el-button type="primary" data-test="op-favorite" @click="action('收藏')">收藏</el-button>
        <el-button data-test="op-appointment" @click="action('预约')">预约</el-button>
        <el-button data-test="op-message" @click="action('留言')">留言</el-button>
      </div>
      <p class="hint">
        以上三个入口当前仅做状态判断（未登录 → 去登录），**尚未接入写操作**（后续批次实现）
      </p>
    </el-card>

    <p class="tip">数据来源：<code>GET /api/counselor/list</code>（G1 过渡策略；仅安全字段）</p>
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
.top {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}
.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  background: #f5f7fa;
}
.name {
  margin: 0;
  font-size: 20px;
}
.sub {
  color: #909399;
  font-size: 12px;
}
.desc {
  margin-bottom: 14px;
}
.ops {
  display: flex;
  gap: 10px;
}
.hint {
  margin: 10px 0 0;
  color: #909399;
  font-size: 12px;
}
.tip {
  margin: 12px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
