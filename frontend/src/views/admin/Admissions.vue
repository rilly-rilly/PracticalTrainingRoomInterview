<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { adminApi } from '../../api'
import FeedbackState from '../../components/FeedbackState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { countdownText, formatDateTime } from '../../utils/format'

const loading = ref(true)
const error = ref(null)
const data = ref(null)

const quotaDrafts = ref({})
const quotaErrors = ref({})
const quotaSaving = ref('')

const forceExpire = ref(false)
const expireBusy = ref(false)
const expireResult = ref(null)
const expireError = ref('')

const now = ref(Date.now())
const timer = setInterval(() => {
  now.value = Date.now()
}, 1000)
onUnmounted(() => clearInterval(timer))

const roleLabels = { admin: '管理员', teacher: '导师', student: '学生' }

const statItems = computed(() => {
  const s = data.value?.stats || {}
  return [
    { label: '学生数', value: s.students ?? '—' },
    { label: '导师数', value: s.teachers ?? '—' },
    { label: '意向数', value: s.intentions ?? '—' },
    { label: '待面试', value: s.pending ?? '—' },
    { label: '面试完成', value: s.scored ?? '—' },
    { label: '拟录取', value: s.admitted ?? '—' },
    { label: '递补', value: s.waitlist ?? '—' },
    { label: '已确认', value: s.confirmed ?? '—' },
    { label: '已录取', value: s.enrolled ?? '—' },
    { label: '未录取', value: s.rejected ?? '—' }
  ]
})

const confirmWindow = computed(() => data.value?.confirmWindow || null)

const confirmRemain = computed(() => {
  void now.value
  const w = confirmWindow.value
  return w ? countdownText(w.endAt) : '—'
})

function errText(e) {
  return `[${e?.code ?? 'ERR'}] ${e?.message || '请求失败'}`
}

async function fetchData(silent = false) {
  if (!silent) loading.value = true
  error.value = null
  try {
    const res = await adminApi.admissions()
    data.value = res.data
    const drafts = {}
    for (const room of res.data?.rooms || []) drafts[room.id] = room.quota
    quotaDrafts.value = drafts
    quotaErrors.value = {}
  } catch (e) {
    error.value = { code: e.code, message: e.message }
  } finally {
    if (!silent) loading.value = false
  }
}
fetchData()

async function saveQuota(room) {
  const quota = Number(quotaDrafts.value[room.id])
  if (!Number.isInteger(quota) || quota < 1 || quota > 20) {
    quotaErrors.value = { ...quotaErrors.value, [room.id]: '名额上限必须是 1~20 的整数' }
    return
  }
  const next = { ...quotaErrors.value }
  delete next[room.id]
  quotaErrors.value = next
  quotaSaving.value = room.id
  try {
    await adminApi.updateQuota(room.id, quota)
    await fetchData(true)
  } catch (e) {
    quotaErrors.value = { ...quotaErrors.value, [room.id]: errText(e) }
  } finally {
    quotaSaving.value = ''
  }
}

