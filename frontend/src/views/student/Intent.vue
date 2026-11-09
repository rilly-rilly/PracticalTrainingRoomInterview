<script setup>
import { computed, ref } from 'vue'
import { publicApi, studentApi } from '../../api'
import FeedbackState from '../../components/FeedbackState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { formatDateTime, windowStateText } from '../../utils/format'
import { pushToast } from '../../utils/toast'

const loading = ref(true)
const error = ref(null)
const home = ref(null)
const intentions = ref([])
const rooms = ref([])

const formError = ref('')
const submitting = ref(false)
const intro = ref('')
const choices = ref(['', '', ''])
const acceptInnerAdjust = ref(true)
const acceptGlobalAdjust = ref(true)

const intentWindow = computed(() => home.value?.intentWindow || null)
const windowLabel = computed(() => windowStateText(intentWindow.value))
const isOpen = computed(() => Boolean(intentWindow.value?.isOpen))
const submittedInfo = computed(() => intentions.value[0] || null)
const selectedTeachers = computed(() => choices.value.filter(Boolean))
const introLength = computed(() => intro.value.trim().length)

async function load() {
  loading.value = true
  error.value = null
  try {
    const [homeRes, intentionRes] = await Promise.all([publicApi.home(), studentApi.intentions()])
    home.value = homeRes.data
    intentions.value = intentionRes.data || []

    const roomRes = await publicApi.rooms()
    const roomList = roomRes.data || []
    const details = await Promise.all(roomList.map((room) => publicApi.room(room.id)))
    rooms.value = roomList.map((room, index) => ({
      ...room,
      teachers: details[index]?.data?.teachers || []
    }))
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

function validate() {
  if (selectedTeachers.value.length < 1) return '请至少选择 1 个意向导师'
  if (selectedTeachers.value.length > 3) return '最多填报 3 个平行意向'
  if (new Set(selectedTeachers.value).size !== selectedTeachers.value.length) {
    return '同一导师不能重复填报，请调整选择'
  }
  if (introLength.value < 20) {
    return `个人简介不能少于 20 字，当前 ${introLength.value} 字`
  }
  return ''
}

async function handleSubmit() {
  formError.value = ''
  const invalid = validate()
  if (invalid) {
    formError.value = invalid
    return
  }
  submitting.value = true
  try {
    await studentApi.submit({
      choices: selectedTeachers.value,
      intro: intro.value.trim(),
      acceptInnerAdjust: acceptInnerAdjust.value,
      acceptGlobalAdjust: acceptGlobalAdjust.value
    })
    pushToast('意向提交成功，本轮平行意向已记录', 'success', 200)
    await load()
  } catch (e) {
    formError.value = `[${e.code}] ${e.message}`
  } finally {
    submitting.value = false
  }
}

load()
</script>

<template>
  <main class="page">
    <div class="page-head">
      <div>
        <h1>意向填报</h1>
        <p class="page-head__sub">
          最多填报 3 个平行意向（不分先后），同一导师不可重复；另需填写一段不少于 20 字的个人简介，并选择是否服从调剂。
        </p>
      </div>
      <div v-if="!loading && !error" class="page-head__actions">
        <span class="badge" :class="isOpen ? 'badge--confirmed' : 'badge--pending'">
          填报窗口 · {{ windowLabel }}
        </span>
      </div>
    </div>

    <FeedbackState :loading="loading" :error="error" @retry="load">
      <div class="stack">
        <template v-if="intentions.length">
          <section class="card">
            <div class="card__head">
              <h2>已提交意向</h2>
              <span class="badge badge--confirmed">已提交</span>
            </div>
            <div class="card__body stack">
              <div class="hint--box hint--success">
                本轮意向已提交，不可修改。以下 {{ intentions.length }} 条意向为平行关系，最终以导师面试与录取结果为准。
              </div>
              <div class="table-wrap">
                <table class="table">
                  <thead>
                    <tr>
                      <th>意向序号</th>
                      <th>导师</th>
                      <th>实训室</th>
                      <th>状态</th>
                      <th>提交时间</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in intentions" :key="item.id">
                      <td>
                        <strong>第 {{ item.priority }} 意向</strong>
                      </td>
                      <td>{{ item.teacherName }}<span class="muted">（{{ item.teacherNo }}）</span></td>
                      <td>{{ item.roomName }}</td>
                      <td><StatusBadge :status="item.status" /></td>
                      <td>{{ formatDateTime(item.submittedAt) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </section>

          <div class="grid grid--2">
            <div class="card card--flush">
              <div class="card__head">
                <h3>个人简介</h3>
              </div>
              <div class="card__body">
                <p style="font-size: 13px; color: var(--text-2); line-height: 1.8">
                  {{ submittedInfo?.intro || '—' }}
                </p>
              </div>
            </div>
            <div class="card card--flush">
              <div class="card__head">
                <h3>调剂意愿</h3>
              </div>
              <div class="card__body">
                <div class="info-row">
                  <span>服从实训室内部调剂</span>
                  <strong>{{ submittedInfo?.acceptInnerAdjust ? '是' : '否' }}</strong>
                </div>
                <div class="info-row">
                  <span>服从全院跨实训室调剂</span>
                  <strong>{{ submittedInfo?.acceptGlobalAdjust ? '是' : '否' }}</strong>
                </div>
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="hint--box" :class="isOpen ? 'hint--success' : 'hint--warn'">
            <strong>填报时间窗口：{{ formatDateTime(intentWindow?.startAt) }} 至 {{ formatDateTime(intentWindow?.endAt) }}（{{ windowLabel }}）</strong>
            <p style="margin-top: 4px">
              窗口关闭后仍可点击提交，用于演示后端 410 拦截；拦截信息会显示在表单上方。
            </p>
          </div>

          <div class="hint--box">
            <strong>当前为空数据态，请完成填报。</strong>
            <p style="margin-top: 4px">
              三个下拉框分别对应第一、第二、第三意向，可只填 1 个，但不可重复选择同一位导师。
            </p>
          </div>

          <section class="card">
            <div class="card__head">
              <h2>填写意向</h2>
              <span class="hint">平行意向 · 不分先后</span>
            </div>
            <div class="card__body">
              <div v-if="formError" class="form-error" style="margin-bottom: 14px">{{ formError }}</div>

              <form class="form" @submit.prevent="handleSubmit">
                <div class="field">
                  <label for="student-intro">
                    个人简介
                    <span class="field__hint">{{ introLength }} / 20 字起</span>
                  </label>
                  <textarea
                    id="student-intro"
                    v-model="intro"
                    class="textarea"
                    placeholder="请介绍你的专业背景、技能方向、项目经历与进组后的学习计划（不少于 20 字）"
                  ></textarea>
                  <span class="field__hint" :class="introLength && introLength < 20 ? 'muted' : ''">
                    {{ introLength && introLength < 20 ? '少于 20 字，暂不能提交' : '至少 20 字，提交后不可修改' }}
                  </span>
                </div>

                <div class="grid grid--3">
                  <div v-for="(slot, index) in ['第一意向', '第二意向', '第三意向']" :key="slot" class="field">
                    <label :for="`intent-slot-${index}`">{{ slot }}</label>
                    <select :id="`intent-slot-${index}`" v-model="choices[index]" class="select">
                      <option value="">不填报</option>
                      <optgroup
                        v-for="room in rooms"
                        :key="room.id"
                        :label="`${room.name} · ${room.code}`"
                      >
                        <option v-for="teacher in room.teachers" :key="teacher.id" :value="teacher.id">
                          {{ teacher.name }} · {{ teacher.title }}
                        </option>
                      </optgroup>
                    </select>
                  </div>
                </div>
                <span class="field__hint">同一导师不可在多个意向中重复填报。</span>

                <div class="stack">
                  <label class="checkline">
                    <input v-model="acceptInnerAdjust" type="checkbox" />
                    <span>
                      <strong>服从实训室内部调剂</strong>
                      <small>未被录取时，优先在本实训室其他未招满导师中调剂</small>
                    </span>
                  </label>
                  <label class="checkline">
                    <input v-model="acceptGlobalAdjust" type="checkbox" />
                    <span>
                      <strong>服从全院跨实训室调剂</strong>
                      <small>本实训室无法调剂时，接受全院其他实训室的调剂安排</small>
                    </span>
                  </label>
                </div>

                <div class="btn-row">
                  <button class="btn btn--lg" type="submit" :disabled="submitting">
                    {{ submitting ? '提交中…' : '提交意向' }}
                  </button>
                  <span class="hint">提交成功后本轮意向不可修改。</span>
                </div>
              </form>
            </div>
          </section>
        </template>
      </div>
    </FeedbackState>
  </main>
</template>
