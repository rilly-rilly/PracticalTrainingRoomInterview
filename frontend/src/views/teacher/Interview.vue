<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { teacherApi } from '../../api'
import FeedbackState from '../../components/FeedbackState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { formatDateTime } from '../../utils/format'
import { pushToast } from '../../utils/toast'

const route = useRoute()

const loading = ref(false)
const error = ref(null)
const detail = ref(null)
const submitting = ref(false)
const scoreError = ref('')
const deciding = ref(false)
const decisionError = ref(null)

const form = reactive({
  attitude: 7,
  logic: 7,
  coding: 7,
  commitment: 7,
  comment: ''
})

const scoreFields = ['attitude', 'logic', 'coding', 'commitment']

const hasInterview = computed(() => Boolean(detail.value?.interview))

const total = computed(() =>
  scoreFields.reduce((sum, key) => sum + (Number(form[key]) || 0), 0)
)

const scoresValid = computed(() =>
  scoreFields.every((key) => {
    const v = form[key]
    return Number.isInteger(v) && v >= 1 && v <= 10
  })
)

const resultText = computed(() => {
  const d = detail.value
  if (!d) return ''
  if (d.status === '拟录取') return '已标记为拟录取，等待学生在规定时间内确认唯一去向。'
  if (d.status === '递补') {
    return d.rank ? `已加入递补名单，顺位第 ${d.rank}。` : '已加入递补名单。'
  }
  if (d.status === '已确认') return '学生已确认该志愿，录取名额已锁定。'
  if (d.status === '已录取') return '已正式录取入组。'
  if (d.status === '未录取') return '已标记为未录取，学生将进入调剂流程。'
  if (d.status === '已放弃') return '学生已放弃该志愿。'
  return '当前状态不可再作出录取决定。'
})

