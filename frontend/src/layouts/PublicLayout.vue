<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const displayName = computed(() => (auth.isLoggedIn ? auth.username || '已登录' : '未登录'))

function goLogin() {
  router.push('/login')
}
function logout() {
  auth.logout()
  // 受保护页退出后必须离开：守卫不会因本地状态变化自动重跑
  if (route.meta.requiresAuth) router.replace('/login')
  else router.push('/')
}
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="brand">心理健康陪伴系统</div>
      <nav class="nav">
        <router-link to="/" class="nav-link" data-test="nav-home">首页</router-link>
        <router-link to="/knowledge" class="nav-link" data-test="nav-knowledge">知识</router-link>
        <router-link to="/notifications" class="nav-link" data-test="nav-notifications">
          通知
        </router-link>
        <router-link to="/counselors" class="nav-link" data-test="nav-counselors">老师</router-link>
        <router-link to="/me" class="nav-link" data-test="nav-me">我的信息</router-link>
        <router-link to="/me/records" class="nav-link" data-test="nav-records">我的记录</router-link>
        <router-link to="/me/wrong-questions" class="nav-link" data-test="nav-wrong">
          错题
        </router-link>
        <router-link to="/me/favorites" class="nav-link" data-test="nav-favorites">
          收藏
        </router-link>
        <router-link to="/me/appointments" class="nav-link" data-test="nav-appointments">
          预约
        </router-link>
        <router-link to="/me/messages" class="nav-link" data-test="nav-messages">
          留言
        </router-link>
      </nav>
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
.nav {
  display: flex;
  align-items: center;
  gap: 18px;
}
.nav-link {
  color: #606266;
  font-size: 14px;
  text-decoration: none;
}
.nav-link.router-link-active {
  color: #409eff;
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
