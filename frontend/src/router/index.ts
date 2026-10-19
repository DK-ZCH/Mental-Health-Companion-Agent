import { createRouter, createWebHistory } from 'vue-router'
import PublicLayout from '@/layouts/PublicLayout.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'

/**
 * Step 7 第一批只落地两条路由：
 *   /        → 首页（公开，展示真实 /knowledgeArticle/list 数据）
 *   /login   → 登录（真实 /student/login）
 * 其余路由在后续批次按 Step 6 §3 路由表逐条加入。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: PublicLayout,
      children: [{ path: '', name: 'home', component: HomeView }]
    },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

export default router
