import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { setAuthFailureHandler } from './api/http'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia).use(router).use(ElementPlus)

/**
 * API 层判定「登录失效」（HTTP 200 + code=401，或标准 HTTP 401）后的统一处理：
 *   1) 清 Pinia + 4 个 localStorage key（复用 store.logout，单一实现）
 *   2) 跳 /login 并带 redirect，登录后可回到原页面
 * 注意：403（无权访问）**不会**走到这里 —— 由 api/http.ts 直接抛 ApiError(403)。
 */
setAuthFailureHandler((redirectPath) => {
  try {
    useAuthStore(pinia).logout()
  } catch {
    // store 尚未就绪时忽略（例如应用启动极早期）
  }
  if (router.currentRoute.value.name === 'login') return
  router.replace({ path: '/login', query: { redirect: redirectPath } })
})

app.mount('#app')
