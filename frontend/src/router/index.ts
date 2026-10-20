import { createRouter, createWebHistory } from 'vue-router'
import PublicLayout from '@/layouts/PublicLayout.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import MeView from '@/views/MeView.vue'
import AssessmentRecordsView from '@/views/AssessmentRecordsView.vue'
import RecordDetailView from '@/views/RecordDetailView.vue'
import WrongQuestionListView from '@/views/WrongQuestionListView.vue'
import FavoriteListView from '@/views/FavoriteListView.vue'
import AppointmentListView from '@/views/AppointmentListView.vue'
import KnowledgeListView from '@/views/KnowledgeListView.vue'
import KnowledgeDetailView from '@/views/KnowledgeDetailView.vue'
import NotificationListView from '@/views/NotificationListView.vue'
import NotificationDetailView from '@/views/NotificationDetailView.vue'
import CounselorListView from '@/views/CounselorListView.vue'
import CounselorDetailView from '@/views/CounselorDetailView.vue'
import { TOKEN_KEY } from '@/api/http'

/**
 * Step 7 路由表（Batch 3 扩展 · 对齐 Step 6 §3.1）
 *   /                       公开    首页（知识列表）
 *   /login                  公开    学生登录
 *   /knowledge              公开    R4 知识列表（/knowledgeArticle/list）
 *   /knowledge/:id          公开    R5 知识详情（G1：/list + 前端按 id 匹配）
 *   /notifications          公开    R6 通知列表（/notification/list）
 *   /notifications/:id      公开    R7 通知详情（G1）
 *   /counselors             公开    R8 老师目录（/counselor/list，仅安全字段）
 *   /counselors/:id         公开    R9 老师详情（G1）+ 收藏/预约/留言入口
 *   /me                     受保护  R10 当前登录者（/student/session）
 *   /me/records             受保护  R16 测评记录（/assessmentRecord/list，DataScope）
 *   /assessmentRecords      —      Batch 2 旧口径 → redirect /me/records
 * 其余路由（R3/R11–R15/R17/R18）在后续批次加入。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: PublicLayout,
      children: [
        { path: '', name: 'home', component: HomeView },

        // ===== 访客内容浏览（Batch 3 · R4–R9，全部公开）=====
        { path: 'knowledge', name: 'knowledge', component: KnowledgeListView },
        { path: 'knowledge/:id', name: 'knowledgeDetail', component: KnowledgeDetailView },
        { path: 'notifications', name: 'notifications', component: NotificationListView },
        {
          path: 'notifications/:id',
          name: 'notificationDetail',
          component: NotificationDetailView
        },
        { path: 'counselors', name: 'counselors', component: CounselorListView },
        { path: 'counselors/:id', name: 'counselorDetail', component: CounselorDetailView },

        // ===== 用户中心（需登录）=====
        { path: 'me', name: 'me', component: MeView, meta: { requiresAuth: true } },
        {
          path: 'me/records',
          name: 'meRecords',
          component: AssessmentRecordsView,
          meta: { requiresAuth: true }
        },
        {
          path: 'me/records/:recordNo',
          name: 'meRecordDetail',
          component: RecordDetailView,
          meta: { requiresAuth: true }
        },
        {
          path: 'me/wrong-questions',
          name: 'meWrongQuestions',
          component: WrongQuestionListView,
          meta: { requiresAuth: true }
        },
        {
          path: 'me/favorites',
          name: 'meFavorites',
          component: FavoriteListView,
          meta: { requiresAuth: true }
        },
        {
          path: 'me/appointments',
          name: 'meAppointments',
          component: AppointmentListView,
          meta: { requiresAuth: true }
        },
        // 旧口径兼容：Batch 2 曾用 /assessmentRecords ⇒ 重定向到 Step 6 定义的 /me/records
        { path: 'assessmentRecords', redirect: '/me/records' }
      ]
    },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

/**
 * 守卫职责边界（Batch 2 设计原则）：
 *   守卫只回答「本地有没有登录凭证」；**不回答**「服务端是否认这个 token」。
 *   后者由 API 层负责：401 → 清态 + 回 /login?redirect=…（见 api/http.ts）。
 */
router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return true

  const token = localStorage.getItem(TOKEN_KEY)
  if (token) return true

  return { path: '/login', query: { redirect: to.fullPath } }
})

export default router
