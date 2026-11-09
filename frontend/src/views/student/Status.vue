<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { studentApi, teacherApi } from '../../api'
import FeedbackState from '../../components/FeedbackState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { countdownText, formatDateTime } from '../../utils/format'
import { pushToast } from '../../utils/toast'

const loading = ref(true)
const error = ref(null)
const data = ref(null)

const selectedId = ref('')
const confirming = ref(false)
const actionError = ref('')

const permissionLoading = ref(false)
const permissionResult = ref(null)

const expanded = ref({})
const tick = ref(Date.now())
const timer = setInterval(() => {
  tick.value = Date.now()
}, 1000)
onUnmounted(() => clearInterval(timer))

const intentions = computed(() => data.value?.intentions || [])
const summary = computed(() => data.value?.summary || {})
const confirmWindow = computed(() => data.value?.confirmWindow || null)
const admittedList = computed(() => intentions.value.filter((item) => item.status === '拟录取'))
const confirmedIntention = computed(
  () => intentions.value.find((item) => item.id === summary.value.confirmedIntentionId) || null
)

const countdownLabel = computed(() => {
  tick.value
  return countdownText(confirmWindow.value?.endAt)
})
const deadlinePassed = computed(() => {
  tick.value
  if (!confirmWindow.value?.endAt) return true
  return Date.now() > confirmWindow.value.endAt
})
const waitlistText = computed(() => {
  if (!summary.value.hasWaitlist) return '无递补'
  const ranks = (summary.value.waitlistRank || []).filter((rank) => rank > 0)
  return ranks.length ? `第 ${ranks.join(' / ')} 顺位` : '递补中'
})
const confirmStateText = computed(() => {
  if (summary.value.confirmedIntentionId) return '已确认'
  if (summary.value.needChoose) return '待选择'
  return '无'
})

function toggleExpand(id) {
  expanded.value = { ...expanded.value, [id]: !expanded.value[id] }
}

function canExpand(item) {
  return (item.intro || '').length > 40
}