const resultTone = computed(() => {
  const status = detail.value?.status
  if (['拟录取', '已确认', '已录取'].includes(status)) return 'hint--success'
  if (status === '递补') return 'hint--warn'
  if (status === '未录取') return 'hint--danger'
  return ''
})

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await teacherApi.applicant(route.params.id)
    detail.value = res.data
  } catch (e) {
    error.value = e
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function submitScore() {
  scoreError.value = ''
  if (!scoresValid.value) {
    scoreError.value = '每项评分必须为 1~10 的整数'
    return
  }
  submitting.value = true
  try {
    const res = await teacherApi.score({
      intentionId: detail.value.id,
      attitude: form.attitude,
      logic: form.logic,
      coding: form.coding,
      commitment: form.commitment,
      comment: form.comment
    })
    pushToast(res.message, 'success')
    await load()
  } catch (e) {
    scoreError.value = e.message
  } finally {
    submitting.value = false
  }
}

const confirmText = {
  admit: '确认将该学生标记为「拟录取」？录取名额将由服务端校验。',
  waitlist: '确认将该学生加入「递补」名单？',
  reject: '确认将该学生标记为「未录取」？'
}

async function decide(decision) {
  if (!window.confirm(confirmText[decision])) return
  deciding.value = true
  decisionError.value = null
  try {
    const res = await teacherApi.decide({ intentionId: detail.value.id, decision })
    pushToast(res.message, 'success')
    await load()
  } catch (e) {
    decisionError.value = {
      message: e.message,
      quota: e.data?.quota ?? null,
      used: e.data?.used ?? null
    }
  } finally {
    deciding.value = false
  }
}

load()
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>面试考核</h1>
        <p v-if="detail" class="page-head__sub">
          {{ detail.studentName }} · {{ detail.studentNo }}
          <StatusBadge :status="detail.status" />
        </p>
        <p v-else class="page-head__sub">查看学生意向详情、录入面试评分并作出录取决定</p>
      </div>
      <div class="page-head__actions">
        <router-link to="/teacher/applicants" class="btn btn--ghost btn--sm">
          ← 返回报考学生列表
        </router-link>
      </div>
    </div>

    <FeedbackState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && !detail"
      empty-text="未找到该报考记录"
      @retry="load"
    >
      <div v-if="detail" class="stack">
        <div class="grid grid--2">
          <div class="card">
            <div class="card__head">
              <h3>学生信息</h3>
            </div>
            <div class="card__body">
              <div class="kv-grid">
                <div class="kv">
                  <span>学号</span>
                  <strong class="mono">{{ detail.studentNo }}</strong>
                </div>
                <div class="kv">
                  <span>姓名</span>
                  <strong>{{ detail.studentName }}</strong>
                </div>
                <div class="kv">
                  <span>专业</span>
                  <strong>{{ detail.major }}</strong>
                </div>
                <div class="kv">
                  <span>年级</span>
                  <strong>{{ detail.grade }}</strong>
                </div>
                <div class="kv">
                  <span>意向优先级</span>
                  <strong>{{ detail.priority ? '第 ' + detail.priority + ' 志愿' : '调剂' }}</strong>
                </div>
                <div class="kv">
                  <span>提交时间</span>
                  <strong>{{ formatDateTime(detail.submittedAt) }}</strong>
                </div>
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card__head">
              <h3>实训室与名额信息</h3>
            </div>
            <div class="card__body">
              <div class="info-row">
                <span>实训室</span>
                <strong>{{ detail.roomName }}</strong>
              </div>
              <div class="info-row">
                <span>名额上限</span>
                <strong>{{ detail.roomQuota }} 人</strong>
              </div>
              <div class="info-row">
                <span>已占用</span>
                <strong>{{ detail.roomUsed }} 人</strong>
              </div>
              <div class="info-row">
                <span>剩余名额</span>
                <strong>{{ Math.max(0, detail.roomQuota - detail.roomUsed) }} 人</strong>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card__head">
            <h3>个人简介与调剂意愿</h3>
          </div>
          <div class="card__body">
            <p class="muted">{{ detail.intro || '未填写个人简介' }}</p>
            <div class="divider"></div>
            <div class="info-row">
              <span>服从内部调剂</span>
              <strong>{{ detail.acceptInnerAdjust ? '是' : '否' }}</strong>
            </div>
            <div class="info-row">
              <span>服从全院调剂</span>
              <strong>{{ detail.acceptGlobalAdjust ? '是' : '否' }}</strong>
            </div>
          </div>
        </div>

        <div class="split">
          <div class="card">
            <div class="card__head">
              <h3>面试评分</h3>
            </div>
            <div class="card__body">
              <form v-if="detail.status === '待面试' && !hasInterview" class="form" @submit.prevent="submitScore">
                <div class="grid grid--2">
                  <div class="field">
                    <label>学习态度 <small class="field__hint">1~10 的整数</small></label>
                    <input
                      v-model.number="form.attitude"
                      class="input"
                      type="number"
                      min="1"
                      max="10"
                      step="1"
                    />
                  </div>
                  <div class="field">
                    <label>逻辑思维 <small class="field__hint">1~10 的整数</small></label>
                    <input
                      v-model.number="form.logic"
                      class="input"
                      type="number"
                      min="1"
                      max="10"
                      step="1"
                    />
                  </div>
                  <div class="field">
                    <label>编程基础 <small class="field__hint">1~10 的整数</small></label>
                    <input
                      v-model.number="form.coding"
                      class="input"
                      type="number"
                      min="1"
                      max="10"
                      step="1"
                    />
                  </div>
                  <div class="field">
                    <label>时间投入 <small class="field__hint">1~10 的整数</small></label>
                    <input
                      v-model.number="form.commitment"
                      class="input"
                      type="number"
                      min="1"
                      max="10"
                      step="1"
                    />
                  </div>
                </div>

                <div class="field">
                  <label>评语 <small class="field__hint">可选</small></label>
                  <textarea
                    v-model="form.comment"
                    class="textarea"
                    placeholder="记录面试表现、技术基础与投入意愿等"
                  ></textarea>
                </div>

                <div class="hint--box">实时总分：<strong>{{ total }}</strong> / 40</div>

                <p v-if="scoreError" class="form-error">{{ scoreError }}</p>

                <div class="btn-row">
                  <button type="submit" class="btn" :disabled="submitting">
                    {{ submitting ? '保存中…' : '保存面试评分' }}
                  </button>
                </div>
              </form>

              <div v-else-if="hasInterview" class="stack">
                <div class="kv-grid">
                  <div class="kv">
                    <span>学习态度</span>
                    <strong>{{ detail.interview.attitude }}</strong>
                  </div>
                  <div class="kv">
                    <span>逻辑思维</span>
                    <strong>{{ detail.interview.logic }}</strong>
                  </div>
                  <div class="kv">
                    <span>编程基础</span>
                    <strong>{{ detail.interview.coding }}</strong>
                  </div>
                  <div class="kv">
                    <span>时间投入</span>
                    <strong>{{ detail.interview.commitment }}</strong>
                  </div>
                </div>
                <div class="info-row">
                  <span>总分</span>
                  <strong>{{ detail.interview.total }}</strong>
                </div>
                <div class="info-row">
                  <span>评分时间</span>
                  <strong>{{ formatDateTime(detail.interview.createdAt) }}</strong>
                </div>
                <p>评语：{{ detail.interview.comment || '未填写评语' }}</p>
                <p class="hint hint--box hint--success">评分已保存</p>
              </div>

              <p v-else class="muted">当前状态不可录入面试评分。</p>
            </div>
          </div>

          <div class="card">
            <div class="card__head">
              <h3>录取决定</h3>
            </div>
            <div class="card__body">
              <div class="stack">
                <div class="hint hint--box">
                  录取名额由服务端校验，前端传入的名额参数不采信。当前实训室已占用
                  <strong>{{ detail.roomUsed }}</strong> / <strong>{{ detail.roomQuota }}</strong> 人。
                </div>

                <template v-if="detail.status === '面试完成'">
                  <div class="btn-row">
                    <button
                      type="button"
                      class="btn btn--success btn--sm"
                      :disabled="deciding"
                      @click="decide('admit')"
                    >
                      拟录取
                    </button>
                    <button
                      type="button"
                      class="btn btn--warn btn--sm"
                      :disabled="deciding"
                      @click="decide('waitlist')"
                    >
                      加入递补
                    </button>
                    <button
                      type="button"
                      class="btn btn--danger btn--sm"
                      :disabled="deciding"
                      @click="decide('reject')"
                    >
                      不录取
                    </button>
                  </div>

                  <div v-if="decisionError" class="form-error">
                    <p>{{ decisionError.message }}</p>
                    <p v-if="decisionError.quota != null">
                      名额上限 {{ decisionError.quota }} 人，当前已占用 {{ decisionError.used }} 人。
                    </p>
                  </div>
                </template>

                <div v-else-if="detail.status === '待面试'" class="hint--box hint--warn">
                  需先完成面试评分
                </div>

                <div v-else class="hint hint--box" :class="resultTone">
                  <div class="inline">
                    <strong>当前结果</strong>
                    <StatusBadge :status="detail.status" />
                  </div>
                  <p>{{ resultText }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </FeedbackState>
  </div>
</template>