async function doExpire() {
  expireBusy.value = true
  expireResult.value = null
  expireError.value = ''
  try {
    const res = await adminApi.expire({ force: forceExpire.value })
    expireResult.value = res.data
    await fetchData(true)
  } catch (e) {
    expireError.value = errText(e)
  } finally {
    expireBusy.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>录取统筹</h1>
        <p class="page-head__sub">统筹各实训室名额、导师录取情况与拟录取确认进度。</p>
      </div>
      <div class="page-head__actions">
        <button class="btn btn--ghost btn--sm" :disabled="loading" @click="fetchData()">刷新数据</button>
      </div>
    </div>

    <FeedbackState
      :loading="loading"
      :error="error"
      :empty="!data"
      empty-text="暂无可统筹的录取数据"
      @retry="fetchData()"
    >
      <section class="section">
        <div class="card">
          <div class="card__body">
            <div class="grid grid--4">
              <div v-for="item in statItems" :key="item.label" class="kv">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>各实训室名额与录取情况</h2>
            <p class="section__desc">名额上限可在此调整，保存后立即生效；上限不得低于当前已占用人数。</p>
          </div>
        </div>
        <FeedbackState v-if="!data.rooms.length" :empty="true" empty-text="暂无实训室数据" />
        <div v-else class="card">
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>招收上限</th>
                  <th>已占用</th>
                  <th>剩余</th>
                  <th>拟录取</th>
                  <th>已确认</th>
                  <th>递补</th>
                  <th>已录取</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="room in data.rooms" :key="room.id">
                  <td><strong>{{ room.name }}</strong><br /><span class="mono muted">{{ room.code }}</span></td>
                  <td>
                    <input
                      class="input"
                      type="number"
                      min="1"
                      max="20"
                      v-model="quotaDrafts[room.id]"
                    />
                  </td>
                  <td>{{ room.used }}</td>
                  <td>{{ room.remaining }}</td>
                  <td>{{ room.admitted }}</td>
                  <td>{{ room.confirmed }}</td>
                  <td>{{ room.waitlist }}</td>
                  <td>{{ room.enrolled }}</td>
                  <td>
                    <button
                      class="btn btn--sm"
                      :disabled="quotaSaving === room.id"
                      @click="saveQuota(room)"
                    >
                      {{ quotaSaving === room.id ? '保存中…' : '保存名额' }}
                    </button>
                    <p v-if="quotaErrors[room.id]" class="form-error">{{ quotaErrors[room.id] }}</p>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>各导师录取情况</h2>
            <p class="section__desc">统计每位导师当前报考、拟录取与递补人数。</p>
          </div>
        </div>
        <FeedbackState v-if="!data.teachers.length" :empty="true" empty-text="暂无导师数据" />
        <div v-else class="card">
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>工号</th>
                  <th>姓名</th>
                  <th>实训室</th>
                  <th>报考人数</th>
                  <th>拟录取</th>
                  <th>递补</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="t in data.teachers" :key="t.id">
                  <td class="mono">{{ t.teacherNo }}</td>
                  <td><strong>{{ t.name }}</strong></td>
                  <td>{{ t.roomName || '—' }}</td>
                  <td>{{ t.applied }}</td>
                  <td>{{ t.admitted }}</td>
                  <td>{{ t.waitlist }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="card">
          <div class="card__head">
            <h2>逾期未确认处理</h2>
            <span v-if="confirmWindow" class="countdown">{{ confirmRemain }}</span>
          </div>
          <div class="card__body stack">
            <div>
              <div class="info-row">
                <span>确认窗口</span>
                <strong>{{ confirmWindow?.title || '—' }}</strong>
              </div>
              <div class="info-row">
                <span>截止时间</span>
                <strong>{{ confirmWindow ? formatDateTime(confirmWindow.endAt) : '—' }}</strong>
              </div>
            </div>
            <label class="checkline">
              <input type="checkbox" v-model="forceExpire" />
              <span>
                <strong>强制模拟逾期（演示用）</strong>
                <small>跳过确认窗口时间校验，直接将当前拟录取记录按逾期未确认处理。</small>
              </span>
            </label>
            <div class="btn-row">
              <button class="btn btn--warn" :disabled="expireBusy" @click="doExpire">
                {{ expireBusy ? '处理中…' : '执行逾期未确认处理' }}
              </button>
            </div>
            <div v-if="expireResult" class="form-success">
              已处理 {{ expireResult.expired }} 条逾期拟录取，递补补位 {{ expireResult.promoted }} 人（涉及学生
              {{ expireResult.students }} 名）
            </div>
            <div v-if="expireError" class="form-error">{{ expireError }}</div>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>全部意向明细</h2>
            <p class="section__desc">共 {{ data.intentions.length }} 条意向记录。</p>
          </div>
        </div>
        <FeedbackState v-if="!data.intentions.length" :empty="true" empty-text="暂无学生意向记录" />
        <div v-else class="card">
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>学生</th>
                  <th>专业</th>
                  <th>导师</th>
                  <th>实训室</th>
                  <th>轮次</th>
                  <th>优先级</th>
                  <th>面试总分</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="it in data.intentions" :key="it.id">
                  <td>
                    <strong>{{ it.studentName }}</strong><br />
                    <span class="mono muted">{{ it.studentNo }}</span>
                  </td>
                  <td>{{ it.major || '—' }}</td>
                  <td>{{ it.teacherName || '—' }}</td>
                  <td>{{ it.roomName || '—' }}</td>
                  <td>第 {{ it.round }} 轮</td>
                  <td>第 {{ it.priority }} 志愿</td>
                  <td>{{ it.interview ? it.interview.total : '—' }}</td>
                  <td><StatusBadge :status="it.status" /></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>关键操作日志</h2>
            <p class="section__desc">最近 30 条录取统筹相关操作。</p>
          </div>
        </div>
        <FeedbackState v-if="!data.logs.length" :empty="true" empty-text="暂无操作日志" />
        <div v-else class="card">
          <div class="table-wrap">
            <table class="table table--compact">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>操作角色</th>
                  <th>操作人</th>
                  <th>动作</th>
                  <th>详情</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="log in data.logs" :key="log.id">
                  <td class="mono">{{ formatDateTime(log.createdAt) }}</td>
                  <td>
                    <span class="badge badge--role">{{ roleLabels[log.operatorRole] || log.operatorRole }}</span>
                  </td>
                  <td>{{ log.operatorName }}</td>
                  <td><strong>{{ log.action }}</strong></td>
                  <td>{{ log.detail }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </FeedbackState>
  </div>
</template>
