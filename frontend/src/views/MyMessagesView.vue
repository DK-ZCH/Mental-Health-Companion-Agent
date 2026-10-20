<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { addMessage, listMyMessages, type MessageItem } from '@/api/message'
import { listCounselors, type Counselor } from '@/api/counselor'

const items = ref<MessageItem[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listMyMessages()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 老师目录（公开 `/counselor/list`；api 层已做安全字段投影 ✓） */
const counselors = ref<Counselor[]>([])
async function loadCounselors() {
  if (counselors.value.length) return
  try {
    counselors.value = await listCounselors()
  } catch {
    counselors.value = []
  }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formError = ref('')
const form = reactive({ counselorId: '', content: '' })

function openDialog() {
  formError.value = ''
  form.content = ''
  if (!form.counselorId && counselors.value[0]?.id) {
    form.counselorId = String(counselors.value[0].id)
  }
  dialogVisible.value = true
  void loadCounselors()
}

async function submit() {
  formError.value = ''
  if (!form.counselorId) {
    formError.value = '请选择心理老师'
    return
  }
  const content = form.content.trim()
  if (!content) {
    formError.value = '请输入留言内容'
    return
  }

  submitting.value = true
  try {
    // 只提交 counselorId + content（不提交 replyContent / repliedAt / studentId ✗）
    await addMessage({ counselorId: form.counselorId, content })
    ElMessage.success('留言已提交，回复由老师侧处理')
    dialogVisible.value = false
    form.content = ''
    await load()
  } catch (e: unknown) {
    formError.value = e instanceof Error ? e.message : '提交失败'
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await load()
  await loadCounselors()
})
</script>

<template>
  <div>
    <div class="head">
      <h2>我的留言</h2>
      <div class="meta">
        <span class="count" data-test="message-count">共 {{ items.length }} 条</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
        <el-button size="small" type="primary" data-test="message-new" @click="openDialog">
          写留言
        </el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-empty v-else-if="!items.length && !error" description="暂无留言" />

    <el-table v-else :data="items" border stripe data-test="message-table">
      <el-table-column prop="sentAt" label="留言时间" width="170" />
      <el-table-column label="老师" min-width="130">
        <template #default="{ row }">
          <router-link :to="'/counselors/' + row.counselorId" class="link">
            {{ row.counselorName || '-' }}
          </router-link>
        </template>
      </el-table-column>
      <el-table-column label="我的留言" min-width="220">
        <template #default="{ row }">
          <span class="content">{{ row.content || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="回复" min-width="220">
        <template #default="{ row }">
          <span v-if="row.replyContent" :data-test="'message-reply-' + row.id" class="reply">
            {{ row.replyContent }}
          </span>
          <span v-else class="muted" :data-test="'message-noreply-' + row.id">暂无回复</span>
        </template>
      </el-table-column>
      <el-table-column label="回复时间" width="170">
        <template #default="{ row }">
          <span :class="{ muted: !row.repliedAt }">{{ row.repliedAt || '—' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <p class="tip">
      数据来源：<code>GET /api/counselorMessage/list</code>（按当前学生过滤）；本系统为「留言 + 单条回复」，
      无未读状态；回复由管理端录入，学生端只读
    </p>

    <el-dialog v-model="dialogVisible" title="写留言" width="460px">
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="err"
      />
      <el-form label-position="top">
        <el-form-item label="心理老师">
          <el-empty v-if="!counselors.length" description="暂无可选老师" :image-size="50" />
          <el-radio-group v-else v-model="form.counselorId" data-test="message-counselor">
            <el-radio-button
              v-for="c in counselors"
              :key="c.id"
              :value="String(c.id)"
              :data-test="'message-counselor-' + c.id"
            >
              {{ c.name || ('#' + c.id) }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="留言内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="请描述你的困扰或想咨询的问题"
            data-test="message-content"
          />
        </el-form-item>
      </el-form>
      <p class="tip">留言提交后不可修改；回复由老师侧处理（本页不提供删除/回复）</p>
      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          data-test="message-submit"
          @click="submit"
        >
          提交
        </el-button>
      </template>
    </el-dialog>
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
.content {
  white-space: pre-wrap;
  word-break: break-word;
}
.reply {
  white-space: pre-wrap;
  word-break: break-word;
  color: #303133;
}
.muted {
  color: #c0c4cc;
}
.tip {
  margin: 12px 0 0;
  color: #909399;
  font-size: 12px;
}
</style>
