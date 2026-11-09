<script setup>
import { computed, ref } from 'vue'
import { adminApi } from '../../api'
import FeedbackState from '../../components/FeedbackState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { countdownText, formatDateTime } from '../../utils/format'
import { pushToast } from '../../utils/toast'

const loading = ref(true)
const error = ref(null)
const data = ref(null)

const publishBusy = ref(false)
const publishError = ref('')

const forceFinal = ref(false)
const finalizeBusy = ref(false)
const finalizeResult = ref(null)
const finalizeError = ref('')

const stats = computed(() => {
  const records = data.value?.records || []
  return [
    { label: '待公示人数', value: data.value?.eligible?.length ?? 0 },
    { label: '公示中人数', value: records.filter((r) => r.status === 'public').length },
    { label: '已正式入组人数', value: records.filter((r) => r.status === 'final').length }
  ]
})

function errText(e) {
  return `[${e?.code ?? 'ERR'}] ${e?.message || '请求失败'}`
}

async function fetchData(silent = false) {
  if (!silent) loading.value = true
  error.value = null
  try {
    const res = await adminApi.publicity()
    data.value = res.data
  } catch (e) {
    error.value = { code: e.code, message: e.message }
  } finally {
    if (!silent) loading.value = false
  }
}
fetchData()

async function doPublish() {
  const count = data.value?.eligible?.length || 0
  if (!count) return
  if (!window.confirm(`确认发布 ${count} 人的录取结果公示？公示期为 1 天。`)) return
  publishBusy.value = true
  publishError.value = ''
  try {
    const res = await adminApi.publish()
    pushToast(res.message || '公示已发布，公示期 1 天', 'success', res.code)
    await fetchData(true)
  } catch (e) {
    publishError.value = errText(e)
  } finally {
    publishBusy.value = false
  }
}

async function doFinalize() {
  finalizeBusy.value = true
  finalizeResult.value = null
  finalizeError.value = ''
  try {
    const res = await adminApi.finalize({ force: forceFinal.value })
    finalizeResult.value = res.data
    await fetchData(true)
  } catch (e) {
    finalizeError.value = errText(e)
  } finally {
    finalizeBusy.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>结果公示</h1>
        <p class="page-head__sub">发布已确认录取学生的结果公示，公示期满后正式入组。</p>
      </div>
      <div class="page-head__actions">
        <button class="btn btn--ghost btn--sm" :disabled="loading" @click="fetchData()">刷新数据</button>
      </div>
    </div>

    <FeedbackState
      :loading="loading"
      :error="error"
      :empty="!data"
      empty-text="暂无可公示的录取数据"
      @retry="fetchData()"
    >
      <section class="section">
        <div class="card">
          <div class="card__body">
            <div class="grid grid--3">
              <div v-for="item in stats" :key="item.label" class="kv">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="card">
          <div class="card__head">
            <h2>发布结果公示</h2>
            <span class="hint">公示期为 1 天</span>
          </div>
          <div class="card__body stack">
            <p class="hint">
              将当前全部「已确认」状态的学生发布为公示记录，公示期间学生可在最终结果页查看。
            </p>
            <div class="btn-row">
              <button
                class="btn btn--lg"
                :disabled="publishBusy || !data.eligible.length"
                @click="doPublish"
              >
                {{ publishBusy ? '发布中…' : '发布结果公示' }}
              </button>
              <span class="hint">待公示 {{ data.eligible.length }} 人</span>
            </div>
            <div v-if="publishError" class="form-error">{{ publishError }}</div>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>待公示名单</h2>
            <p class="section__desc">共 {{ data.eligible.length }} 名已确认录取学生。</p>
          </div>
        </div>
        <FeedbackState v-if="!data.eligible.length" :empty="true" empty-text="暂无已确认录取、待公示的学生" />
        <div v-else class="card">
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>学号</th>
                  <th>姓名</th>
                  <th>专业</th>
                  <th>实训室</th>
                  <th>导师</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in data.eligible" :key="item.studentId">
                  <td class="mono">{{ item.studentNo }}</td>
                  <td><strong>{{ item.name }}</strong></td>
                  <td>{{ item.major || '—' }}</td>
                  <td>{{ item.roomName || '—' }}</td>
                  <td>{{ item.teacherName || '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>公示记录</h2>
            <p class="section__desc">共 {{ data.records.length }} 条公示记录，按公示开始时间倒序排列。</p>
          </div>
        </div>
        <FeedbackState v-if="!data.records.length" :empty="true" empty-text="暂无公示记录" />
        <div v-else class="card">
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>学生</th>
                  <th>实训室</th>
                  <th>导师</th>
                  <th>公示开始</th>
                  <th>公示截止</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in data.records" :key="record.id">
                  <td>
                    <strong>{{ record.studentName || '—' }}</strong><br />
                    <span class="mono muted">{{ record.studentNo || '—' }}</span>
                  </td>
                  <td>{{ record.roomNameSnap || '—' }}</td>
                  <td>{{ record.teacherNameSnap || '—' }}</td>
                  <td class="mono">{{ formatDateTime(record.startAt) }}</td>
                  <td class="mono">{{ formatDateTime(record.endAt) }}</td>
                  <td>
                    <template v-if="record.status === 'public'">
                      <span class="inline">
                        <StatusBadge status="公示中" />
                        <span class="countdown">{{ countdownText(record.endAt) }}</span>
                      </span>
                    </template>
                    <span v-else-if="record.status === 'final'" class="badge badge--enrolled">已正式入组</span>
                    <span v-else class="muted">{{ record.status }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="card">
          <div class="card__head">
            <h2>结束公示并正式入组</h2>
          </div>
          <div class="card__body stack">
            <div v-if="data.window">
              <div class="info-row">
                <span>公示阶段</span>
                <strong>{{ data.window.title || '结果公示' }}</strong>
              </div>
              <div class="info-row">
                <span>起止时间</span>
                <strong>
                  {{ formatDateTime(data.window.startAt) }} ~ {{ formatDateTime(data.window.endAt) }}
                </strong>
              </div>
            </div>
            <label class="checkline">
              <input type="checkbox" v-model="forceFinal" />
              <span>
                <strong>提前结束公示（演示用）</strong>
                <small>跳过公示截止时间校验，立即将公示中的记录转为正式入组。</small>
              </span>
            </label>
            <div class="btn-row">
              <button class="btn btn--success" :disabled="finalizeBusy" @click="doFinalize">
                {{ finalizeBusy ? '处理中…' : '结束公示并正式入组' }}
              </button>
            </div>
            <div v-if="finalizeResult" class="form-success">
              公示无异议，{{ finalizeResult.enrolled }} 名学生已正式入组（状态变为「已录取」）。
            </div>
            <div v-if="finalizeError" class="form-error">{{ finalizeError }}</div>
          </div>
        </div>
      </section>
    </FeedbackState>
  </div>
</template>
