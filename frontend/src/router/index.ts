import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { isDesktopRuntime } from '@/api/base'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
  }
}

const router = createRouter({
  history: isDesktopRuntime() ? createWebHashHistory() : createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue')
    },
    {
      path: '/interviews/:id',
      name: 'interview-session',
      component: () => import('@/views/InterviewSessionView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/interviews/:id/chat',
      name: 'interview-chat',
      component: () => import('@/views/InterviewChatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/interviews/:id/report',
      name: 'interview-report',
      component: () => import('@/views/InterviewReportView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/resume-review',
      name: 'resume-review',
      component: () => import('@/views/ResumeReviewView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/history',
      name: 'growth',
      component: () => import('@/views/InterviewGrowthView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/history/:key',
      name: 'history',
      component: () => import('@/views/InterviewHistoryView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/admin/questions',
      name: 'admin-questions',
      component: () => import('@/views/AdminQuestionsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  const desktop = isDesktopRuntime()
  if (to.meta.requiresAuth && !auth.isAuthenticated && !desktop) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && (auth.isAuthenticated || desktop)) {
    return { name: 'home' }
  }
  return true
})

export default router
