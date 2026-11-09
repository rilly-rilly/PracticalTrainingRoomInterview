<script setup>
import { computed, ref } from 'vue'
import { adminApi } from '../../api'
import FeedbackState from '../../components/FeedbackState.vue'
import { formatDateTime } from '../../utils/format'

const loading = ref(true)
const error = ref(null)
const data = ref(null)

const runBusy = ref('')
const runResult = ref(null)
const runError = ref('')

const priorityBlocks = [
  {
    no: '①',
    title: '第一优先级 · 实训室内部调剂',
    text: '在未录取学生本人已填报的实训室内部更换导师，尽量保留其原志愿方向。'
  },
  {
    no: '②',
    title: '第二优先级 · 全院跨实训室调剂',
    text: '原填报实训室均无名额时，在全院其他仍有空缺的实训室中寻找机会。'
  },
  {
    no: '③',
    title: '第三优先级 · 约谈不服从调剂学生',
    text: '对全部未录取且不接受内部、全院调剂的学生生成约谈安排，由学院统一处理。'
  }
]

const actions = [
  {
    type: 'inner',
    label: '执行实训室内部调剂（第一优先级）',
    desc: '在本人填报的实训室内部更换导师，优先保障原志愿方向。',
    cls: ''
  },
  {
    type: 'global',
    label: '执行全院跨实训室调剂（第二优先级）',
    desc: '原实训室无名额时，在全院其他实训室寻找空缺名额。',
    cls: 'btn--ghost'
  },
  {
    type: 'interview',
    label: '第三优先级：约谈不服从调剂学生',
    desc: '为全部未录取且不接受调剂的学生生成约谈安排。',
    cls: 'btn--warn'
  },
  {
    type: 'supplement',
    label: '执行分轮补录',
    desc: '按递补顺位补齐各实训室空缺名额，必要时自动确认。',
    cls: 'btn--success'
  }
]

const studentMap = computed(() => {
  const map = new Map()
  for (const s of data.value?.students || []) map.set(s.studentId, s.name)
  return map
})

async function fetchData(silent = false) {
  if (!silent) loading.value = true
  error.value = null
  try {
    const res = await adminApi.adjustments()
    data.value = res.data
  } catch (e) {
    error.value = { code: e.code, message: e.message }
  } finally {
    if (!silent) loading.value = false
  }
}
fetchData()

