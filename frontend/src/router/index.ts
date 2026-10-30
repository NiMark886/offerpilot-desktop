import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { isDesktopRuntime } from '@/api/base'

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
      path: '/resume-builder',
      name: 'resume-builder',
      component: () => import('@/views/ResumeBuilderView.vue'),
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

export default router
