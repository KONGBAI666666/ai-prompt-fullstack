import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUser } from '@/utils/auth'

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
          // 编辑复用新增页面组件，页面内部根据有无 :id 切换新增/编辑模式
          path: 'prompt/edit/:id',
          name: 'prompt-edit',
          component: () => import('@/views/PromptCreateView.vue'),
        },
        {
          // 动态路由：/prompt/1、/prompt/2 都命中这条（静态路由 /prompt/create 优先匹配）
          path: 'prompt/:id',
          name: 'prompt-detail',
          component: () => import('@/views/PromptDetailView.vue'),
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
        },
        {
          path: 'admin',
          name: 'admin',
          component: () => import('@/views/AdminView.vue'),
          meta: { requiresAdmin: true },
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
  // 管理员页面：非 ADMIN 角色踢回首页（体验层，后端 @SaCheckRole 才是真正防线）
  if (to.meta.requiresAdmin && getUser()?.role !== 'ADMIN') {
    return '/'
  }
})

export default router