async function runAction(item) {
  if (!window.confirm(`确认${item.label}？系统将写入调剂/补录记录。`)) return
  runBusy.value = item.type
  runResult.value = null
  runError.value = ''
  try {
    const res = await adminApi.runAdjustment({ type: item.type })
    runResult.value = {
      title: item.label,
      message: res.message,
      affected: res.data?.affected ?? 0,
      skipped: res.data?.skipped || []
    }
    await fetchData(true)
  } catch (e) {
    runError.value = `[${e?.code ?? 'ERR'}] ${e?.message || '请求失败'}`
  } finally {
    runBusy.value = ''
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>补录与调剂</h1>
        <p class="page-head__sub">按优先级执行实训室内部调剂、全院调剂与约谈安排，并处理分轮补录。</p>
      </div>
      <div class="page-head__actions">
        <button class="btn btn--ghost btn--sm" :disabled="loading" @click="fetchData()">刷新数据</button>
      </div>
    </div>

    <FeedbackState
      :loading="loading"
      :error="error"
      :empty="!data"
      empty-text="暂无可调剂的数据"
      @retry="fetchData()"
    >
      <section class="section">
        <div class="section__head">
          <div>
            <h2>实训室名额可用性</h2>
            <p class="section__desc">调剂与补录均受各实训室剩余名额限制。</p>
          </div>
        </div>
        <FeedbackState v-if="!data.rooms.length" :empty="true" empty-text="暂无实训室数据" />
        <div v-else class="grid grid--4">
          <div v-for="room in data.rooms" :key="room.id" class="card">
            <div class="card__body">
              <h3>{{ room.name }}</h3>
              <div class="info-row">
                <span>名额</span>
                <strong>{{ room.quota }}</strong>
              </div>
              <div class="info-row">
                <span>已占用</span>
                <strong>{{ room.used }}</strong>
              </div>
              <div class="info-row">
                <span>剩余</span>
                <strong>{{ room.remaining }}</strong>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>调剂优先级</h2>
            <p class="section__desc">请自上而下依次执行，前一级完成后再进入下一级。</p>
          </div>
        </div>
        <div class="grid grid--3">
          <div v-for="block in priorityBlocks" :key="block.no" class="hint--box">
            <strong>{{ block.no }} {{ block.title }}</strong>
            <p>{{ block.text }}</p>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>调剂与补录操作</h2>
            <p class="section__desc">每项操作执行前都会二次确认，执行结果将同步写入下方记录。</p>
          </div>
        </div>
        <div class="grid grid--2">
          <div v-for="item in actions" :key="item.type" class="card">
            <div class="card__body stack">
              <button
                class="btn btn--block"
                :class="item.cls"
                :disabled="!!runBusy"
                @click="runAction(item)"
              >
                {{ runBusy === item.type ? '执行中…' : item.label }}
              </button>
              <p class="hint">{{ item.desc }}</p>
            </div>
          </div>
        </div>

        <div v-if="runResult" class="hint--box hint--success" style="margin-top: 16px">
          <strong>{{ runResult.title }}</strong>
          <p>{{ runResult.message }}</p>
          <p>影响人数：{{ runResult.affected }}</p>
          <p v-for="skipped in runResult.skipped" :key="skipped.name" class="hint">
            未能调剂：{{ skipped.name }} —— {{ skipped.reason }}
          </p>
        </div>
        <div v-if="runError" class="form-error" style="margin-top: 16px">{{ runError }}</div>
      </section>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>待调剂学生名单</h2>
            <p class="section__desc">共 {{ data.students.length }} 名学生，标注其调剂意愿与是否已调剂。</p>
          </div>
        </div>
        <FeedbackState v-if="!data.students.length" :empty="true" empty-text="暂无学生数据" />
        <div v-else class="card">
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>学生</th>
                  <th>专业</th>
                  <th>意向状态</th>
                  <th>服从内部调剂</th>
                  <th>服从全院调剂</th>
                  <th>是否已调剂</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="s in data.students" :key="s.studentId">
                  <td>
                    <strong>{{ s.name }}</strong><br />
                    <span class="mono muted">{{ s.studentNo }}</span>
                  </td>
                  <td>{{ s.major || '—' }}</td>
                  <td>{{ s.lastStatus || '—' }}</td>
                  <td>
                    <span
                      class="badge"
                      :class="s.acceptInnerAdjust ? 'badge--confirmed' : 'badge--givenup'"
                    >
                      {{ s.acceptInnerAdjust ? '是' : '否' }}
                    </span>
                  </td>
                  <td>
                    <span
                      class="badge"
                      :class="s.acceptGlobalAdjust ? 'badge--confirmed' : 'badge--givenup'"
                    >
                      {{ s.acceptGlobalAdjust ? '是' : '否' }}
                    </span>
                  </td>
                  <td>
                    <span class="badge" :class="s.adjusted ? 'badge--admitted' : 'badge--givenup'">
                      {{ s.adjusted ? '已调剂' : '未调剂' }}
                    </span>
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
            <h2>调剂与补录记录</h2>
            <p class="section__desc">共 {{ data.records.length }} 条记录，按时间倒序排列。</p>
          </div>
        </div>
        <FeedbackState
          v-if="!data.records.length"
          :empty="true"
          empty-text="暂无调剂与补录记录，可先执行上方调剂操作"
        />
        <div v-else class="card">
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>学生姓名</th>
                  <th>类型</th>
                  <th>原实训室 → 新实训室</th>
                  <th>新导师</th>
                  <th>结果</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in data.records" :key="record.id">
                  <td class="mono">{{ formatDateTime(record.createdAt) }}</td>
                  <td><strong>{{ studentMap.get(record.studentId) || record.studentId }}</strong></td>
                  <td>{{ record.typeLabel }}</td>
                  <td>
                    {{ record.fromRoomName || '—' }} → {{ record.toRoomName || '—' }}
                  </td>
                  <td>{{ record.toTeacherName || '—' }}</td>
                  <td>{{ record.result }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </FeedbackState>
  </div>
</template>
