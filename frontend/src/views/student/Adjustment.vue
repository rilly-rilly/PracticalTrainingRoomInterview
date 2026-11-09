<script setup>
import { computed, ref } from 'vue'
import { studentApi } from '../../api'
import FeedbackState from '../../components/FeedbackState.vue'
import { formatDateTime } from '../../utils/format'

const loading = ref(true)
const error = ref(null)
const data = ref(null)

const records = computed(() => data.value?.records || [])
const summary = computed(() => data.value?.summary || {})

const typeFallback = {
  inner: '实训室内部调剂',
  global: '全院跨实训室调剂',
  supplement: '分轮补录',
  interview: '约谈（第三优先级）'
}

function typeLabel(record) {
  return record.typeLabel || typeFallback[record.type] || record.type
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
    const res = await studentApi.adjustments()
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
        <h1>调剂与递补</h1>
        <p class="page-head__sub">查看是否进入调剂池、你是否服从调剂，以及每一轮调剂的去向与结果。</p>
      </div>
    </div>

    <FeedbackState :loading="loading" :error="error" @retry="load">
      <div class="stack">
        <div class="grid grid--3">
          <div class="card">
            <div class="card__body">
              <div class="kv">
                <span>是否已进入调剂池</span>
                <strong>
                  {{ summary.rejected ? '所有意向未录取，已进入调剂流程' : '尚未进入调剂流程' }}
                </strong>
              </div>
            </div>
          </div>
          <div class="card">
            <div class="card__body">
              <div class="kv">
                <span>服从实训室内部调剂</span>
                <strong>{{ summary.acceptInnerAdjust ? '是' : '否' }}</strong>
              </div>
            </div>
          </div>
          <div class="card">
            <div class="card__body">
              <div class="kv">
                <span>服从全院跨实训室调剂</span>
                <strong>{{ summary.acceptGlobalAdjust ? '是' : '否' }}</strong>
              </div>
            </div>
          </div>
        </div>

        <section class="section">
          <div class="section__head">
            <div>
              <h2>调剂优先级说明</h2>
              <p class="section__desc">前一级优先：只有前一级无法满足时，才进入下一级。</p>
            </div>
          </div>
          <div class="grid grid--3">
            <div class="step">
              <span class="step__no">①</span>
              <span class="step__title">实训室内部调剂</span>
              <span class="step__time">
                优先在你填报的实训室内，安排至尚未招满的其他导师，尽量保留原实训室去向。
              </span>
            </div>
            <div class="step">
              <span class="step__no">②</span>
              <span class="step__title">全院跨实训室调剂</span>
              <span class="step__time">
                本实训室无法安排时，再由学院在全院其他实训室的剩余名额中统一调剂。
              </span>
            </div>
            <div class="step">
              <span class="step__no">③</span>
              <span class="step__title">约谈未服从调剂学生</span>
              <span class="step__time">
                未勾选任何调剂的未录取学生，由学院统一安排约谈并说明后续处理方式。
              </span>
            </div>
          </div>
        </section>

        <section class="section">
          <div class="section__head">
            <h2>调剂记录</h2>
          </div>
          <div class="card">
            <FeedbackState
              :loading="false"
              :error="null"
              :empty="records.length === 0"
              empty-text="暂无调剂记录。若第一次录取未成功且勾选了服从调剂，管理员执行调剂后会在此显示。"
            >
              <div class="table-wrap">
                <table class="table">
                  <thead>
                    <tr>
                      <th>时间</th>
                      <th>类型</th>
                      <th>原实训室</th>
                      <th>调剂去向</th>
                      <th>结果</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="record in records" :key="record.id">
                      <td class="mono">{{ formatDateTime(record.createdAt) }}</td>
                      <td>
                        <strong>{{ typeLabel(record) }}</strong>
                        <span v-if="record.round === 2" class="badge badge--waitlist" style="margin-left: 8px">调剂</span>
                      </td>
                      <td>{{ record.fromRoomName || '—' }}</td>
                      <td>{{ targetText(record) }}</td>
                      <td>{{ record.result || '—' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </FeedbackState>
          </div>
        </section>

        <div class="inline">
          <router-link to="/student/status">返回录取状态</router-link>
          <span class="muted">·</span>
          <router-link to="/student/final">查看最终结果</router-link>
        </div>
      </div>
    </FeedbackState>
  </main>
</template>
