import { createRouter, createWebHistory } from 'vue-router'
import { authState } from '../auth'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/Home.vue'), meta: { public: true } },
  { path: '/rooms', name: 'rooms', component: () => import('../views/Rooms.vue'), meta: { public: true } },
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/403', name: 'forbidden', component: () => import('../views/Forbidden.vue'), meta: { public: true } },

  { path: '/student/intent', name: 'student-intent', component: () => import('../views/student/Intent.vue'), meta: { roles: ['student'] } },
  { path: '/student/status', name: 'student-status', component: () => import('../views/student/Status.vue'), meta: { roles: ['student'] } },
  { path: '/student/adjustment', name: 'student-adjustment', component: () => import('../views/student/Adjustment.vue'), meta: { roles: ['student'] } },
  { path: '/student/final', name: 'student-final', component: () => import('../views/student/Final.vue'), meta: { roles: ['student'] } },

  { path: '/teacher/applicants', name: 'teacher-applicants', component: () => import('../views/teacher/Applicants.vue'), meta: { roles: ['teacher'] } },
  { path: '/teacher/interview/:id', name: 'teacher-interview', component: () => import('../views/teacher/Interview.vue'), meta: { roles: ['teacher'] } },

  { path: '/admin/import', name: 'admin-import', component: () => import('../views/admin/Import.vue'), meta: { roles: ['admin'] } },
  { path: '/admin/schedule', name: 'admin-schedule', component: () => import('../views/admin/Schedule.vue'), meta: { roles: ['admin'] } },
  { path: '/admin/admissions', name: 'admin-admissions', component: () => import('../views/admin/Admissions.vue'), meta: { roles: ['admin'] } },
  { path: '/admin/adjustment', name: 'admin-adjustment', component: () => import('../views/admin/Adjustment.vue'), meta: { roles: ['admin'] } },
  { path: '/admin/publicity', name: 'admin-publicity', component: () => import('../views/admin/Publicity.vue'), meta: { roles: ['admin'] } },

  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!authState.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const roles = to.meta.roles
  if (roles && !roles.includes(authState.role)) {
    return '/403'
  }
  return true
})

export default router
