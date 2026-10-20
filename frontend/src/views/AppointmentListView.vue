<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMyAppointments, removeAppointment, type AppointmentItem } from '@/api/appointment'
import { ApiError } from '@/api/http'

const items = ref<AppointmentItem[]>([])
const loading = ref(false)
const error = ref('')
const removingId = ref<number | null>(null)

/** 状态标签（字典 appointment_status：1 待审核 / 2 同意 / 3 拒绝 —— 均由老师侧审核产生） */
function statusType(s?: number): 'warning' | 'success' | 'danger' | 'info' {
  if (s === 1) return 'warning'
  if (s === 2) return 'success'
  if (s === 3) return 'danger'
  return 'info'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listMyAppointments()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/**
 * 取消预约 = **物理删除**（系统无"已取消"状态 ✗；status=3 是老师审核拒绝 ✗）
 * · 仅对「待审核」提供取消入口（已同意/已拒绝的取消策略未定 ⚠️ → 本批不提供）
 * · 只使用列表返回的自有 id（后端 /delete 无归属校验 = SEC-APPOINTMENT-DELETE-IDOR P1）
 */
async function cancel(appt: AppointmentItem) {
  if (!appt.id || appt.status !== 1) return
  try {
    await ElMessageBox.confirm(
      `确定取消该预约吗？（${appt.appointmentDate ?? ''} ${appt.timeSlotLabel ?? ''}）`,
      '取消预约',
      { confirmButtonText: '确定', cancelButtonText: '返回', type: 'warning' }
    )
  } catch {
    return
  }

  removingId.value = appt.id
  try {
    await removeAppointment(appt.id)
    ElMessage.success('已取消预约')
    await load()
  } catch (e: unknown) {
    if (e instanceof ApiError) ElMessage.error(e.message)
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
      <h2>我的预约</h2>
      <div class="meta">
        <span class="count" data-test="appointment-count">共 {{ items.length }} 条</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-empty v-else-if="!items.length && !error" description="暂无预约" />

    <el-table v-else :data="items" border stripe data-test="appointment-table">
      <el-table-column prop="appointmentNo" label="流水号" width="170" />
      <el-table-column label="老师" min-width="140">
        <template #default="{ row }">
          <router-link :to="'/counselors/' + row.counselorId" class="link">
            {{ row.counselorName || '-' }}
          </router-link>
        </template>
      </el-table-column>
      <el-table-column prop="appointmentDate" label="日期" width="120" />
      <el-table-column prop="timeSlotLabel" label="时段" width="130" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small" :data-test="'appt-status-' + row.id">
            {{ row.statusLabel || row.status || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核意见" min-width="140">
        <template #default="{ row }">{{ row.reviewComment || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 1"
            size="small"
            type="danger"
            plain
            :loading="removingId === row.id"
            :data-test="'appointment-cancel-' + row.id"
            @click="cancel(row)"
          >
            取消预约
          </el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <p class="tip">
      数据来源：<code>GET /api/counselingAppointment/list</code>（按当前学生过滤）；取消为物理删除，仅对「待审核」开放
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
.link {
  color: #409eff;
  text-decoration: none;
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
