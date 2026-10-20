<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listCounselors, type Counselor } from '@/api/counselor'
import { findById, ApiError } from '@/api/http'
import {
  addFavorite,
  findFavoriteByCounselor,
  removeFavorite,
  type FavoriteItem
} from '@/api/favorite'
import { addAppointment } from '@/api/appointment'
import { addMessage } from '@/api/message'
import { listDictItems, type DictItem } from '@/api/dict'
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

/** 收藏状态（仅已登录时查询；`/list?counselorId=` 过滤实测生效 ✓） */
const fav = ref<FavoriteItem | null>(null)
const favLoading = ref(false)

async function loadFavorite() {
  if (!auth.isLoggedIn) {
    fav.value = null
    return
  }
  try {
    fav.value = (await findFavoriteByCounselor(id.value)) ?? null
  } catch {
    fav.value = null
  }
}

/**
 * 收藏 / 取消收藏（R11）
 *  · 新增：`/add`（`favoriteType` 由 api 层固定为 1；studentId 服务端强制本人 ✓）
 *  · 取消：**只用本条记录的自有 id**（后端 `/delete` 无归属校验 = SEC-FAVORITE-DELETE-IDOR P1）
 */
async function toggleFavorite() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  favLoading.value = true
  try {
    if (fav.value?.id) {
      await removeFavorite(fav.value.id)
      ElMessage.success('已取消收藏')
      fav.value = null
    } else {
      await addFavorite(id.value)
      ElMessage.success('收藏成功')
      await loadFavorite()
    }
  } catch (e: unknown) {
    if (e instanceof ApiError && e.code === 511) {
      ElMessage.warning(e.message || '您已经收藏过了')
      await loadFavorite()
    } else {
      ElMessage.error(e instanceof Error ? e.message : '操作失败')
    }
  } finally {
    favLoading.value = false
  }
}

/** 预约表单（时段**字典驱动** ✓，不硬编码 ✗） */
const bookingVisible = ref(false)
const bookingSubmitting = ref(false)
const bookingError = ref('')
const slots = ref<DictItem[]>([])
const bookingForm = reactive({ appointmentDate: '', timeSlot: '' })

async function loadSlots() {
  if (slots.value.length) return
  try {
    slots.value = await listDictItems('time_slot')
  } catch {
    slots.value = []
  }
}

function todayStr(): string {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function openBooking() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  bookingError.value = ''
  bookingForm.appointmentDate = todayStr()
  bookingForm.timeSlot = ''
  bookingVisible.value = true
  void loadSlots()
}

/**
 * 提交预约：`/add`（`status` 由 api 层固定 1；`studentId` 服务端强制 ✓）
 * ⚠️ 「日期不得早于今天」是**前端约束** ✗ —— 后端无此校验（`DEF-APPOINTMENT-NO-PAST-CHECK` P2）
 *    因此只能表述为「前端阻止过去日期请求」，不能表述为「系统不允许」✗
 */
async function submitBooking() {
  bookingError.value = ''
  if (!bookingForm.appointmentDate) {
    bookingError.value = '请选择预约日期'
    return
  }
  if (bookingForm.appointmentDate < todayStr()) {
    bookingError.value = '不能预约过去的日期（前端校验；后端暂无此约束）'
    return
  }
  if (!bookingForm.timeSlot) {
    bookingError.value = '请选择时间段'
    return
  }

  bookingSubmitting.value = true
  try {
    await addAppointment({
      counselorId: id.value,
      appointmentDate: bookingForm.appointmentDate,
      timeSlot: bookingForm.timeSlot
    })
    ElMessage.success('预约已提交，等待老师审核')
    bookingVisible.value = false
  } catch (e: unknown) {
    bookingError.value = e instanceof Error ? e.message : '提交失败'
  } finally {
    bookingSubmitting.value = false
  }
}

/**
 * 留言（R13）：`POST /counselorMessage/add`
 * · **只提交** `counselorId` + `content` ✓（`studentId` 服务端强制 ✓；不提交 `replyContent` ✗）
 * · `replyContent` 客户端可绑定属 `SEC-MESSAGE-REPLY-SPOOF`（已实测可落库 ✗，本批不修）
 */
const msgVisible = ref(false)
const msgSubmitting = ref(false)
const msgError = ref('')
const msgContent = ref('')

function openMessage() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  msgError.value = ''
  msgContent.value = ''
  msgVisible.value = true
}

async function submitMessage() {
  msgError.value = ''
  const content = msgContent.value.trim()
  if (!content) {
    msgError.value = '请输入留言内容'
    return
  }
  msgSubmitting.value = true
  try {
    await addMessage({ counselorId: id.value, content })
    ElMessage.success('留言已提交')
    msgVisible.value = false
  } catch (e: unknown) {
    msgError.value = e instanceof Error ? e.message : '提交失败'
  } finally {
    msgSubmitting.value = false
  }
}

onMounted(async () => {
  await load()
  await loadFavorite()
})
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
        <el-button
          :type="fav ? 'success' : 'primary'"
          :loading="favLoading"
          data-test="op-favorite"
          @click="toggleFavorite"
        >
          {{ fav ? '已收藏（点击取消）' : '收藏' }}
        </el-button>
        <el-button data-test="op-appointment" @click="openBooking">预约</el-button>
        <el-button data-test="op-message" @click="openMessage">留言</el-button>
      </div>
      <p class="hint">
        收藏、预约、留言均已接入（预约时段来自后端字典 time_slot；留言的回复由老师侧处理）
      </p>
    </el-card>

    <el-dialog v-model="bookingVisible" title="预约心理咨询" width="420px">
      <el-alert
        v-if="bookingError"
        :title="bookingError"
        type="error"
        show-icon
        :closable="false"
        class="err"
      />
      <el-form label-position="top">
        <el-form-item label="预约日期">
          <el-date-picker
            v-model="bookingForm.appointmentDate"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            data-test="booking-date"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="时间段">
          <el-empty v-if="!slots.length" description="时段字典为空" :image-size="50" />
          <el-radio-group v-else v-model="bookingForm.timeSlot" data-test="booking-slot">
            <el-radio-button
              v-for="s in slots"
              :key="s.itemCode"
              :value="s.itemCode || ''"
              :data-test="'slot-' + s.itemCode"
            >
              {{ s.itemName || s.itemCode }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <p class="hint">
        前端限制：仅可预约今天及以后（后端无此校验 ⚠️）；时间段来自后端字典 <code>time_slot</code>
      </p>
      <template #footer>
        <el-button :disabled="bookingSubmitting" @click="bookingVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="bookingSubmitting"
          data-test="booking-submit"
          @click="submitBooking"
        >
          提交预约
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="msgVisible" title="给老师留言" width="460px">
      <el-alert
        v-if="msgError"
        :title="msgError"
        type="error"
        show-icon
        :closable="false"
        class="err"
      />
      <el-form label-position="top">
        <el-form-item :label="'留言给：' + (item?.name || '')">
          <el-input
            v-model="msgContent"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="请描述你的困扰或想咨询的问题"
            data-test="detail-message-content"
          />
        </el-form-item>
      </el-form>
      <p class="hint">留言提交后不可修改；回复由老师侧处理，可在「我的留言」查看</p>
      <template #footer>
        <el-button :disabled="msgSubmitting" @click="msgVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="msgSubmitting"
          data-test="detail-message-submit"
          @click="submitMessage"
        >
          提交
        </el-button>
      </template>
    </el-dialog>

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
