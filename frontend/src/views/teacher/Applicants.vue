<script setup>
import { computed, reactive, ref } from 'vue'
import { teacherApi } from '../../api'
import FeedbackState from '../../components/FeedbackState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { formatDateTime } from '../../utils/format'
import { pushToast } from '../../utils/toast'

const tabs = [
  { value: '', label: '全部' },
  { value: '待面试', label: '待面试' },
  { value: '面试完成', label: '面试完成' },
  { value: '拟录取', label: '拟录取' },
  { value: '递补', label: '递补' },
  { value: '已确认', label: '已确认' },
  { value: '未录取', label: '未录取' }
]

const loading = ref(false)
const error = ref(null)
const applications = ref([])
const teacher = ref(null)
const room = ref(null)
const statusCounts = ref({})
const activeStatus = ref('')
const expandedId = ref(null)
const decidingId = ref(null)
const decisionErrors = reactive({})

const totalCount = computed(() =>
  Object.values(statusCounts.value || {}).reduce((sum, n) => sum + n, 0)
)

function countOf(value) {
  if (!value) return totalCount.value
  return statusCounts.value?.[value] ?? 0
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await teacherApi.applicants({ status: activeStatus.value })
    const data = res.data || {}
    applications.value = data.applications || []
    teacher.value = data.teacher || null
    room.value = data.room || null
    statusCounts.value = data.statusCounts || {}
  } catch (e) {
    error.value = e
    applications.value = []
  } finally {
    loading.value = false
  }
}

function changeStatus(value) {
  if (activeStatus.value === value) return
  activeStatus.value = value
  expandedId.value = null
  load()
}

function toggle(id) {
  expandedId.value = expandedId.value === id ? null : id
  if (expandedId.value) delete decisionErrors[id]
}

async function decide(item, decision) {
  decidingId.value = item.id
  delete decisionErrors[item.id]
  try {
    const res = await teacherApi.decide({ intentionId: item.id, decision })
    pushToast(res.message, 'success')
    await load()
  } catch (e) {
    decisionErrors[item.id] = e.message
  } finally {
    decidingId.value = null
  }
}

load()
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>报考学生与考核</h1>
        <p class="page-head__sub">查看报考你实训室的学生，录入面试评分并作出录取决定</p>
      </div>
    </div>

    <div class="card section">
      <div class="card__body">
        <div class="grid grid--4">
          <div class="kv">
            <span>当前导师</span>
            <strong>{{ teacher?.name || '—' }}</strong>
            <div class="hint">工号 {{ teacher?.no || '—' }}</div>
          </div>
          <div class="kv">
            <span>所属实训室</span>
            <strong>{{ room?.name || teacher?.roomName || '—' }}</strong>
          </div>
          <div class="kv">
            <span>名额占用（已用 / 上限）</span>
            <strong>{{ room?.used ?? 0 }} / {{ room?.quota ?? 0 }}</strong>
          </div>
          <div class="kv">
            <span>剩余名额</span>
            <strong>{{ room?.remaining ?? 0 }} 人</strong>
          </div>
        </div>
      </div>
    </div>

    <section class="section">
      <div class="section__head">
        <h2>报考学生</h2>
        <div class="tabs">
          <button
            v-for="tab in tabs"
            :key="tab.value || 'all'"
            type="button"
            class="tab"
            :class="{ 'is-active': activeStatus === tab.value }"
            @click="changeStatus(tab.value)"
          >
            {{ tab.label }}
            <span class="tab__count">{{ countOf(tab.value) }}</span>
          </button>
        </div>
      </div>

      <div class="card">
        <FeedbackState
          v-if="loading || error || applications.length === 0"
          :loading="loading"
          :error="error"
          :empty="!loading && !error"
          empty-text="当前筛选条件下暂无报考学生"
          @retry="load"
        />

        <div v-else class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>学号 / 姓名</th>
                <th>专业</th>
                <th>意向优先级</th>
                <th>实训室</th>
                <th>面试总分</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="item in applications" :key="item.id">
                <tr>
                  <td>
                    <strong>{{ item.studentName }}</strong>
                    <div class="mono muted">{{ item.studentNo }}</div>
                  </td>
                  <td>
                    {{ item.major }}
                    <div class="muted">{{ item.grade }}</div>
                  </td>
                  <td>{{ item.priority ? '第 ' + item.priority + ' 志愿' : '调剂' }}</td>
                  <td>{{ item.roomName }}</td>
                  <td>{{ item.interview?.total ?? '—' }}</td>
                  <td><StatusBadge :status="item.status" /></td>
                  <td>
                    <div class="btn-row">
                      <router-link
                        class="btn btn--sm btn--ghost"
                        :to="`/teacher/interview/${item.id}`"
                      >
                        {{ item.status === '待面试' ? '录入评分' : '查看详情' }}
                      </router-link>
                      <button
                        type="button"
                        class="btn btn--sm btn--ghost"
                        @click="toggle(item.id)"
                      >
                        {{ expandedId === item.id ? '收起简介' : '展开简介' }}
                      </button>
                    </div>
                  </td>
                </tr>
                <tr v-if="expandedId === item.id">
                  <td colspan="7">
                    <div class="stack">
                      <div>
                        <h3>个人简介</h3>
                        <p class="muted">{{ item.intro || '未填写个人简介' }}</p>
                      </div>

                      <div class="kv-grid">
                        <div class="kv">
                          <span>服从内部调剂</span>
                          <strong>{{ item.acceptInnerAdjust ? '是' : '否' }}</strong>
                        </div>
                        <div class="kv">
                          <span>服从全院调剂</span>
                          <strong>{{ item.acceptGlobalAdjust ? '是' : '否' }}</strong>
                        </div>
                      </div>

                      <div>
                        <h3>面试评分</h3>
                        <div v-if="item.interview" class="stack">
                          <div class="tag-row">
                            <span class="tag">学习态度 {{ item.interview.attitude }}</span>
                            <span class="tag">逻辑思维 {{ item.interview.logic }}</span>
                            <span class="tag">编程基础 {{ item.interview.coding }}</span>
                            <span class="tag">时间投入 {{ item.interview.commitment }}</span>
                            <span class="tag">总分 {{ item.interview.total }}</span>
                          </div>
                          <p>评语：{{ item.interview.comment || '未填写评语' }}</p>
                          <p class="hint">评分时间：{{ formatDateTime(item.interview.createdAt) }}</p>
                        </div>
                        <p v-else class="muted">尚未录入面试评分</p>
                      </div>

                      <div v-if="item.status === '面试完成'" class="btn-row">
                        <button
                          type="button"
                          class="btn btn--success btn--sm"
                          :disabled="decidingId === item.id"
                          @click="decide(item, 'admit')"
                        >
                          拟录取
                        </button>
                        <button
                          type="button"
                          class="btn btn--warn btn--sm"
                          :disabled="decidingId === item.id"
                          @click="decide(item, 'waitlist')"
                        >
                          递补
                        </button>
                        <button
                          type="button"
                          class="btn btn--danger btn--sm"
                          :disabled="decidingId === item.id"
                          @click="decide(item, 'reject')"
                        >
                          不录取
                        </button>
                      </div>

                      <p v-if="decisionErrors[item.id]" class="form-error">
                        {{ decisionErrors[item.id] }}
                      </p>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>
