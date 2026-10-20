<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMyFavorites, removeFavorite, type FavoriteItem } from '@/api/favorite'
import { ApiError } from '@/api/http'

const items = ref<FavoriteItem[]>([])
const loading = ref(false)
const error = ref('')
const removingId = ref<number | null>(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listMyFavorites()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function cancel(fav: FavoriteItem) {
  // 只使用列表返回的自有 id（后端 /delete 无归属校验，不得由 counselorId 推导）
  if (!fav.id) return
  try {
    await ElMessageBox.confirm('确定取消收藏该老师吗？', '取消收藏', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  removingId.value = fav.id
  try {
    await removeFavorite(fav.id)
    ElMessage.success('已取消收藏')
    await load()
  } catch (e: unknown) {
    if (e instanceof ApiError && e.code === 511) ElMessage.warning(e.message)
    else ElMessage.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    removingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h2>我的收藏</h2>
      <div class="meta">
        <span class="count" data-test="favorite-count">共 {{ items.length }} 条</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-empty v-else-if="!items.length && !error" description="暂无收藏" />

    <el-row v-else :gutter="16">
      <el-col v-for="f in items" :key="f.id" :xs="24" :sm="12" :md="8" class="col">
        <el-card class="item" shadow="hover" :data-test="'favorite-item-' + f.id">
          <div class="top">
            <img v-if="f.counselorAvatarUrl" :src="f.counselorAvatarUrl" class="avatar" alt="" />
            <div>
              <router-link :to="'/counselors/' + f.counselorId" class="name">
                {{ f.counselorName || '（未命名）' }}
              </router-link>
              <div class="sub">
                <el-tag v-if="f.favoriteTypeLabel" size="small">{{ f.favoriteTypeLabel }}</el-tag>
                <span class="time">{{ f.favoritedAt || f.createdAt || '' }}</span>
              </div>
            </div>
          </div>
          <p v-if="f.counselorExpertise" class="expertise">{{ f.counselorExpertise }}</p>
          <div class="ops">
            <el-button
              size="small"
              type="danger"
              plain
              :loading="removingId === f.id"
              :data-test="'favorite-remove-' + f.id"
              @click="cancel(f)"
            >
              取消收藏
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <p class="tip">
      数据来源：<code>GET /api/counselorFavorite/list</code>（服务端按当前学生过滤）；取消使用该列表返回的记录 id
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
.col {
  margin-bottom: 16px;
}
.top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  background: #f5f7fa;
}
.name {
  font-weight: 600;
  color: #303133;
  text-decoration: none;
}
.name:hover {
  color: #409eff;
}
.sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}
.time {
  color: #909399;
  font-size: 12px;
}
.expertise {
  margin: 0 0 8px;
  color: #606266;
  font-size: 13px;
}
.ops {
  display: flex;
  justify-content: flex-end;
}
.tip {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
