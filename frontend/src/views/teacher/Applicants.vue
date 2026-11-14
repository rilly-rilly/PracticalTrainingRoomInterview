<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { teacherApi } from '../../api'
import FeedbackState from '../../components/FeedbackState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { formatDateTime } from '../../utils/format'
import { pushToast } from '../../utils/toast'
import { matchKeyword, compareByInitial } from '../../utils/pinyin'

const PAGE_SIZE = 15

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

// ---- 新增：筛选 / 搜索 / 排序 / 分页 ----
const keyword = ref('')
const majorFilter = ref('')
const priorityFilter = ref('')
const sortField = ref('studentNo')   // 'studentNo' | 'name'
const sortOrder = ref('asc')          // 'asc' | 'desc'
const currentPage = ref(1)

const totalCount = computed(() =>
  Object.values(statusCounts.value || {}).reduce((sum, n) => sum + n, 0)
)

function countOf(value) {
  if (!value) return totalCount.value
  return statusCounts.value?.[value] ?? 0
}

// 可用专业列表（去重）
const majorOptions = computed(() => {
  const set = new Set()
  applications.value.forEach((a) => a.major && set.add(a.major))
  return [...set].sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'))
})

// 可用意向优先级列表
const priorityOptions = computed(() => {
  const set = new Set()
  applications.value.forEach((a) => {
    if (a.priority) set.add(a.priority)
  })
  return [...set].sort((a, b) => a - b)
})

// 切换任一筛选条件时回到第一页
watch(
  [activeStatus, keyword, majorFilter, priorityFilter, sortField, sortOrder],
  () => {
    currentPage.value = 1
  }
)

// 过滤 + 排序后的完整列表
const filteredList = computed(() => {
  let list = applications.value

  // 1. 状态过滤（虽然 Tab 也会调 load，但保留双保险）
  if (activeStatus.value) {
    list = list.filter((a) => a.status === activeStatus.value)
  }

  // 2. 搜索：姓名 / 学号 / 拼音
  if (keyword.value) {
    const kw = keyword.value.trim()
    list = list.filter((a) => {
      return (
        matchKeyword(a.studentName, kw) ||
        matchKeyword(a.studentNo, kw)
      )
    })
  }

  // 3. 专业筛选
  if (majorFilter.value) {
    list = list.filter((a) => a.major === majorFilter.value)
  }

  // 4. 意向优先级筛选
  if (priorityFilter.value) {
    const p = Number(priorityFilter.value)
    list = list.filter((a) => a.priority === p)
  }

  // 5. 排序
  const order = sortOrder.value
  list = [...list].sort((a, b) => {
    if (sortField.value === 'name') {
      return compareByInitial(a, b, (x) => x.studentName, order)
    }
    // studentNo
    let cmp = String(a.studentNo).localeCompare(String(b.studentNo), 'zh-Hans-CN')
    return order === 'asc' ? cmp : -cmp
  })

  return list
})

// 分页切片
const totalPages = computed(() => {
  const total = filteredList.value.length
  if (total === 0) return 0
  return Math.ceil(total / PAGE_SIZE)
})

const pagedList = computed(() => {
  if (totalPages.value === 0) return []
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filteredList.value.slice(start, start + PAGE_SIZE)
})

// 页码范围（当前页前后各 1 页）
const pageNumbers = computed(() => {
  const total = totalPages.value
  const cur = currentPage.value
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }
  const pages = []
  pages.push(1)
  if (cur > 3) pages.push('...')
  for (let i = Math.max(2, cur - 1); i <= Math.min(total - 1, cur + 1); i++) {
    pages.push(i)
  }
  if (cur < total - 2) pages.push('...')
  pages.push(total)
  return pages
})

function goPage(p) {
  if (typeof p === 'number' && p >= 1 && p <= totalPages.value) {
    currentPage.value = p
  }
}

function resetFilters() {
  keyword.value = ''
  majorFilter.value = ''
  priorityFilter.value = ''
  sortField.value = 'studentNo'
  sortOrder.value = 'asc'
  activeStatus.value = ''
  expandedId.value = null
  currentPage.value = 1
  load()
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

      <!-- 工具栏：搜索 / 筛选 / 排序 / 重置 -->
      <div class="toolbar">
        <div class="toolbar__left">
          <div class="toolbar__group">
            <label class="toolbar__label">排序</label>
            <select v-model="sortField" class="select select--sm">
              <option value="studentNo">学号</option>
              <option value="name">姓名首字母</option>
            </select>
            <button
              type="button"
              class="btn btn--sm btn--ghost toolbar__order"
              :title="sortOrder === 'asc' ? '升序' : '降序'"
              @click="sortOrder = sortOrder === 'asc' ? 'desc' : 'asc'"
            >
              <span v-if="sortOrder === 'asc'">↑ 升</span>
              <span v-else>↓ 降</span>
            </button>
          </div>

          <div class="toolbar__divider"></div>

          <div class="toolbar__group">
            <label class="toolbar__label">专业</label>
            <select v-model="majorFilter" class="select select--sm">
              <option value="">全部</option>
              <option v-for="m in majorOptions" :key="m" :value="m">{{ m }}</option>
            </select>
          </div>

          <div class="toolbar__group">
            <label class="toolbar__label">意向</label>
            <select v-model="priorityFilter" class="select select--sm">
              <option value="">全部</option>
              <option v-for="p in priorityOptions" :key="p" :value="p">第 {{ p }} 志愿</option>
            </select>
          </div>
        </div>

        <div class="toolbar__right">
          <div class="search-wrap">
            <svg class="search-wrap__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              v-model="keyword"
              type="text"
              class="input input--search"
              placeholder="搜索姓名、学号、拼音（如 zym）"
            />
            <button
              v-if="keyword"
              type="button"
              class="search-wrap__clear"
              title="清空"
              @click="keyword = ''"
            >×</button>
          </div>
          <button type="button" class="btn btn--sm btn--ghost" @click="resetFilters">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="1 4 1 10 7 10" />
              <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" />
            </svg>
            重置
          </button>
        </div>
      </div>

      <div class="card">
        <!-- 加载 / 错误 / 无后端数据 -->
        <FeedbackState
          v-if="loading || error || (applications.length === 0 && !keyword && !majorFilter && !priorityFilter)"
          :loading="loading"
          :error="error"
          :empty="!loading && !error && applications.length === 0"
          empty-text="当前筛选条件下暂无报考学生"
          @retry="load"
        />

        <!-- 有后端数据但搜索/筛选无匹配 -->
        <FeedbackState
          v-else-if="pagedList.length === 0"
          :loading="false"
          :empty="true"
          empty-text="暂无匹配数据，请尝试调整搜索关键词或筛选条件"
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
              <template v-for="item in pagedList" :key="item.id">
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

          <!-- 分页：仅当筛选后有数据时显示 -->
          <div v-if="filteredList.length > PAGE_SIZE" class="pagination">
            <span class="pagination__total">共 {{ filteredList.length }} 条</span>
            <button
              type="button"
              class="pagination__btn"
              :disabled="currentPage === 1"
              @click="goPage(currentPage - 1)"
            >上一页</button>
            <template v-for="(p, idx) in pageNumbers" :key="idx">
              <span v-if="p === '...'" class="pagination__ellipsis">…</span>
              <button
                v-else
                type="button"
                class="pagination__btn"
                :class="{ 'is-active': p === currentPage }"
                @click="goPage(p)"
              >{{ p }}</button>
            </template>
            <button
              type="button"
              class="pagination__btn"
              :disabled="currentPage === totalPages"
              @click="goPage(currentPage + 1)"
            >下一页</button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
