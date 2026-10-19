<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const displayName = computed(() => (auth.isLoggedIn ? auth.username || '已登录' : '未登录'))

function goLogin() {
  router.push('/login')
}
function logout() {
  auth.logout()
  router.push('/')
}
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="brand">心理健康陪伴系统</div>
      <div class="right">
        <span class="who">{{ displayName }}</span>
        <el-button v-if="!auth.isLoggedIn" type="primary" size="small" @click="goLogin">登录</el-button>
        <el-button v-else size="small" @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100%;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.brand {
  font-size: 16px;
  font-weight: 600;
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.who {
  color: #909399;
  font-size: 13px;
}
</style>