function introText(item) {
  const text = item.intro || ''
  if (!text) return '—'
  if (expanded.value[item.id] || text.length <= 40) return text
  return `${text.slice(0, 40)}…`
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await studentApi.status()
    data.value = res.data
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

async function handleConfirm() {
  actionError.value = ''
  if (!selectedId.value) {
    actionError.value = '请先选择一个拟录取去向'
    return
  }
  confirming.value = true
  try {
    await studentApi.confirm(selectedId.value)
    pushToast('已确认唯一去向，其余拟录取将自动放弃', 'success', 200)
    selectedId.value = ''
    await load()
  } catch (e) {
    actionError.value = `[${e.code}] ${e.message}`
  } finally {
    confirming.value = false
  }
}

async function handlePermissionDemo() {
  permissionResult.value = null
  permissionLoading.value = true
  try {
    await teacherApi.applicants()
    permissionResult.value = { ok: true, message: '未被拦截（异常）：学生身份成功调用了导师后台接口' }
  } catch (e) {
    permissionResult.value = { ok: false, code: e.code, message: e.message }
  } finally {
    permissionLoading.value = false
  }
}

load()
</script>

<template>
  <main class="page">
    <div class="page-head">
      <div>
        <h1>录取状态</h1>
        <p class="page-head__sub">查看本轮意向的面试评分、拟录取与递补状态，并在确认窗口内锁定唯一去向。</p>
      </div>
    </div>

    <FeedbackState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && intentions.length === 0"
      empty-text="当前账号还没有提交任何意向，请先完成意向填报。"
      @retry="load"
    >
      <template #empty-action>
        <router-link class="btn btn--sm" to="/student/intent">前往意向填报</router-link>
      </template>

      <div class="stack">
        <div class="grid grid--4">
          <div class="card">
            <div class="card__body">
              <div class="kv">
                <span>意向数</span>
                <strong>{{ summary.total ?? intentions.length }}</strong>
              </div>
            </div>
          </div>
          <div class="card">
            <div class="card__body">
              <div class="kv">
                <span>拟录取数</span>
                <strong>{{ summary.admits ?? 0 }}</strong>
              </div>
            </div>
          </div>
          <div class="card">
            <div class="card__body">
              <div class="kv">
                <span>递补状态</span>
                <strong>{{ waitlistText }}</strong>
              </div>
            </div>
          </div>
          <div class="card">
            <div class="card__body">
              <div class="kv">
                <span>确认状态</span>
                <strong>{{ confirmStateText }}</strong>
              </div>
            </div>
          </div>
        </div>

        <section class="section">
          <div class="section__head">
            <div>
              <h2>我的意向</h2>
              <p class="section__desc">平行意向按填报顺序展示；调剂产生的意向会标注「调剂」。</p>
            </div>
          </div>
          <div class="card">
            <div class="table-wrap">
              <table class="table">
                <thead>
                  <tr>
                    <th>优先级</th>
                    <th>导师</th>
                    <th>实训室</th>
                    <th>状态</th>
                    <th>面试总分</th>
                    <th>递补顺位</th>
                    <th>简介</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in intentions" :key="item.id">
                    <td>
                      <div class="inline">
                        <strong>第 {{ item.priority }} 意向</strong>
                        <span v-if="item.round === 2" class="badge badge--waitlist">调剂</span>
                      </div>
                    </td>
                    <td>{{ item.teacherName }}</td>
                    <td>{{ item.roomName }}</td>
                    <td><StatusBadge :status="item.status" /></td>
                    <td>{{ item.interview?.total ?? '—' }}</td>
                    <td>{{ item.rank || '—' }}</td>
                    <td style="max-width: 320px">
                      <span>{{ introText(item) }}</span>
                      <button
                        v-if="canExpand(item)"
                        class="btn btn--ghost btn--sm"
                        type="button"
                        style="margin-left: 8px"
                        @click="toggleExpand(item.id)"
                      >
                        {{ expanded[item.id] ? '收起' : '展开' }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card__head">
            <h2>拟录取确认</h2>
            <span v-if="summary.needChoose" class="badge badge--pending">待确认</span>
          </div>
          <div class="card__body stack">
            <template v-if="summary.needChoose">
              <div class="hint--box hint--warn">
                <strong>收到多个拟录取，请在截止前确认唯一去向</strong>
                <p style="margin-top: 4px">
                  确认截止：{{ formatDateTime(confirmWindow?.endAt) }}
                  <span class="countdown" style="margin-left: 8px">{{ countdownLabel }}</span>
                </p>
                <p v-if="deadlinePassed" style="margin-top: 4px">确认窗口已截止，逾期未确认视为放弃。</p>
              </div>

              <div class="stack">
                <label v-for="item in admittedList" :key="item.id" class="checkline">
                  <input v-model="selectedId" type="radio" name="confirm-target" :value="item.id" />
                  <span>
                    <strong>{{ item.teacherName }}</strong>
                    <small>{{ item.roomName }} · 面试总分 {{ item.interview?.total ?? '—' }}</small>
                  </span>
                </label>
              </div>

              <div v-if="actionError" class="form-error">{{ actionError }}</div>

              <div class="btn-row">
                <button
                  class="btn btn--lg"
                  type="button"
                  :disabled="!selectedId || deadlinePassed || confirming"
                  @click="handleConfirm"
                >
                  {{ confirming ? '确认中…' : '确认唯一去向' }}
                </button>
                <span class="hint">确认后其余拟录取将自动转为「已放弃」，不可撤销。</span>
              </div>
            </template>

            <div v-if="summary.confirmedIntentionId" class="hint--box hint--success">
              <strong>已确认唯一去向</strong>
              <p style="margin-top: 4px">
                {{ confirmedIntention?.teacherName || '—' }} · {{ confirmedIntention?.roomName || '—' }}
              </p>
            </div>

            <div v-if="summary.autoConfirmed" class="hint--box hint--success">
              仅收到 1 个拟录取，系统已自动确认，无需手动选择。
            </div>

            <div
              v-if="!summary.confirmedIntentionId && admittedList.length === 0"
              class="hint--box"
            >
              暂无待确认的拟录取，请等待导师面试与录取结果。
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card__head">
            <h2>越权访问演示</h2>
            <span class="badge badge--role">权限边界</span>
          </div>
          <div class="card__body stack">
            <div class="hint--box">
              以学生身份直接调用导师后台接口 <code>GET /api/teacher/applicants</code>，后端应按角色返回 403 并拦截请求。
            </div>
            <div class="btn-row">
              <button class="btn btn--ghost" type="button" :disabled="permissionLoading" @click="handlePermissionDemo">
                {{ permissionLoading ? '调用中…' : '以学生身份调用导师后台接口' }}
              </button>
            </div>
            <div
              v-if="permissionResult"
              class="hint--box"
              :class="permissionResult.ok ? 'hint--success' : 'hint--danger'"
            >
              <strong>{{ permissionResult.ok ? '未被拦截（异常）' : '已被后端拦截（预期）' }}</strong>
              <p v-if="!permissionResult.ok" style="margin-top: 4px">
                状态码 {{ permissionResult.code }} · {{ permissionResult.message }}
              </p>
              <p v-else style="margin-top: 4px">{{ permissionResult.message }}</p>
            </div>
          </div>
        </section>

        <div class="inline">
          <router-link to="/student/adjustment">查看调剂与递补记录</router-link>
          <span class="muted">·</span>
          <router-link to="/student/final">查看最终结果</router-link>
        </div>
      </div>
    </FeedbackState>
  </main>
</template>
