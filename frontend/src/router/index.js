import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      // 主布局：顶部导航 + 内容区，登录后的页面都挂在它下面
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
        },
        {
          path: 'prompt/create',
          name: 'prompt-create',
          component: () => import('@/views/PromptCreateView.vue'),
        },
        {
          // 动态路由：:id 是参数，/prompt/1 和 /prompt/2 都命中这条
          // 注意：静态路由 /prompt/create 优先级高于动态参数，不会被误匹配
          path: 'prompt/:id',
          name: 'prompt-detail',
          component: () => import('@/views/PromptDetailView.vue'),
        },
      ],
    },
  ],
})

// 全局前置守卫：每次路由跳转前执行
// 未登录（无token）只允许去 /login，其余一律踢回登录页
router.beforeEach((to) => {
  const token = getToken()
  if (!token && to.path !== '/login') {
    return '/login'
  }
  // 已登录还访问登录页，直接送回首页
  if (token && to.path === '/login') {
    return '/'
  }
})

export default router
