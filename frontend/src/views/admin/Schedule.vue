<script setup>
import { onMounted, reactive, ref } from 'vue'
import FeedbackState from '../../components/FeedbackState.vue'
import { adminApi } from '../../api'
import { formatDateTime, fromLocalInput, toLocalInput } from '../../utils/format'
import { pushToast } from '../../utils/toast'

const schedules = ref([])
const scheduleLoading = ref(false)
const scheduleError = ref(null)
const savingId = ref('')

const notices = ref([])
const noticesLoading = ref(false)
const noticesError = ref(null)
const deletingId = ref('')

const levelOptions = ['重要', '提醒', '普通']
const form = reactive({ title: '', level: '普通', content: '' })
const formError = ref('')
const submitting = ref(false)

function toEditable(schedule) {
  return {
    id: schedule.id,
    step: schedule.step,
    title: schedule.title,
    startInput: toLocalInput(schedule.startAt),
    endInput: toLocalInput(schedule.endAt),
    description: schedule.description || ''
  }
}

async function loadSchedules() {
  scheduleLoading.value = true
  scheduleError.value = null
  try {
    const res = await adminApi.schedules()
    schedules.value = (res.data || []).map(toEditable)
  } catch (e) {
    scheduleError.value = { code: e.code, message: e.message }
  } finally {
    scheduleLoading.value = false
  }
}

async function saveSchedule(row) {
  if (savingId.value) return
  const title = row.title.trim()
  if (!title) {
    pushToast('日程标题不能为空', 'warn')
    return
  }
  const startAt = fromLocalInput(row.startInput)
  const endAt = fromLocalInput(row.endInput)
  if (!Number.isFinite(startAt) || !Number.isFinite(endAt)) {
    pushToast('请完整选择开始与结束时间', 'warn')
    return
  }
  if (startAt >= endAt) {
    pushToast('开始时间必须早于结束时间，请调整后再保存', 'warn')
    return
  }
  savingId.value = row.id
  try {
    const res = await adminApi.updateSchedule(row.id, { title, startAt, endAt, description: row.description })
    pushToast(res.message || '日程已更新', 'success')
    await loadSchedules()
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    savingId.value = ''
  }
}

async function loadNotices() {
  noticesLoading.value = true
  noticesError.value = null
  try {
    const res = await adminApi.notices()
    notices.value = res.data || []
  } catch (e) {
    noticesError.value = { code: e.code, message: e.message }
  } finally {
    noticesLoading.value = false
  }
}

async function removeNotice(notice) {
  if (!window.confirm(`确定删除公告「${notice.title}」吗？删除后不可恢复。`)) return
  deletingId.value = notice.id
  try {
    const res = await adminApi.deleteNotice(notice.id)
    pushToast(res.message || '公告已删除', 'success')
    await loadNotices()
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    deletingId.value = ''
  }
}

async function submitNotice() {
  formError.value = ''
  const title = form.title.trim()
  const content = form.content.trim()
  if (!title) {
    formError.value = '公告标题不能为空'
    return
  }
  if (!content) {
    formError.value = '公告内容不能为空'
    return
  }
  submitting.value = true
  try {
    const res = await adminApi.createNotice({ title, content, level: form.level })
    pushToast(res.message || '公告已发布', 'success')
    form.title = ''
    form.level = '普通'
    form.content = ''
    await loadNotices()
  } catch (e) {
    // 错误提示由请求拦截器统一处理，保留表单内容
  } finally {
    submitting.value = false
  }
}

function levelClass(level) {
  if (level === '重要') return 'badge--rejected'
  if (level === '提醒') return 'badge--pending'
  return 'badge--givenup'
}

