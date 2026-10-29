import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/AppLayout.vue'),
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'chat',
        component: () => import('@/views/ChatView.vue'),
        meta: { title: '智能问答' }
      },
      {
        path: 'kb',
        name: 'kb',
        component: () => import('@/views/KbView.vue'),
        meta: { title: '知识库' }
      },
      {
        path: 'doc',
        name: 'doc',
        component: () => import('@/views/DocView.vue'),
        meta: { title: '文档管理' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/chat'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const user = useUserStore()
  if (!to.meta.public && !user.token) {
    return { name: 'login' }
  }
  if (to.name === 'login' && user.token) {
    return { name: 'chat' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · AI 知识库` : 'AI 知识库问答平台'
})

export default router
