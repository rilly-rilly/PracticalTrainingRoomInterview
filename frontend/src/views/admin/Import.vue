<script setup>
import { computed, ref } from 'vue'
import FeedbackState from '../../components/FeedbackState.vue'
import { adminApi } from '../../api'
import { pushToast } from '../../utils/toast'

const templates = {
  student: ['学号', '姓名', '专业', '年级'],
  teacher: ['工号', '姓名', '职称', '实训室编号']
}

const samples = {
  student: ['学号,姓名,专业,年级', 'S2026111,林一舟,计算机科学与技术,2024 级', 'S2026112,何雨薇,软件工程,2024 级', 'S2026001,张一鸣,计算机科学与技术,2024 级'].join('\n'),
  teacher: ['工号,姓名,职称,实训室编号', 'T2011,钱学远,副教授,RV-01', 'T2012,周清和,讲师,RV-03', 'T1001,李伯谦,副教授,RV-01'].join('\n')
}

const type = ref('student')
const text = ref('')
const strict = ref(true)
const parsing = ref(false)
const importing = ref(false)
const parsedRows = ref([])
const parseNote = ref('')
const result = ref(null)
const error = ref(null)

const template = computed(() => templates[type.value])

const placeholder = computed(() => {
  const first = samples[type.value].split('\n')
  return `${first[0]}\n${first[1]}\n…`
})

const activeFailures = computed(() => {
  if (result.value) return result.value.failures || []
  if (error.value) return error.value.failures || []
  return []
})

const accounts = computed(() => (result.value ? result.value.accounts || [] : []))

const accountNoLabel = computed(() => (type.value === 'student' ? '学号' : '工号'))

const errorHint = computed(() => {
  if (!error.value) return ''
  if (error.value.strict) return '严格模式下任一行失败即整体回滚，本次未写入任何账号，请修正失败行后重新导入。'
  return '本次没有可导入的有效数据，请检查必填字段与账号唯一性。'
})

function splitCells(line) {
  return line
    .split(/[,，\t]/)
    .map((cell) => cell.trim().replace(/^"|"$/g, '').trim())
}

function parseCsv(raw) {
  const lines = raw
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line !== '')

  if (lines.length === 0) return { rows: [], note: '请先粘贴 CSV 内容' }

  const header = splitCells(lines[0])
  const required = type.value === 'student' ? ['学号', '姓名', '专业', '年级'] : ['工号', '姓名', '实训室编号']
  const missing = required.filter((key) => !header.includes(key))
  if (missing.length > 0) {
    return { rows: [], note: `表头缺少列：${missing.join('、')}，请按模板表头填写` }
  }

  const rows = lines.slice(1).map((line) => {
    const cells = splitCells(line)
    const row = {}
    header.forEach((key, index) => {
      if (key) row[key] = (cells[index] || '').trim()
    })
    return row
  })

  return {
    rows: rows.filter((row) => Object.values(row).some((value) => value !== '')),
    note: ''
  }
}

function resetResult() {
  result.value = null
  error.value = null
}

function switchType(next) {
  if (type.value === next) return
  type.value = next
  text.value = ''
  parsedRows.value = []
  parseNote.value = ''
  resetResult()
}

function fillSample() {
  text.value = samples[type.value]
  parsedRows.value = []
  parseNote.value = ''
  resetResult()
  pushToast('示例数据已填入，可直接点击「解析预览」', 'info')
}

function handleParse() {
  if (parsing.value) return
  const currentType = type.value
  parsing.value = true
  parseNote.value = ''
  resetResult()
  // 局部 loading：让界面先渲染 loading 状态，再做纯前端解析
  setTimeout(() => {
    if (type.value !== currentType) {
      parsing.value = false
      return
    }
    const parsed = parseCsv(text.value)
    parsedRows.value = parsed.rows
    parseNote.value = parsed.note
    parsing.value = false
    if (parsed.rows.length > 0) {
      pushToast(`解析完成，共 ${parsed.rows.length} 行数据`, 'success')
    } else if (parsed.note) {
      pushToast(parsed.note, 'warn')
    }
  }, 260)
}

async function handleImport() {
  if (importing.value || !parsedRows.value.length) return
  importing.value = true
  resetResult()
  try {
    const res = await adminApi.importData({ type: type.value, rows: parsedRows.value, strict: strict.value })
    result.value = { ...res.data, message: res.message }
    parsedRows.value = []
    pushToast(res.message, 'success', res.code)
  } catch (e) {
    error.value = {
      code: e.code,
      message: e.message,
      failures: Array.isArray(e.data?.failures) ? e.data.failures : [],
      skipped: e.data?.skipped ?? 0,
      strict: e.data?.strict
    }
  } finally {
    importing.value = false
  }
}

