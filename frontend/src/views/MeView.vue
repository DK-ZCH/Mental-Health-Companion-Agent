<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCurrentStudent, updateStudent, type CurrentStudent } from '@/api/student'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const me = ref<CurrentStudent | null>(null)
const loading = ref(false)
const error = ref('')

const editing = ref(false)
const saving = ref(false)
const formError = ref('')

const form = reactive({
  name: '',
  phone: '',
  email: '',
  avatarUrl: ''
})

/** 身份只来自会话（绝不从 URL/表单取目标 id） */
const selfId = computed<number | null>(() => {
  const id = me.value?.id ?? auth.userId
  return typeof id === 'number' && id > 0 ? id : null
})

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

function startEdit() {
  formError.value = ''
  form.name = String(me.value?.name ?? '')
  form.phone = String(me.value?.phone ?? '')
  form.email = String(me.value?.email ?? '')
  form.avatarUrl = String(me.value?.avatarUrl ?? '')
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  formError.value = ''
}

/** 头像仅接受 http(s):// 或站内 /upload/…（不做任何 URL 抓取，避免引入 SSRF 面） */
function isAcceptableAvatar(url: string): boolean {
  if (!url) return true
  return /^https?:\/\//i.test(url) || url.startsWith('/upload/')
}

function validate(): string {
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone.trim())) return '手机号格式不正确（11 位，1 开头）'
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return '邮箱格式不正确'
  if (!isAcceptableAvatar(form.avatarUrl.trim())) {
    return '头像地址仅支持 http(s):// 开头或站内 /upload/ 路径'
  }
  return ''
}

function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 511) return '该手机号/账号/身份证号已被占用'
    if (e.code === 400) return '没有需要更新的内容'
    if (e.code === 403) return '无权修改该资料'
    return e.message
  }
  return e instanceof Error ? e.message : '保存失败'
}

async function save() {
  formError.value = ''
  const bad = validate()
  if (bad) {
    formError.value = bad
    return
  }
  if (selfId.value === null) {
    formError.value = '未获取到当前用户身份，请重新登录'
    return
  }

  saving.value = true
  try {
    // 只提交 4 个可改字段 + 会话 id（不含 username/gender/idCardNo/password/createdAt）
    await updateStudent({
      id: selfId.value,
      name: form.name.trim(),
      phone: form.phone.trim(),
      email: form.email.trim(),
      avatarUrl: form.avatarUrl.trim()
    })
    ElMessage.success('保存成功')
    editing.value = false
    await load()
  } catch (e: unknown) {
    formError.value = errorMessage(e)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h2>我的信息</h2>
      <div class="ops">
        <el-button v-if="!editing" size="small" :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="!editing" type="primary" size="small" :disabled="!me" @click="startEdit">
          编辑
        </el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="4" animated />

    <!-- 查看 -->
    <el-card v-else-if="me && !editing" class="card">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户名">
          <span data-test="me-username">{{ me.username || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="姓名">
          <span data-test="me-name">{{ me.name || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="性别">{{ me.gender || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机">
          <span data-test="me-phone">{{ me.phone || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="邮箱">
          <span data-test="me-email">{{ me.email || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ me.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <p class="tip">数据来源：<code>GET /api/student/session</code>（令牌由拦截器自动注入）</p>
    </el-card>

    <!-- 编辑（仅 4 个可改字段） -->
    <el-card v-else-if="editing" class="card">
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="err"
      />
      <el-form label-position="top">
        <el-form-item label="姓名">
          <el-input v-model="form.name" data-test="edit-name" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="form.phone" data-test="edit-phone" placeholder="11 位手机号" clearable />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" data-test="edit-email" placeholder="name@example.com" clearable />
        </el-form-item>
        <el-form-item label="头像地址">
          <el-input
            v-model="form.avatarUrl"
            data-test="edit-avatar"
            placeholder="http(s)://… 或 /upload/…"
            clearable
          />
          <div class="field-hint">
            本批不支持上传文件；仅接受 http(s):// 或站内 /upload/ 地址（不做服务端抓取）
          </div>
        </el-form-item>
      </el-form>
      <div class="ops">
        <el-button type="primary" :loading="saving" data-test="save" @click="save">保存</el-button>
        <el-button :disabled="saving" @click="cancelEdit">取消</el-button>
      </div>
      <p class="tip">
        说明：仅提交 <code>name</code> / <code>phone</code> / <code>email</code> /
        <code>avatarUrl</code>；用户名、性别、身份证号、密码、注册时间均由服务端管理。
      </p>
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
.ops {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}
.err {
  margin-bottom: 12px;
}
.card {
  max-width: 760px;
}
.field-hint {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.tip {
  margin: 12px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
