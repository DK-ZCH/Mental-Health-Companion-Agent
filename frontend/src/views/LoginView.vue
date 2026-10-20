<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

/** 登录后回跳目标：仅接受站内绝对路径（防开放重定向） */
function redirectTarget(): string {
  const raw = route.query.redirect
  const path = typeof raw === 'string' ? raw : ''
  if (path.startsWith('/') && !path.startsWith('//')) return path
  return '/'
}

async function submit() {
  error.value = ''
  if (!form.username || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.replace(redirectTarget())
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <el-card class="card">
      <h2 class="title">学生登录</h2>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="例如 a1" clearable />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />
        <el-button type="primary" :loading="loading" class="submit" @click="submit">登录</el-button>
      </el-form>
      <p class="hint">测试账号（来自数据库）：a1 / 123456</p>
    </el-card>
  </div>
</template>

<style scoped>
.wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}
.card {
  width: 380px;
}
.title {
  margin: 0 0 16px;
  font-size: 18px;
}
.err {
  margin-bottom: 12px;
}
.submit {
  width: 100%;
}
.hint {
  margin: 14px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
