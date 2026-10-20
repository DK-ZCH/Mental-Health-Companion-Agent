import { createRouter, createWebHistory } from 'vue-router'
import PublicLayout from '@/layouts/PublicLayout.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import MeView from '@/views/MeView.vue'
import AssessmentRecordsView from '@/views/AssessmentRecordsView.vue'
import { TOKEN_KEY } from '@/api/http'

/**
 * Step 7 路由表（Batch 2 扩展）
 *   /                      公开    健康知识（真实 /knowledgeArticle/list）
 *   /login                 公开    学生登录（真实 /student/login）
 *   /me                    受保护  当前登录者（真实 /student/session）
 *   /assessmentRecords     受保护  测评记录（真实 /assessmentRecord/list，DataScope）
 * 其余路由按 Step 6 §3 路由表在后续批次逐条加入。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: PublicLayout,
      children: [
        { path: '', name: 'home', component: HomeView },
        { path: 'me', name: 'me', component: MeView, meta: { requiresAuth: true } },
        {
          path: 'assessmentRecords',
          name: 'assessmentRecords',
          component: AssessmentRecordsView,
          meta: { requiresAuth: true }
        }
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