async function copyPassword(password) {
  try {
    await navigator.clipboard.writeText(password)
    pushToast('初始密码已复制到剪贴板', 'success')
  } catch (e) {
    pushToast('复制失败，请手动选中密码后复制', 'warn')
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>表格导入与账号生成</h1>
        <p class="page-head__sub">
          学生与导师账号由管理员通过 CSV 表格统一导入创建，系统自动生成初始密码；密码只在本页导入结果中展示一次，数据库中仅保存密码哈希。
        </p>
      </div>
    </div>

    <section class="section">
      <div class="card">
        <div class="card__head">
          <h2>粘贴 CSV 表格</h2>
          <div class="tabs">
            <button
              v-for="item in [
                { value: 'student', label: '学生' },
                { value: 'teacher', label: '导师' }
              ]"
              :key="item.value"
              type="button"
              class="tab"
              :class="{ 'is-active': type === item.value }"
              @click="switchType(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <div class="card__body">
          <div class="stack">
            <div class="field">
              <label>
                CSV 文本
                <span class="field__hint">第一行为表头：{{ template.join(',') }}</span>
              </label>
              <textarea v-model="text" class="textarea" :placeholder="placeholder" spellcheck="false"></textarea>
            </div>

            <div class="btn-row">
              <button class="btn btn--ghost btn--sm" type="button" @click="fillSample">填入示例</button>
              <button class="btn btn--sm" type="button" :disabled="parsing" @click="handleParse">
                {{ parsing ? '解析中…' : '解析预览' }}
              </button>
              <span class="hint">支持英文逗号、中文逗号或制表符分隔，空行会自动跳过</span>
            </div>

            <p v-if="parseNote" class="form-error">{{ parseNote }}</p>

            <label class="checkline">
              <input v-model="strict" type="checkbox" />
              <span>
                <strong>严格模式（任一行失败则整体回滚）</strong>
                <small>勾选后只要有一行校验失败，本次导入不会写入任何账号。</small>
              </span>
            </label>

            <div class="btn-row">
              <button
                class="btn btn--lg"
                type="button"
                :disabled="importing || parsing || !parsedRows.length"
                @click="handleImport"
              >
                {{ importing ? '导入中…' : '开始导入' }}
              </button>
              <span class="hint">当前待导入 {{ parsedRows.length }} 行 · {{ type === 'student' ? '学生账号' : '导师账号' }}</span>
            </div>

            <div class="hint--box">
              导入成功后，学生可使用「学号 + 初始密码」登录学生端，导师可使用「工号 + 初始密码」登录导师端，初始密码由系统随机生成。
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="section">
      <div v-if="importing" class="state">
        <span class="spinner"></span>
        <strong>正在导入，请稍候…</strong>
        <p>服务端正在校验账号唯一性并生成初始密码，请勿重复提交。</p>
      </div>

      <div v-else-if="result || error" class="stack">
        <div v-if="result" class="result-banner result-banner--success">
          <h2>导入完成</h2>
          <p>{{ result.message }}</p>
          <div class="result-banner__meta">
            <div>
              <span>成功生成</span>
              <strong>{{ result.imported }} 条</strong>
            </div>
            <div>
              <span>跳过</span>
              <strong>{{ result.skipped }} 条</strong>
            </div>
            <div>
              <span>导入模式</span>
              <strong>{{ result.strict ? '严格' : '宽松' }}</strong>
            </div>
          </div>
        </div>

        <div v-if="error" class="hint hint--danger">
          <strong>{{ error.message }}</strong>
          <p>{{ errorHint }}</p>
          <span class="state__code">状态码 {{ error.code }}</span>
        </div>

        <div v-if="activeFailures.length" class="card">
          <div class="card__head">
            <h2>失败明细</h2>
            <span class="badge badge--rejected">{{ activeFailures.length }} 行失败</span>
          </div>
          <div class="table-wrap">
            <table class="table table--compact">
              <thead>
                <tr>
                  <th>行号</th>
                  <th>账号</th>
                  <th>姓名</th>
                  <th>原因</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(failure, index) in activeFailures" :key="index">
                  <td class="mono">{{ failure.line }}</td>
                  <td>{{ failure.no || '—' }}</td>
                  <td>{{ failure.name || '—' }}</td>
                  <td>{{ (failure.reasons || []).join('；') || '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="result && accounts.length" class="card">
          <div class="card__head">
            <h2>生成账号（初始密码仅显示一次）</h2>
            <span class="badge badge--admitted">{{ accounts.length }} 个账号</span>
          </div>
          <div class="card__body">
            <div class="hint--box hint--warn">
              密码只在此处展示一次，请立即复制并通过安全渠道通知本人；数据库中仅保存密码哈希，刷新或离开本页后无法再次查看。
            </div>
          </div>
          <div class="table-wrap">
            <table class="table table--compact">
              <thead>
                <tr>
                  <th>{{ accountNoLabel }}</th>
                  <th>姓名</th>
                  <th>初始密码</th>
                  <th v-if="type === 'teacher'">实训室</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="account in accounts" :key="account.no">
                  <td class="mono">{{ account.no }}</td>
                  <td><strong>{{ account.name }}</strong></td>
                  <td><span class="copy-pwd">{{ account.password }}</span></td>
                  <td v-if="type === 'teacher'">{{ account.roomName || '—' }}</td>
                  <td>
                    <button class="btn btn--ghost btn--sm" type="button" @click="copyPassword(account.password)">复制</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <FeedbackState
        v-else
        :loading="parsing"
        loading-text="正在解析 CSV…"
        :empty="!parsedRows.length"
        empty-text="请先在上方粘贴 CSV 内容并点击「解析预览」，解析结果会在这里展示。"
      >
        <div class="card">
          <div class="card__head">
            <h2>解析预览</h2>
            <span class="badge badge--role">共 {{ parsedRows.length }} 行</span>
          </div>
          <div class="table-wrap">
            <table class="table table--compact">
              <thead>
                <tr>
                  <th>行号</th>
                  <th v-for="column in template" :key="column">{{ column }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in parsedRows" :key="index">
                  <td class="mono">{{ index + 2 }}</td>
                  <td v-for="column in template" :key="column">{{ row[column] || '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </FeedbackState>
    </section>
  </div>
</template>
