<script setup>
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authState, clearAuth, roleHome } from './auth'
import { authApi } from './api'
import { pushToast } from './utils/toast'
import ToastHost from './components/ToastHost.vue'

const route = useRoute()
const router = useRouter()

const roleLabels = { admin: '学院管理员', teacher: '导师', student: '学生' }

const navItems = computed(() => {
  const base = [
    { to: '/', label: '首页' },
    { to: '/rooms', label: '实训室介绍' }
  ]
  if (authState.role === 'student') {
    return [
      ...base,
      { to: '/student/intent', label: '意向填报' },
      { to: '/student/status', label: '录取状态' },
      { to: '/student/adjustment', label: '调剂与递补' },
      { to: '/student/final', label: '最终结果' }
    ]
  }
  if (authState.role === 'teacher') {
    return [...base, { to: '/teacher/applicants', label: '报考学生与考核' }]
  }
  if (authState.role === 'admin') {
    return [
      ...base,
      { to: '/admin/import', label: '导入账号' },
      { to: '/admin/schedule', label: '日程与通知' },
      { to: '/admin/admissions', label: '录取统筹' },
      { to: '/admin/adjustment', label: '补录与调剂' },
      { to: '/admin/publicity', label: '结果公示' }
    ]
  }
  return base
})

function isActive(item) {
  if (item.to === '/') return route.path === '/'
  return route.path.startsWith(item.to)
}

async function handleLogout() {
  try {
    await authApi.logout()
  } catch (e) {
    // 登录态可能已失效，忽略即可
  }
  clearAuth()
  pushToast('已退出登录，旧登录态已失效', 'success', 200)
  router.replace('/login')
}

watch(
  () => authState.token,
  (token) => {
    if (!token && !route.meta.public) {
      router.replace({ path: '/login', query: { redirect: route.fullPath } })
    }
  }
)
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <router-link to="/" class="brand">
        <span class="brand__mark">星</span>
        <span>
          星火实训室
          <small>进组选拔系统 · DEMO</small>
        </span>
      </router-link>

      <nav class="nav">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav__item"
          :class="{ 'is-active': isActive(item) }"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div v-if="authState.token" class="header-user">
        <span class="avatar">{{ (authState.profile?.name || '?').slice(0, 1) }}</span>
        <div class="header-user__meta">
          <strong>{{ authState.profile?.name }}</strong>
          <span>{{ roleLabels[authState.role] }} · {{ authState.profile?.no || authState.profile?.username }}</span>
        </div>
        <button class="btn btn--ghost btn--sm" @click="handleLogout">退出</button>
      </div>
      <router-link v-else to="/login" class="btn btn--sm">登录</router-link>
    </div>
  </header>

  <router-view />

  <ToastHost />
</template>
