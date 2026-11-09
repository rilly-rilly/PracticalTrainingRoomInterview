<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { studentApi } from '../../api'
import FeedbackState from '../../components/FeedbackState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { countdownText, formatDateTime } from '../../utils/format'

const loading = ref(true)
const error = ref(null)
const data = ref(null)

const tick = ref(Date.now())
const timer = setInterval(() => {
  tick.value = Date.now()
}, 1000)
onUnmounted(() => clearInterval(timer))

const stage = computed(() => data.value?.stage || '未填报')
const intentions = computed(() => data.value?.intentions || [])
const adjustments = computed(() => data.value?.adjustments || [])
const publicity = computed(() => data.value?.publicity || null)
const target = computed(() => data.value?.intention || null)

const bannerClass = computed(() => {
  if (stage.value === '已录取') return 'result-banner--success'
  if (stage.value === '公示中') return 'result-banner--warn'
  return 'result-banner--muted'
})

const bannerText = computed(() => {
  const map = {
    已录取: '录取结果已生效，欢迎加入实训室。',
    公示中: '公示期结束后无异议即正式入组。',
    待公示: '已确认唯一去向，等待学院统一公示。',
    递补中: '当前处于递补顺位，名额释放后将按顺位递补。',
    考核进行中: '面试与录取流程正在进行，请耐心等待结果。',
    等待调剂: '所有意向暂未录取，学院将按调剂规则继续安排。',
    未填报: '尚未提交意向，请先完成意向填报。'
  }
  return map[stage.value] || '请关注学院通知，及时查看最新结果。'
})

const roomName = computed(() => target.value?.roomName || publicity.value?.roomName || '')
const teacherName = computed(() => target.value?.teacherName || publicity.value?.teacherName || '')

const countdownLabel = computed(() => {
  tick.value
  return countdownText(publicity.value?.endAt)
})

function typeLabel(record) {
  if (record.typeLabel) return record.typeLabel
  const map = {
    inner: '实训室内部调剂',
    global: '全院跨实训室调剂',
    supplement: '分轮补录',
    interview: '约谈（第三优先级）'
  }
  return map[record.type] || record.type
}

function targetText(record) {
  const parts = []
  if (record.toRoomName) parts.push(record.toRoomName)
  if (record.toTeacherName) parts.push(record.toTeacherName)
  return parts.length ? parts.join(' · ') : '—'
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await studentApi.final()
    data.value = res.data
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

load()
</script>

<template>
  <main class="page">
    <div class="page-head">
      <div>
        <h1>最终结果</h1>
        <p class="page-head__sub">录取结果、公示进度与调剂补录记录的最终汇总。</p>
      </div>
    </div>

    <FeedbackState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && intentions.length === 0"
      empty-text="当前账号还没有提交任何意向，完成填报后才能查看最终结果。"
      @retry="load"
    >
      <template #empty-action>
        <router-link class="btn btn--sm" to="/student/intent">前往意向填报</router-link>
      </template>

      <div class="stack">
        <div class="result-banner" :class="bannerClass">
          <div class="result-banner__stamp">{{ stage }}</div>
          <h2>{{ stage }}</h2>
          <p>{{ bannerText }}</p>
          <div class="result-banner__meta">
            <div v-if="roomName">
              <span>实训室</span>
              <strong>{{ roomName }}</strong>
            </div>
            <div v-if="teacherName">
              <span>导师</span>
              <strong>{{ teacherName }}</strong>
            </div>
            <div v-if="publicity">
              <span>公示截止</span>
              <strong>{{ formatDateTime(publicity.endAt) }}</strong>
            </div>
            <div v-if="publicity">
              <span>剩余时间</span>
              <strong>{{ countdownLabel }}</strong>
            </div>
          </div>
        </div>

        <section class="section">
          <div class="section__head">
            <div>
              <h2>我的意向明细</h2>
              <p class="section__desc">包含本轮平行意向与调剂产生的补充意向。</p>
            </div>
          </div>
          <div class="card">
            <div class="table-wrap">
              <table class="table">
                <thead>
                  <tr>
                    <th>导师</th>
                    <th>实训室</th>
                    <th>状态</th>
                    <th>面试总分</th>
                    <th>轮次</th>
                    <th>备注</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in intentions" :key="item.id">
                    <td>
                      <strong>{{ item.teacherName }}</strong>
                      <span class="muted">（{{ item.teacherNo }}）</span>
                    </td>
                    <td>{{ item.roomName }}</td>
                    <td><StatusBadge :status="item.status" /></td>
                    <td>{{ item.interview?.total ?? '—' }}</td>
                    <td>
                      <span v-if="item.round === 2" class="badge badge--waitlist">调剂</span>
                      <span v-else>首轮</span>
                    </td>
                    <td style="max-width: 340px">{{ item.interview?.comment || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <section v-if="adjustments.length" class="section">
          <div class="section__head">
            <h2>调剂与补录记录</h2>
          </div>
          <div class="card">
            <div class="table-wrap">
              <table class="table table--compact">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>类型</th>
                    <th>调剂去向</th>
                    <th>结果</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="record in adjustments" :key="record.id">
                    <td class="mono">{{ formatDateTime(record.createdAt) }}</td>
                    <td>{{ typeLabel(record) }}</td>
                    <td>{{ targetText(record) }}</td>
                    <td>{{ record.result || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <div class="inline">
          <router-link to="/student/status">返回录取状态</router-link>
          <span class="muted">·</span>
          <router-link to="/student/adjustment">查看调剂与递补</router-link>
        </div>
      </div>
    </FeedbackState>
  </main>
</template>