onMounted(() => {
  loadSchedules()
  loadNotices()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>选拔日程与公告</h1>
        <p class="page-head__sub">维护 7 个选拔步骤的时间窗口，并发布面向全院的公告通知。</p>
      </div>
    </div>

    <section class="section">
      <div class="section__head">
        <div>
          <h2>选拔日程</h2>
          <p class="section__desc">各环节的时间窗口决定对应功能是否开放，保存后立即生效。</p>
        </div>
        <button class="btn btn--ghost btn--sm" type="button" :disabled="scheduleLoading" @click="loadSchedules">
          {{ scheduleLoading ? '加载中…' : '刷新' }}
        </button>
      </div>

      <FeedbackState
        :loading="scheduleLoading"
        :error="scheduleError"
        :empty="!schedules.length"
        empty-text="暂无可维护的日程步骤"
        @retry="loadSchedules"
      >
        <div class="card">
          <div class="table-wrap">
            <table class="table table--compact schedule-table">
              <thead>
                <tr>
                  <th>步骤</th>
                  <th>标题</th>
                  <th>开始时间</th>
                  <th>结束时间</th>
                  <th>说明</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in schedules" :key="row.id">
                  <td><span class="badge badge--role">第 {{ row.step }} 步</span></td>
                  <td>
                    <input v-model="row.title" class="input" type="text" />
                  </td>
                  <td>
                    <input v-model="row.startInput" class="input" type="datetime-local" />
                  </td>
                  <td>
                    <input v-model="row.endInput" class="input" type="datetime-local" />
                  </td>
                  <td>
                    <input v-model="row.description" class="input" type="text" />
                  </td>
                  <td>
                    <button
                      class="btn btn--sm"
                      type="button"
                      :disabled="savingId === row.id"
                      @click="saveSchedule(row)"
                    >
                      {{ savingId === row.id ? '保存中…' : '保存' }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </FeedbackState>
    </section>

    <section class="section">
      <div class="section__head">
        <div>
          <h2>公告与通知</h2>
          <p class="section__desc">发布后学生端与导师端同步可见，删除后不可恢复。</p>
        </div>
        <button class="btn btn--ghost btn--sm" type="button" :disabled="noticesLoading" @click="loadNotices">
          {{ noticesLoading ? '加载中…' : '刷新' }}
        </button>
      </div>

      <div class="split">
        <FeedbackState
          :loading="noticesLoading"
          :error="noticesError"
          :empty="!notices.length"
          empty-text="暂无公告，可在右侧发布第一条通知"
          @retry="loadNotices"
        >
          <div class="stack">
            <div v-for="notice in notices" :key="notice.id" class="card">
              <div class="card__head">
                <h3>{{ notice.title }}</h3>
                <button
                  class="btn btn--ghost btn--sm"
                  type="button"
                  :disabled="deletingId === notice.id"
                  @click="removeNotice(notice)"
                >
                  {{ deletingId === notice.id ? '删除中…' : '删除' }}
                </button>
              </div>
              <div class="card__body">
                <div class="inline">
                  <span class="badge" :class="levelClass(notice.level)">{{ notice.level }}</span>
                  <span class="muted">{{ formatDateTime(notice.publishedAt) }}</span>
                </div>
                <p class="notice__text">{{ notice.content }}</p>
              </div>
            </div>
          </div>
        </FeedbackState>

        <div class="card">
          <div class="card__head">
            <h2>新增公告</h2>
          </div>
          <div class="card__body">
            <form class="form" @submit.prevent="submitNotice">
              <div class="field">
                <label for="notice-title">标题</label>
                <input id="notice-title" v-model="form.title" class="input" type="text" placeholder="例如：意向填报截止时间提醒" />
              </div>

              <div class="field">
                <label for="notice-level">级别</label>
                <select id="notice-level" v-model="form.level" class="select">
                  <option v-for="level in levelOptions" :key="level" :value="level">{{ level }}</option>
                </select>
              </div>

              <div class="field">
                <label for="notice-content">内容</label>
                <textarea
                  id="notice-content"
                  v-model="form.content"
                  class="textarea"
                  placeholder="公告正文，将展示在学生端与导师端"
                ></textarea>
              </div>

              <p v-if="formError" class="form-error">{{ formError }}</p>

              <button class="btn btn--block" type="submit" :disabled="submitting">
                {{ submitting ? '发布中…' : '发布公告' }}
              </button>
            </form>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.schedule-table .input {
  min-width: 150px;
}

.schedule-table input[type='datetime-local'] {
  min-width: 205px;
}
</style>
