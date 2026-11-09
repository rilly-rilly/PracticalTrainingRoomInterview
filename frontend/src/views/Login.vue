<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '../api'
import { authState, clearAuth, persistAuth, roleHome } from '../auth'
import { pushToast } from '../utils/toast'
import FeedbackState from '../components/FeedbackState.vue'

const route = useRoute()
const router = useRouter()

const roleLabels = { admin: '学院管理员', teacher: '导师', student: '学生' }

const form = reactive({ username: '', password: '' })
const submitting = ref(false)
const formError = ref('')

const demoLoading = ref(true)
const demoError = ref(null)
const demoData = ref(null)
const resetting = ref(false)

const demoAccounts = computed(() => {
  const d = demoData.value
  if (!d) return []
  const list = []
  if (d.admin) list.push({ ...d.admin, key: `admin-${d.admin.username}`, roleLabel: '管理员' })
  if (d.teacher) list.push({ ...d.teacher, key: `teacher-${d.teacher.username}`, roleLabel: '导师' })
  for (const student of d.students || []) {
    list.push({ ...student, key: `student-${student.username}`, roleLabel: '学生' })
  }
  return list
})

const currentRole = computed(() => roleLabels[authState.role] || '游客')

async function loadAccounts() {
  demoLoading.value = true
  demoError.value = null
  try {
    const res = await authApi.demoAccounts()
    demoData.value = res.data
  } catch (e) {
    demoError.value = e
  } finally {
    demoLoading.value = false
  }
}

function fillAccount(account) {
  form.username = account.username
  form.password = account.password
  formError.value = ''
}

async function submit() {
  if (submitting.value) return
  submitting.value = true
  formError.value = ''
  try {
    const res = await authApi.login({ username: form.username.trim(), password: form.password })
    persistAuth(res.data)
    pushToast('登录成功', 'success')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect ? route.query.redirect : ''
    router.replace(redirect || roleHome(res.data.role))
  } catch (e) {
    formError.value = e.message
  } finally {
    submitting.value = false
  }
}

function handleLocalLogout() {
  clearAuth()
  pushToast('已退出当前登录', 'success')
}

async function resetDemo() {
  if (resetting.value) return
  if (!window.confirm('重置将还原全部演示数据并退出当前登录，确认继续？')) return
  resetting.value = true
  try {
    await authApi.resetDemo()
    clearAuth()
    await loadAccounts()
    pushToast('演示数据已重置', 'success')
  } catch (e) {
    // 请求失败时全局 toast 已提示
  } finally {
    resetting.value = false
  }
}

onMounted(loadAccounts)
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <h1>星火实训室 · 进组选拔系统</h1>
      <p>面向学院实训室进组选拔的全流程演示系统：从意向填报、导师面试，到拟录取确认、调剂补录与结果公示。</p>
      <div class="login-features">
        <div class="login-feature">
          <strong>平行意向</strong>
          最多填报 3 个平行意向，按优先级排队等待导师考核。
        </div>
        <div class="login-feature">
          <strong>面试评分</strong>
          导师按态度、逻辑、编码、投入四项打分，并作出录取决定。
        </div>
        <div class="login-feature">
          <strong>名额服务端校验</strong>
          录取与调剂均经后端校验名额，避免超出实训室招收上限。
        </div>
        <div class="login-feature">
          <strong>三级调剂</strong>
          实训室内部调剂 → 全院跨实训室调剂 → 约谈（第三优先级）。
        </div>
      </div>
    </section>

    <section class="login-card">
      <div v-if="authState.token" class="hint hint--success" style="margin-bottom: 18px">
        <div class="inline" style="justify-content: space-between; width: 100%">
          <span>当前身份：<strong>{{ currentRole }}</strong> · {{ authState.profile?.name }}</span>
          <span class="btn-row">
            <router-link :to="roleHome(authState.role)" class="btn btn--sm">进入工作台</router-link>
            <button type="button" class="btn btn--ghost btn--sm" @click="handleLocalLogout">退出登录</button>
          </span>
        </div>
      </div>

      <h2>登录系统</h2>
      <p class="login-card__sub">使用管理员分配的账号登录，或点击下方演示账号快速填充。</p>

      <form class="form" @submit.prevent="submit">
        <div class="field">
          <label for="login-username">
            账号
            <span class="field__hint">学生学号 / 导师工号 / admin</span>
          </label>
          <input
            id="login-username"
            v-model="form.username"
            class="input"
            type="text"
            placeholder="例如 S2026001 或 T1001"
            autocomplete="username"
          />
        </div>

        <div class="field">
          <label for="login-password">密码</label>
          <input
            id="login-password"
            v-model="form.password"
            class="input"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>

        <div v-if="formError" class="form-error">{{ formError }}</div>

        <button class="btn btn--lg btn--block" type="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </form>

      <div class="demo-accounts">
        <div class="demo-accounts__title inline" style="justify-content: space-between">
          <span>演示账号快捷登录</span>
          <button type="button" class="btn btn--ghost btn--sm" :disabled="resetting" @click="resetDemo">
            {{ resetting ? '重置中…' : '重置演示数据' }}
          </button>
        </div>

        <FeedbackState
          :loading="demoLoading"
          :error="demoError"
          :empty="!demoAccounts.length"
          loading-text="正在加载演示账号…"
          empty-text="暂无可用的演示账号，请尝试重置演示数据"
          @retry="loadAccounts"
        >
          <div class="demo-accounts__grid">
            <button
              v-for="account in demoAccounts"
              :key="account.key"
              type="button"
              class="demo-account"
              @click="fillAccount(account)"
            >
              <span>
                <strong>{{ account.roleLabel }} · {{ account.username }}</strong>
                {{ account.name }}
              </span>
              <span class="copy-pwd">{{ account.password }}</span>
            </button>
          </div>
        </FeedbackState>
      </div>
    </section>
  </main>
</template>
