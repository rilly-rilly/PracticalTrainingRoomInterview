import { loadDb, saveDb, resetDb, nextId, nowMs, pushLog } from './db'
import { hashPassword } from './seed'

const HOUR = 3600 * 1000
const DAY = 24 * HOUR

const STATUS = {
  PENDING: '待面试',
  SCORED: '面试完成',
  ADMITTED: '拟录取',
  WAITLIST: '递补',
  CONFIRMED: '已确认',
  GIVEN_UP: '已放弃',
  ENROLLED: '已录取',
  REJECTED: '未录取'
}

const OCCUPIED = [STATUS.ADMITTED, STATUS.CONFIRMED, STATUS.ENROLLED]

class ApiError extends Error {
  constructor(code, message, data = null) {
    super(message)
    this.code = code
    this.data = data
  }
}

const ok = (data = null, message = '操作成功') => ({ message, data })
const fail = (code, message, data = null) => ({ code, message, data })

function getHeader(config, name) {
  const h = config.headers
  if (!h) return ''
  if (typeof h.get === 'function') return h.get(name) || ''
  return h[name] || h[name.toLowerCase()] || ''
}

function parseBody(config) {
  const raw = config.data
  if (raw == null || raw === '') return {}
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch {
      return {}
    }
  }
  return raw
}

function resolveAuth(db, config) {
  const header = getHeader(config, 'Authorization')
  const token = header.replace(/^Bearer\s+/i, '').trim()
  if (!token) return null
  const session = db.sessions.find((s) => s.token === token)
  if (!session) return null
  let user = null
  if (session.role === 'admin') user = db.admin
  if (session.role === 'teacher') user = db.teachers.find((t) => t.id === session.userId)
  if (session.role === 'student') user = db.students.find((s) => s.id === session.userId)
  if (!user) return null
  return { role: session.role, user, token }
}

function profileOf(role, user, db) {
  if (role === 'admin') {
    return { id: user.id, role, name: user.name, username: user.username }
  }
  if (role === 'teacher') {
    const room = db.rooms.find((r) => r.id === user.roomId)
    return { id: user.id, role, name: user.name, no: user.teacherNo, title: user.title, roomId: user.roomId, roomName: room?.name || '' }
  }
  return { id: user.id, role, name: user.name, no: user.studentNo, major: user.major, grade: user.grade }
}

function windowOf(db, key) {
  const s = db.schedules.find((x) => x.key === key)
  if (!s) return null
  const now = nowMs()
  return { key, step: s.step, title: s.title, startAt: s.startAt, endAt: s.endAt, isOpen: now >= s.startAt && now <= s.endAt }
}

function roomUsed(db, roomId, excludeIntentionId = null) {
  return db.intentions.filter(
    (i) => i.roomId === roomId && OCCUPIED.includes(i.status) && i.id !== excludeIntentionId
  ).length
}

function roomOf(db, roomId) {
  return db.rooms.find((r) => r.id === roomId) || null
}

function decorateIntention(db, it) {
  const student = db.students.find((s) => s.id === it.studentId)
  const room = roomOf(db, it.roomId)
  const iv = db.interviews.find((x) => x.intentionId === it.id)
  return {
    id: it.id,
    studentId: it.studentId,
    studentNo: student?.studentNo || '',
    studentName: student?.name || '',
    major: student?.major || '',
    grade: student?.grade || '',
    teacherId: it.teacherId,
    teacherNo: it.teacherNoSnap,
    teacherName: it.teacherNameSnap,
    roomId: it.roomId,
    roomName: it.roomNameSnap,
    roomQuota: room?.quota ?? 0,
    roomUsed: roomUsed(db, it.roomId),
    priority: it.priority,
    status: it.status,
    intro: it.intro,
    acceptInnerAdjust: it.acceptInnerAdjust,
    acceptGlobalAdjust: it.acceptGlobalAdjust,
    submittedAt: it.submittedAt,
    round: it.round,
    rank: it.rank,
    interview: iv
      ? {
          attitude: iv.attitude,
          logic: iv.logic,
          coding: iv.coding,
          commitment: iv.commitment,
          total: iv.total,
          comment: iv.comment,
          createdAt: iv.createdAt
        }
      : null
  }
}

function myIntentions(db, studentId) {
  return db.intentions
    .filter((i) => i.studentId === studentId)
    .sort((a, b) => (a.round - b.round) || (a.priority - b.priority))
}

function allRejected(db, studentId) {
  const list = myIntentions(db, studentId)
  return list.length > 0 && list.every((i) => i.status === STATUS.REJECTED)
}

function findFreeTeacherInRoom(db, roomId, excludeTeacherIds = []) {
  const quota = roomOf(db, roomId)?.quota ?? 0
  if (quota <= 0) return null
  if (roomUsed(db, roomId) >= quota) return null
  return db.teachers.find((t) => t.roomId === roomId && !excludeTeacherIds.includes(t.id)) || null
}

function autoConfirmIfSingle(db, studentId) {
  const admits = db.intentions.filter((i) => i.studentId === studentId && i.status === STATUS.ADMITTED)
  if (admits.length === 1) {
    const target = admits[0]
    target.status = STATUS.CONFIRMED
    db.confirmations.push({
      id: nextId(db, 'cf'),
      studentId,
      intentionId: target.id,
      mode: 'auto',
      confirmedAt: nowMs()
    })
    return true
  }
  return false
}

function createAdjustedIntention(db, student, source, teacher, type) {
  const room = roomOf(db, teacher.roomId)
  const { id } = { id: nextId(db, 'int') }
  const record = {
    id,
    studentId: student.id,
    teacherId: teacher.id,
    teacherNameSnap: teacher.name,
    teacherNoSnap: teacher.teacherNo,
    roomId: room.id,
    roomNameSnap: room.name,
    priority: 0,
    status: STATUS.ADMITTED,
    intro: source?.intro || '',
    acceptInnerAdjust: source?.acceptInnerAdjust ?? true,
    acceptGlobalAdjust: source?.acceptGlobalAdjust ?? true,
    submittedAt: nowMs(),
    round: 2,
    rank: 0
  }
  db.intentions.push(record)
  return record
}

/* ---------------- handlers ---------------- */

function login({ db, body }) {
  const username = String(body.username || '').trim()
  const password = String(body.password || '')
  if (!username || !password) throw new ApiError(400, '请输入账号和密码')

  let role = null
  let user = null
  if (username === 'admin') {
    user = db.admin
    role = 'admin'
  } else if (/^T/i.test(username)) {
    user = db.teachers.find((t) => t.teacherNo.toUpperCase() === username.toUpperCase())
    role = 'teacher'
  } else {
    user = db.students.find((s) => s.studentNo.toUpperCase() === username.toUpperCase())
    role = 'student'
  }

  if (!user || user.pwdHash !== hashPassword(password)) {
    throw new ApiError(401, '账号或密码错误，请使用管理员分配的初始账号登录')
  }

  const token = `tok-${role}-${user.id}-${Math.random().toString(36).slice(2, 10)}`
  db.sessions.push({ token, role, userId: user.id, createdAt: nowMs() })
  return ok({ token, role, profile: profileOf(role, user, db) }, '登录成功')
}

function logout({ db, auth }) {
  db.sessions = db.sessions.filter((s) => s.token !== auth.token)
  return ok(null, '已退出登录，旧登录态已失效')
}

function me({ db, auth }) {
  return ok({ role: auth.role, profile: profileOf(auth.role, auth.user, db) })
}

function publicHome({ db }) {
  const now = nowMs()
  const steps = [...db.schedules].sort((a, b) => a.step - b.step).map((s) => ({
    ...s,
    state: now < s.startAt ? '未开始' : now > s.endAt ? '已结束' : '进行中'
  }))
  return ok({
    steps,
    notices: [...db.notices].sort((a, b) => b.publishedAt - a.publishedAt),
    rooms: db.rooms.map((r) => ({
      id: r.id,
      code: r.code,
      name: r.name,
      direction: r.direction,
      quota: r.quota,
      remaining: Math.max(0, r.quota - roomUsed(db, r.id)),
      tags: r.tags
    })),
    intentWindow: windowOf(db, 'intent')
  })
}

function listRooms({ db }) {
  return ok(
    db.rooms.map((r) => {
      const used = roomUsed(db, r.id)
      return {
        ...r,
        remaining: Math.max(0, r.quota - used),
        teacherCount: db.teachers.filter((t) => t.roomId === r.id).length
      }
    })
  )
}

function roomDetail({ db, params }) {
  const room = roomOf(db, params.id)
  if (!room) throw new ApiError(404, '实训室不存在')
  return ok({
    ...room,
    remaining: Math.max(0, room.quota - roomUsed(db, room.id)),
    teachers: db.teachers.filter((t) => t.roomId === room.id).map((t) => ({ id: t.id, name: t.name, teacherNo: t.teacherNo, title: t.title }))
  })
}

function studentIntentions({ db, auth }) {
  return ok(myIntentions(db, auth.user.id).map((i) => decorateIntention(db, i)))
}

function submitIntentions({ db, auth, body }) {
  const student = auth.user
  const w = windowOf(db, 'intent')
  if (!w || !w.isOpen) {
    throw new ApiError(410, '意向填报时间窗口已关闭，当前不可提交（410）', { window: w })
  }

  const choices = Array.isArray(body.choices) ? body.choices : []
  const intro = String(body.intro || '').trim()

  if (choices.length < 1) throw new ApiError(422, '请至少选择 1 个意向导师')
  if (choices.length > 3) throw new ApiError(422, '最多填报 3 个平行意向，当前提交了 ' + choices.length + ' 个')
  if (new Set(choices).size !== choices.length) throw new ApiError(422, '同一导师不能重复填报')
  if (intro.length < 20) throw new ApiError(422, '个人简介不能为空且不少于 20 字')

  const existing = db.intentions.filter((i) => i.studentId === student.id && i.round === 1)
  if (existing.length > 0) throw new ApiError(422, '本轮意向已提交，不可重复填报')

  const teacherList = []
  for (const teacherId of choices) {
    const teacher = db.teachers.find((t) => t.id === teacherId)
    if (!teacher) throw new ApiError(400, '所选导师不存在：' + teacherId)
    teacherList.push(teacher)
  }

  const now = nowMs()
  teacherList.forEach((teacher, index) => {
    const room = roomOf(db, teacher.roomId)
    db.intentions.push({
      id: nextId(db, 'int'),
      studentId: student.id,
      teacherId: teacher.id,
      teacherNameSnap: teacher.name,
      teacherNoSnap: teacher.teacherNo,
      roomId: room.id,
      roomNameSnap: room.name,
      priority: index + 1,
      status: STATUS.PENDING,
      intro,
      acceptInnerAdjust: Boolean(body.acceptInnerAdjust),
      acceptGlobalAdjust: Boolean(body.acceptGlobalAdjust),
      submittedAt: now,
      round: 1,
      rank: 0
    })
  })

  pushLog(db, 'student', student.name, '提交意向', `${choices.length} 个平行意向 / 内部调剂 ${body.acceptInnerAdjust ? '是' : '否'} / 全院调剂 ${body.acceptGlobalAdjust ? '是' : '否'}`)
  return ok(myIntentions(db, student.id).map((i) => decorateIntention(db, i)), '意向提交成功')
}

function studentStatus({ db, auth }) {
  const studentId = auth.user.id
  const list = myIntentions(db, studentId).map((i) => decorateIntention(db, i))
  const admits = list.filter((i) => i.status === STATUS.ADMITTED)
  const confirmed = list.filter((i) => i.status === STATUS.CONFIRMED)
  const waitlist = list.find((i) => i.status === STATUS.ADMITTED || i.status === STATUS.WAITLIST)
  return ok({
    intentions: list,
    summary: {
      total: list.length,
      admits: admits.length,
      needChoose: admits.length >= 2,
      autoConfirmed: db.confirmations.some((c) => c.studentId === studentId && c.mode === 'auto'),
      confirmedIntentionId: confirmed[0]?.id || null,
      waitlistRank: list.filter((i) => i.status === STATUS.WAITLIST).map((i) => i.rank),
      hasWaitlist: list.some((i) => i.status === STATUS.WAITLIST),
      acceptInnerAdjust: list.some((i) => i.acceptInnerAdjust),
      acceptGlobalAdjust: list.some((i) => i.acceptGlobalAdjust)
    },
    confirmWindow: windowOf(db, 'confirm'),
    confirmations: db.confirmations.filter((c) => c.studentId === studentId),
    adjustments: db.adjustments.filter((a) => a.studentId === studentId)
  })
}

function studentConfirm({ db, auth, body }) {
  const studentId = auth.user.id
  const intention = db.intentions.find((i) => i.id === body.intentionId)
  if (!intention) throw new ApiError(404, '意向记录不存在')
  if (intention.studentId !== studentId) throw new ApiError(403, '无权操作他人的意向记录')
  if (intention.status === STATUS.CONFIRMED) throw new ApiError(422, '该意向已确认，无需重复操作')
  if (intention.status === STATUS.GIVEN_UP) throw new ApiError(422, '该意向已放弃，不能再次确认')
  if (intention.status === STATUS.ENROLLED) throw new ApiError(422, '该意向已正式录取，不能修改')
  if (intention.status !== STATUS.ADMITTED) throw new ApiError(422, '当前状态不允许确认：' + intention.status)

  const admits = db.intentions.filter((i) => i.studentId === studentId && i.status === STATUS.ADMITTED)
  if (admits.length >= 2) {
    const w = windowOf(db, 'confirm')
    if (!w || !w.isOpen) throw new ApiError(410, '拟录取确认时间窗口已关闭，逾期未确认视为放弃（410）')
  }

  intention.status = STATUS.CONFIRMED
  db.confirmations.push({
    id: nextId(db, 'cf'),
    studentId,
    intentionId: intention.id,
    mode: 'manual',
    confirmedAt: nowMs()
  })

  for (const other of admits) {
    if (other.id === intention.id) continue
    other.status = STATUS.GIVEN_UP
    db.confirmations.push({
      id: nextId(db, 'cf'),
      studentId,
      intentionId: other.id,
      mode: 'giveup',
      confirmedAt: nowMs()
    })
  }

  pushLog(db, 'student', auth.user.name, '确认拟录取', `${intention.roomNameSnap} / ${intention.teacherNameSnap}`)
  return ok({ confirmedIntentionId: intention.id, givenUp: admits.filter((i) => i.id !== intention.id).map((i) => i.id) }, '确认成功，已锁定唯一去向')
}

function studentAdjustments({ db, auth }) {
  const list = db.adjustments
    .filter((a) => a.studentId === auth.user.id)
    .sort((a, b) => b.createdAt - a.createdAt)
  return ok({
    records: list,
    summary: {
      totalIntentions: myIntentions(db, auth.user.id).length,
      rejected: allRejected(db, auth.user.id),
      acceptInnerAdjust: myIntentions(db, auth.user.id).some((i) => i.acceptInnerAdjust),
      acceptGlobalAdjust: myIntentions(db, auth.user.id).some((i) => i.acceptGlobalAdjust)
    }
  })
}

function studentFinal({ db, auth }) {
  const studentId = auth.user.id
  const list = myIntentions(db, studentId).map((i) => decorateIntention(db, i))
  const admitted = list.find((i) => i.status === STATUS.ENROLLED)
  const confirmed = list.find((i) => i.status === STATUS.CONFIRMED)
  const publicity = db.publicities.find((p) => p.studentId === studentId && p.status === 'public') || null

  let stage = '未填报'
  let finalStatus = '未填报'
  if (admitted) {
    stage = '已录取'
    finalStatus = STATUS.ENROLLED
  } else if (publicity) {
    stage = '公示中'
    finalStatus = '公示中'
  } else if (confirmed) {
    stage = '待公示'
    finalStatus = STATUS.CONFIRMED
  } else if (list.some((i) => i.status === STATUS.WAITLIST)) {
    stage = '递补中'
    finalStatus = STATUS.WAITLIST
  } else if (list.some((i) => [STATUS.PENDING, STATUS.SCORED, STATUS.ADMITTED].includes(i.status))) {
    stage = '考核进行中'
    finalStatus = '考核中'
  } else if (list.length && list.every((i) => [STATUS.REJECTED, STATUS.GIVEN_UP].includes(i.status))) {
    stage = '等待调剂'
    finalStatus = STATUS.REJECTED
  }

  const target = admitted || confirmed || null
  return ok({
    stage,
    finalStatus,
    intention: target,
    publicity: publicity
      ? {
          startAt: publicity.startAt,
          endAt: publicity.endAt,
          roomName: publicity.roomNameSnap,
          teacherName: publicity.teacherNameSnap
        }
      : null,
    intentions: list,
    adjustments: db.adjustments.filter((a) => a.studentId === studentId)
  })
}

function teacherApplicants({ db, auth, query }) {
  const teacherId = auth.user.id
  let list = db.intentions.filter((i) => i.teacherId === teacherId)
  if (query.status) list = list.filter((i) => i.status === query.status)
  list = list
    .map((i) => decorateIntention(db, i))
    .sort((a, b) => b.interview?.total - a.interview?.total || a.studentNo.localeCompare(b.studentNo))
  const room = roomOf(db, auth.user.roomId)
  return ok({
    applications: list,
    teacher: profileOf('teacher', auth.user, db),
    room: room
      ? { ...room, used: roomUsed(db, room.id), remaining: Math.max(0, room.quota - roomUsed(db, room.id)) }
      : null,
    statusCounts: Object.values(STATUS).reduce((acc, s) => {
      acc[s] = db.intentions.filter((i) => i.teacherId === teacherId && i.status === s).length
      return acc
    }, {})
  })
}

function teacherApplicantDetail({ db, auth, params }) {
  const it = db.intentions.find((i) => i.id === params.id)
  if (!it) throw new ApiError(404, '意向记录不存在')
  if (it.teacherId !== auth.user.id) throw new ApiError(403, '无权查看未报考本人的学生')
  return ok(decorateIntention(db, it))
}

function submitInterview({ db, auth, body }) {
  const it = db.intentions.find((i) => i.id === body.intentionId)
  if (!it) throw new ApiError(404, '意向记录不存在')
  if (it.teacherId !== auth.user.id) throw new ApiError(403, '只能给报考本人的学生打分')
  if (it.status !== STATUS.PENDING) throw new ApiError(422, `只有「待面试」状态可以录入面试考核，当前为「${it.status}」`)

  const fields = ['attitude', 'logic', 'coding', 'commitment']
  const scores = {}
  for (const f of fields) {
    const v = Number(body[f])
    if (!Number.isInteger(v) || v < 1 || v > 10) throw new ApiError(422, '评分必须为 1~10 的整数')
    scores[f] = v
  }
  const total = fields.reduce((sum, f) => sum + scores[f], 0)

  db.interviews.push({
    id: nextId(db, 'iv'),
    intentionId: it.id,
    studentId: it.studentId,
    teacherId: it.teacherId,
    ...scores,
    total,
    comment: String(body.comment || '').trim(),
    createdAt: nowMs()
  })
  it.status = STATUS.SCORED
  pushLog(db, 'teacher', auth.user.name, '录入面试评分', `${decorateIntention(db, it).studentName} / 总分 ${total}`)
  return ok({ total }, '面试评分已保存')
}

function teacherDecision({ db, auth, body }) {
  const it = db.intentions.find((i) => i.id === body.intentionId)
  if (!it) throw new ApiError(404, '意向记录不存在')
  if (it.teacherId !== auth.user.id) throw new ApiError(403, '只能决定报考本人的学生')
  if (it.status !== STATUS.SCORED) {
    throw new ApiError(422, `只有「面试完成」状态可以决定是否录取，当前为「${it.status}」`)
  }

  const decision = body.decision
  if (!['admit', 'waitlist', 'reject'].includes(decision)) throw new ApiError(400, '录取决定参数不合法')

  const room = roomOf(db, it.roomId)
  const student = db.students.find((s) => s.id === it.studentId)

  if (decision === 'admit') {
    const used = roomUsed(db, it.roomId, it.id)
    if (!room) throw new ApiError(400, '实训室不存在')
    if (used >= room.quota) {
      throw new ApiError(409, `名额已满：${room.name} 招收上限 ${room.quota} 人，当前已占用 ${used} 人（409）`, {
        roomId: room.id,
        quota: room.quota,
        used
      })
    }
    it.status = STATUS.ADMITTED
    const auto = autoConfirmIfSingle(db, it.studentId)
    pushLog(
      db,
      'teacher',
      auth.user.name,
      '作出录取决定',
      `${student?.name || ''} / ${room.name} / 拟录取${auto ? '（自动确认）' : '（待学生自主选择）'}`
    )
    return ok({ status: it.status, autoConfirmed: auto }, auto ? '已标记拟录取，学生仅此一个拟录取意向，系统已自动确认' : '已标记拟录取，学生需在规定时间内自主选择唯一去向')
  }

  if (decision === 'waitlist') {
    const sameRoomWait = db.intentions.filter((i) => i.roomId === it.roomId && i.status === STATUS.WAITLIST)
    it.status = STATUS.WAITLIST
    it.rank = Math.max(0, ...sameRoomWait.map((i) => i.rank || 0)) + 1
    pushLog(db, 'teacher', auth.user.name, '作出录取决定', `${student?.name || ''} / ${room?.name || ''} / 递补第 ${it.rank} 顺位`)
    return ok({ status: it.status, rank: it.rank }, `已加入递补名单，顺位 ${it.rank}`)
  }

  it.status = STATUS.REJECTED
  pushLog(db, 'teacher', auth.user.name, '作出录取决定', `${student?.name || ''} / ${room?.name || ''} / 未录取`)
  return ok({ status: it.status }, '已标记为未录取，学生将进入调剂流程')
}

function adminImport({ db, auth, body }) {
  const type = body.type
  if (!['student', 'teacher'].includes(type)) throw new ApiError(400, '导入类型不合法')
  const rows = Array.isArray(body.rows) ? body.rows : []
  if (rows.length === 0) throw new ApiError(422, '请提供待导入的数据行')
  const strict = Boolean(body.strict)

  const failures = []
  const validRows = []
  const seenNo = new Set()
  const existingNo = new Set(
    type === 'student' ? db.students.map((s) => s.studentNo.toUpperCase()) : db.teachers.map((t) => t.teacherNo.toUpperCase())
  )
  const roomCodes = new Set(db.rooms.map((r) => r.code))

  rows.forEach((row, index) => {
    const line = index + 2
    const name = String(row['姓名'] || '').trim()
    const no = String(type === 'student' ? row['学号'] || '' : row['工号'] || '').trim()
    const major = String(row['专业'] || '').trim()
    const grade = String(row['年级'] || '').trim()
    const title = String(row['职称'] || '').trim()
    const roomCode = String(row['实训室编号'] || '').trim()

    const reasons = []
    if (!name) reasons.push('姓名缺失')
    if (!no) reasons.push(type === 'student' ? '学号缺失' : '工号缺失')
    if (type === 'student') {
      if (!major) reasons.push('专业缺失')
      if (!grade) reasons.push('年级缺失')
    } else {
      if (!roomCode) reasons.push('实训室编号缺失')
      else if (!roomCodes.has(roomCode)) reasons.push('实训室编号不存在：' + roomCode)
    }
    if (no && seenNo.has(no.toUpperCase())) reasons.push('文件内账号重复：' + no)
    if (no && existingNo.has(no.toUpperCase())) reasons.push('账号已存在：' + no)

    if (reasons.length > 0) {
      failures.push({ line, no, name, reasons })
      if (no) seenNo.add(no.toUpperCase())
      return
    }
    seenNo.add(no.toUpperCase())
    validRows.push({ name, no, major, grade, title, roomCode })
  })

  if (strict && failures.length > 0) {
    throw new ApiError(422, `严格模式：共 ${failures.length} 行校验失败，本次导入整体回滚，未写入任何数据`, {
      imported: 0,
      skipped: failures.length,
      failures,
      accounts: [],
      strict
    })
  }

  if (validRows.length === 0) {
    throw new ApiError(422, '没有可导入的有效数据，请检查必填字段与账号唯一性', {
      imported: 0,
      skipped: failures.length,
      failures,
      accounts: [],
      strict
    })
  }

  const accounts = []
  for (const row of validRows) {
    const random = Math.random().toString(36).slice(2, 8)
    if (type === 'student') {
      const password = `Stu@${random}`
      db.students.push({
        id: nextId(db, 'stu'),
        studentNo: row.no,
        name: row.name,
        major: row.major,
        grade: row.grade,
        pwdHash: hashPassword(password)
      })
      accounts.push({ no: row.no, name: row.name, password })
    } else {
      const password = `Teach@${random}`
      const room = db.rooms.find((r) => r.code === row.roomCode)
      db.teachers.push({
        id: nextId(db, 'tch'),
        teacherNo: row.no,
        name: row.name,
        title: row.title || '讲师',
        roomId: room.id,
        pwdHash: hashPassword(password)
      })
      accounts.push({ no: row.no, name: row.name, password, roomName: room.name })
    }
  }

  const batch = {
    id: nextId(db, 'imp'),
    type,
    operator: auth.user.name,
    total: rows.length,
    imported: validRows.length,
    skipped: failures.length,
    strict,
    createdAt: nowMs(),
    accounts
  }
  db.imports.unshift(batch)
  pushLog(db, 'admin', auth.user.name, '导入账号', `${type === 'student' ? '学生' : '导师'}表格：成功 ${validRows.length} 条，跳过 ${failures.length} 条，未生成密码明文入库`)

  return ok(
    {
      imported: validRows.length,
      skipped: failures.length,
      failures,
      accounts,
      strict,
      batchId: batch.id
    },
    `导入完成：成功 ${validRows.length} 条，跳过 ${failures.length} 条`
  )
}

function adminLogs({ db }) {
  return ok([...db.logs].sort((a, b) => b.createdAt - a.createdAt).slice(0, 40))
}

function adminSchedules({ db }) {
  return ok([...db.schedules].sort((a, b) => a.step - b.step))
}

function updateSchedule({ db, auth, params, body }) {
  const s = db.schedules.find((x) => x.id === params.id)
  if (!s) throw new ApiError(404, '日程不存在')
  const startAt = Number(body.startAt)
  const endAt = Number(body.endAt)
  if (!Number.isFinite(startAt) || !Number.isFinite(endAt)) throw new ApiError(400, '时间格式不合法')
  if (startAt >= endAt) throw new ApiError(422, '开始时间必须早于结束时间')
  if (body.title) s.title = String(body.title).trim()
  if (body.description !== undefined) s.description = String(body.description)
  s.startAt = startAt
  s.endAt = endAt
  pushLog(db, 'admin', auth.user.name, '调整日程', `${s.title}：${new Date(startAt).toLocaleString()} ~ ${new Date(endAt).toLocaleString()}`)
  return ok(s, '日程已更新')
}

function adminNotices({ db }) {
  return ok([...db.notices].sort((a, b) => b.publishedAt - a.publishedAt))
}

function createNotice({ db, auth, body }) {
  const title = String(body.title || '').trim()
  const content = String(body.content || '').trim()
  if (!title) throw new ApiError(422, '公告标题不能为空')
  if (!content) throw new ApiError(422, '公告内容不能为空')
  const notice = { id: nextId(db, 'notice'), title, content, level: body.level || '普通', publishedAt: nowMs() }
  db.notices.unshift(notice)
  pushLog(db, 'admin', auth.user.name, '发布公告', title)
  return ok(notice, '公告已发布')
}

function deleteNotice({ db, auth, params }) {
  const idx = db.notices.findIndex((n) => n.id === params.id)
  if (idx < 0) throw new ApiError(404, '公告不存在')
  const [removed] = db.notices.splice(idx, 1)
  pushLog(db, 'admin', auth.user.name, '删除公告', removed.title)
  return ok(null, '公告已删除')
}

function adminAdmissions({ db }) {
  const rooms = db.rooms.map((r) => {
    const intentions = db.intentions.filter((i) => i.roomId === r.id).map((i) => decorateIntention(db, i))
    return {
      id: r.id,
      name: r.name,
      code: r.code,
      quota: r.quota,
      used: roomUsed(db, r.id),
      remaining: Math.max(0, r.quota - roomUsed(db, r.id)),
      admitted: intentions.filter((i) => i.status === STATUS.ADMITTED).length,
      confirmed: intentions.filter((i) => i.status === STATUS.CONFIRMED).length,
      waitlist: intentions.filter((i) => i.status === STATUS.WAITLIST).length,
      enrolled: intentions.filter((i) => i.status === STATUS.ENROLLED).length,
      intentions
    }
  })
  const teachers = db.teachers.map((t) => {
    const list = db.intentions.filter((i) => i.teacherId === t.id)
    return {
      id: t.id,
      teacherNo: t.teacherNo,
      name: t.name,
      roomId: t.roomId,
      roomName: roomOf(db, t.roomId)?.name || '',
      applied: list.length,
      admitted: list.filter((i) => [STATUS.ADMITTED, STATUS.CONFIRMED, STATUS.ENROLLED].includes(i.status)).length,
      waitlist: list.filter((i) => i.status === STATUS.WAITLIST).length
    }
  })
  const all = db.intentions.map((i) => decorateIntention(db, i))
  return ok({
    rooms,
    teachers,
    intentions: all.sort((a, b) => a.roomId.localeCompare(b.roomId) || a.teacherName.localeCompare(b.teacherName)),
    stats: {
      students: db.students.length,
      teachers: db.teachers.length,
      intentions: db.intentions.length,
      pending: all.filter((i) => i.status === STATUS.PENDING).length,
      scored: all.filter((i) => i.status === STATUS.SCORED).length,
      admitted: all.filter((i) => i.status === STATUS.ADMITTED).length,
      waitlist: all.filter((i) => i.status === STATUS.WAITLIST).length,
      confirmed: all.filter((i) => i.status === STATUS.CONFIRMED).length,
      enrolled: all.filter((i) => i.status === STATUS.ENROLLED).length,
      rejected: all.filter((i) => i.status === STATUS.REJECTED).length
    },
    confirmWindow: windowOf(db, 'confirm'),
    logs: [...db.logs].sort((a, b) => b.createdAt - a.createdAt).slice(0, 30)
  })
}

function updateRoomQuota({ db, auth, params, body }) {
  const room = roomOf(db, params.id)
  if (!room) throw new ApiError(404, '实训室不存在')
  const quota = Number(body.quota)
  if (!Number.isInteger(quota) || quota < 1 || quota > 20) throw new ApiError(422, '名额上限必须是 1~20 的整数')
  const used = roomUsed(db, room.id)
  if (quota < used) throw new ApiError(409, `名额上限不能低于已录取人数（当前已占用 ${used} 人）`)
  room.quota = quota
  pushLog(db, 'admin', auth.user.name, '调整录取名额', `${room.name} 招收上限调整为 ${quota}`)
  return ok({ id: room.id, quota })
}

function expireConfirmations({ db, auth, body }) {
  const force = Boolean(body.force)
  const w = windowOf(db, 'confirm')
  if (!force && w && nowMs() <= w.endAt) {
    throw new ApiError(410, '拟录取确认窗口尚未结束，不能执行逾期处理（可使用演示强制模式）')
  }
  const pending = db.intentions.filter((i) => i.status === STATUS.ADMITTED)
  if (pending.length === 0) throw new ApiError(422, '当前没有待确认的拟录取记录')

  const affectedStudents = new Set()
  const affectedRooms = new Set()
  for (const it of pending) {
    it.status = STATUS.GIVEN_UP
    affectedStudents.add(it.studentId)
    affectedRooms.add(it.roomId)
    db.confirmations.push({
      id: nextId(db, 'cf'),
      studentId: it.studentId,
      intentionId: it.id,
      mode: 'expired',
      confirmedAt: nowMs()
    })
  }

  let promoted = 0
  for (const roomId of affectedRooms) {
    const room = roomOf(db, roomId)
    if (!room) continue
    while (roomUsed(db, roomId) < room.quota) {
      const wait = db.intentions
        .filter((i) => i.roomId === roomId && i.status === STATUS.WAITLIST)
        .sort((a, b) => a.rank - b.rank)[0]
      if (!wait) break
      wait.status = STATUS.ADMITTED
      const auto = autoConfirmIfSingle(db, wait.studentId)
      db.adjustments.push({
        id: nextId(db, 'adj'),
        studentId: wait.studentId,
        round: 2,
        type: 'supplement',
        typeLabel: '分轮补录',
        fromRoomName: '',
        toRoomName: room.name,
        toTeacherName: wait.teacherNameSnap,
        result: auto ? '递补录取并自动确认' : '递补拟录取，待学生确认',
        createdAt: nowMs()
      })
      promoted += 1
    }
  }

  pushLog(db, 'admin', auth.user.name, '执行逾期未确认处理', `放弃 ${pending.length} 条拟录取，递补补位 ${promoted} 人`)
  return ok(
    { expired: pending.length, promoted, students: affectedStudents.size },
    `已处理 ${pending.length} 条逾期未确认记录，递补补位 ${promoted} 人`
  )
}

function adminAdjustments({ db }) {
  const studentRows = db.students.map((s) => {
    const list = myIntentions(db, s.id)
    const first = list[0]
    const admitted = list.some((i) => [STATUS.ADMITTED, STATUS.CONFIRMED, STATUS.ENROLLED].includes(i.status))
    const waitlist = list.some((i) => i.status === STATUS.WAITLIST)
    return {
      studentId: s.id,
      studentNo: s.studentNo,
      name: s.name,
      major: s.major,
      intentionsCount: list.length,
      lastStatus: list.map((i) => i.status).join(' / ') || '未填报',
      admitted,
      waitlist,
      rejectedAll: allRejected(db, s.id),
      acceptInnerAdjust: list.some((i) => i.acceptInnerAdjust),
      acceptGlobalAdjust: list.some((i) => i.acceptGlobalAdjust),
      originalRooms: [...new Set(list.map((i) => i.roomNameSnap))],
      adjusted: db.adjustments.some((a) => a.studentId === s.id)
    }
  })
  return ok({
    students: studentRows,
    records: [...db.adjustments].sort((a, b) => b.createdAt - a.createdAt),
    rooms: db.rooms.map((r) => ({ id: r.id, name: r.name, quota: r.quota, used: roomUsed(db, r.id), remaining: Math.max(0, r.quota - roomUsed(db, r.id)) }))
  })
}

function runAdjustment({ db, auth, body }) {
  const type = body.type
  if (!['inner', 'global', 'interview', 'supplement'].includes(type)) throw new ApiError(400, '调剂类型不合法')

  if (type === 'supplement') {
    let promoted = 0
    for (const room of db.rooms) {
      while (roomUsed(db, room.id) < room.quota) {
        const wait = db.intentions
          .filter((i) => i.roomId === room.id && i.status === STATUS.WAITLIST)
          .sort((a, b) => a.rank - b.rank)[0]
        if (!wait) break
        wait.status = STATUS.ADMITTED
        const auto = autoConfirmIfSingle(db, wait.studentId)
        db.adjustments.push({
          id: nextId(db, 'adj'),
          studentId: wait.studentId,
          round: 2,
          type: 'supplement',
          typeLabel: '分轮补录',
          fromRoomName: '',
          toRoomName: room.name,
          toTeacherName: wait.teacherNameSnap,
          result: auto ? '递补录取并自动确认' : '递补拟录取，待学生确认',
          createdAt: nowMs()
        })
        promoted += 1
      }
    }
    pushLog(db, 'admin', auth.user.name, '执行分轮补录', `递补补位 ${promoted} 人`)
    return ok({ affected: promoted }, `本轮补录完成，递补补位 ${promoted} 人`)
  }

  if (type === 'interview') {
    const targets = db.students.filter((s) => {
      const list = myIntentions(db, s.id)
      if (!allRejected(db, s.id)) return false
      return list.every((i) => !i.acceptInnerAdjust && !i.acceptGlobalAdjust)
    })
    if (targets.length === 0) throw new ApiError(422, '当前没有「不服从调剂」的未录取学生')
    for (const s of targets) {
      if (db.adjustments.some((a) => a.studentId === s.id && a.type === 'interview')) continue
      db.adjustments.push({
        id: nextId(db, 'adj'),
        studentId: s.id,
        round: 2,
        type: 'interview',
        typeLabel: '约谈（第三优先级）',
        fromRoomName: myIntentions(db, s.id)[0]?.roomNameSnap || '',
        toRoomName: '',
        toTeacherName: '',
        result: '待约谈，由学院统一安排',
        createdAt: nowMs()
      })
    }
    pushLog(db, 'admin', auth.user.name, '执行第三优先级约谈', `约谈 ${targets.length} 名不服从调剂学生`)
    return ok({ affected: targets.length }, `已生成 ${targets.length} 条约谈安排`)
  }

  // inner / global
  const candidates = db.students.filter((s) => {
    if (!allRejected(db, s.id)) return false
    if (db.adjustments.some((a) => a.studentId === s.id && a.type === type)) return false
    const list = myIntentions(db, s.id)
    return type === 'inner' ? list.some((i) => i.acceptInnerAdjust) : list.some((i) => i.acceptGlobalAdjust)
  })

  if (candidates.length === 0) {
    throw new ApiError(422, type === 'inner' ? '当前没有可执行实训室内部调剂的未录取学生' : '当前没有可执行全院跨实训室调剂的未录取学生')
  }

  let affected = 0
  const skipped = []
  for (const s of candidates) {
    const list = myIntentions(db, s.id)
    const source = list[0]
    const originalRoomIds = [...new Set(list.map((i) => i.roomId))]
    const excludeTeacherIds = list.map((i) => i.teacherId)
    let targetTeacher = null

    if (type === 'inner') {
      for (const roomId of originalRoomIds) {
        targetTeacher = findFreeTeacherInRoom(db, roomId, excludeTeacherIds)
        if (targetTeacher) break
      }
    } else {
      for (const room of db.rooms) {
        if (originalRoomIds.includes(room.id)) continue
        targetTeacher = findFreeTeacherInRoom(db, room.id, [])
        if (targetTeacher) break
      }
    }

    if (!targetTeacher) {
      skipped.push({ name: s.name, reason: type === 'inner' ? '本人填报的实训室均无名额' : '全院其他实训室均无名额' })
      continue
    }

    const record = createAdjustedIntention(db, s, source, targetTeacher, type)
    const auto = autoConfirmIfSingle(db, s.id)
    const toRoom = roomOf(db, targetTeacher.roomId)
    db.adjustments.push({
      id: nextId(db, 'adj'),
      studentId: s.id,
      round: 2,
      type,
      typeLabel: type === 'inner' ? '实训室内部调剂' : '全院跨实训室调剂',
      fromRoomName: source?.roomNameSnap || '',
      toRoomName: toRoom.name,
      toTeacherName: targetTeacher.name,
      result: auto ? '调剂成功并自动确认' : '调剂拟录取，待学生确认',
      createdAt: nowMs()
    })
    affected += 1
  }

  pushLog(
    db,
    'admin',
    auth.user.name,
    type === 'inner' ? '执行实训室内部调剂' : '执行全院跨实训室调剂',
    `成功 ${affected} 人，未能调剂 ${skipped.length} 人`
  )
  return ok({ affected, skipped }, `调剂完成：成功 ${affected} 人，未能调剂 ${skipped.length} 人`)
}

function adminPublicity({ db }) {
  const eligible = db.students
    .map((s) => {
      const list = myIntentions(db, s.id)
      const target = list.find((i) => i.status === STATUS.CONFIRMED)
      if (!target) return null
      return {
        studentId: s.id,
        studentNo: s.studentNo,
        name: s.name,
        major: s.major,
        roomName: target.roomNameSnap,
        teacherName: target.teacherNameSnap,
        intentionId: target.id,
        status: target.status
      }
    })
    .filter(Boolean)
  return ok({
    eligible,
    records: [...db.publicities].sort((a, b) => b.startAt - a.startAt).map((p) => {
      const student = db.students.find((s) => s.id === p.studentId)
      return { ...p, studentNo: student?.studentNo, studentName: student?.name }
    }),
    window: windowOf(db, 'publicity')
  })
}

function publishPublicity({ db, auth }) {
  const candidates = db.students
    .map((s) => {
      const target = myIntentions(db, s.id).find((i) => i.status === STATUS.CONFIRMED)
      return target ? { student: s, intention: target } : null
    })
    .filter(Boolean)

  if (candidates.length === 0) throw new ApiError(422, '暂无可公示的已确认录取学生')

  let created = 0
  for (const { student, intention } of candidates) {
    if (db.publicities.some((p) => p.intentionId === intention.id && p.status === 'public')) continue
    db.publicities.push({
      id: nextId(db, 'pub'),
      studentId: student.id,
      intentionId: intention.id,
      roomId: intention.roomId,
      teacherId: intention.teacherId,
      roomNameSnap: intention.roomNameSnap,
      teacherNameSnap: intention.teacherNameSnap,
      startAt: nowMs(),
      endAt: nowMs() + DAY,
      status: 'public'
    })
    created += 1
  }
  pushLog(db, 'admin', auth.user.name, '发布结果公示', `公示 ${created} 人，公示期 1 天`)
  return ok({ created }, `已发布公示，共 ${created} 人，公示期 1 天`)
}

function finalizePublicity({ db, auth, body }) {
  const force = Boolean(body.force)
  const targets = db.publicities.filter((p) => p.status === 'public' && (force || nowMs() >= p.endAt))
  if (targets.length === 0) {
    throw new ApiError(422, '没有可结束的公示（公示期未满，可使用演示提前结束）')
  }
  let enrolled = 0
  for (const p of targets) {
    p.status = 'final'
    const intention = db.intentions.find((i) => i.id === p.intentionId)
    if (intention && intention.status === STATUS.CONFIRMED) {
      intention.status = STATUS.ENROLLED
      enrolled += 1
    }
  }
  pushLog(db, 'admin', auth.user.name, '结束公示并正式入组', `正式入组 ${enrolled} 人`)
  return ok({ enrolled }, `公示无异议，${enrolled} 名学生已置为「已录取」`)
}

function demoReset() {
  resetDb()
  return ok(null, '演示数据已重置')
}

function demoAccounts({ db }) {
  return ok({
    admin: { username: db.admin.username, password: 'admin123', name: db.admin.name },
    teacher: { username: 'T1001', password: 'Teach@2026', name: '李伯谦（智能视觉）' },
    students: [
      { username: 'S2026001', password: 'Stu@2026', name: '张一鸣（2 个拟录取，待自主选择）' },
      { username: 'S2026002', password: 'Stu@2026', name: '李思远（1 个拟录取，已自动确认）' },
      { username: 'S2026003', password: 'Stu@2026', name: '王雨桐（递补第 1 顺位）' },
      { username: 'S2026004', password: 'Stu@2026', name: '陈子豪（待面试）' },
      { username: 'S2026005', password: 'Stu@2026', name: '刘嘉琪（3 个面试完成，待导师决定）' },
      { username: 'S2026006', password: 'Stu@2026', name: '周文轩（未录取，服从内部调剂）' },
      { username: 'S2026007', password: 'Stu@2026', name: '吴涵（未录取，仅服从全院调剂）' },
      { username: 'S2026008', password: 'Stu@2026', name: '郑欣然（未填报，可演示填报窗口）' },
      { username: 'S2026009', password: 'Stu@2026', name: '韩雨泽（未录取，不服从调剂）' }
    ]
  })
}

/* ---------------- routing ---------------- */

const routes = [
  { method: 'POST', path: '/api/auth/login', roles: ['public'], handler: login },
  { method: 'POST', path: '/api/auth/logout', roles: ['admin', 'teacher', 'student'], handler: logout },
  { method: 'GET', path: '/api/auth/me', roles: ['admin', 'teacher', 'student'], handler: me },
  { method: 'GET', path: '/api/public/home', roles: ['public'], handler: publicHome },
  { method: 'GET', path: '/api/public/demo-accounts', roles: ['public'], handler: demoAccounts },
  { method: 'POST', path: '/api/public/reset', roles: ['public'], handler: demoReset },
  { method: 'GET', path: '/api/rooms', roles: ['public'], handler: listRooms },
  { method: 'GET', path: '/api/rooms/:id', roles: ['public'], handler: roomDetail },

  { method: 'GET', path: '/api/student/intentions', roles: ['student'], handler: studentIntentions },
  { method: 'POST', path: '/api/student/intentions', roles: ['student'], handler: submitIntentions },
  { method: 'GET', path: '/api/student/status', roles: ['student'], handler: studentStatus },
  { method: 'POST', path: '/api/student/confirm', roles: ['student'], handler: studentConfirm },
  { method: 'GET', path: '/api/student/adjustments', roles: ['student'], handler: studentAdjustments },
  { method: 'GET', path: '/api/student/final', roles: ['student'], handler: studentFinal },

  { method: 'GET', path: '/api/teacher/applicants', roles: ['teacher'], handler: teacherApplicants },
  { method: 'GET', path: '/api/teacher/applicants/:id', roles: ['teacher'], handler: teacherApplicantDetail },
  { method: 'POST', path: '/api/teacher/interviews', roles: ['teacher'], handler: submitInterview },
  { method: 'POST', path: '/api/teacher/decision', roles: ['teacher'], handler: teacherDecision },

  { method: 'POST', path: '/api/admin/import', roles: ['admin'], handler: adminImport },
  { method: 'GET', path: '/api/admin/schedules', roles: ['admin'], handler: adminSchedules },
  { method: 'PUT', path: '/api/admin/schedules/:id', roles: ['admin'], handler: updateSchedule },
  { method: 'GET', path: '/api/admin/notices', roles: ['admin'], handler: adminNotices },
  { method: 'POST', path: '/api/admin/notices', roles: ['admin'], handler: createNotice },
  { method: 'DELETE', path: '/api/admin/notices/:id', roles: ['admin'], handler: deleteNotice },
  { method: 'GET', path: '/api/admin/admissions', roles: ['admin'], handler: adminAdmissions },
  { method: 'PUT', path: '/api/admin/rooms/:id/quota', roles: ['admin'], handler: updateRoomQuota },
  { method: 'POST', path: '/api/admin/admissions/expire', roles: ['admin'], handler: expireConfirmations },
  { method: 'GET', path: '/api/admin/adjustments', roles: ['admin'], handler: adminAdjustments },
  { method: 'POST', path: '/api/admin/adjustments/run', roles: ['admin'], handler: runAdjustment },
  { method: 'GET', path: '/api/admin/publicity', roles: ['admin'], handler: adminPublicity },
  { method: 'POST', path: '/api/admin/publicity/publish', roles: ['admin'], handler: publishPublicity },
  { method: 'POST', path: '/api/admin/publicity/finalize', roles: ['admin'], handler: finalizePublicity },
  { method: 'GET', path: '/api/admin/logs', roles: ['admin'], handler: adminLogs }
]

function matchRoute(method, url) {
  const clean = url.split('?')[0]
  const segments = clean.split('/')
  for (const route of routes) {
    if (route.method !== method) continue
    const target = route.path.split('/')
    if (target.length !== segments.length) continue
    const params = {}
    let matched = true
    for (let i = 0; i < target.length; i++) {
      if (target[i].startsWith(':')) {
        params[target[i].slice(1)] = decodeURIComponent(segments[i])
      } else if (target[i] !== segments[i]) {
        matched = false
        break
      }
    }
    if (matched) return { route, params }
  }
  return null
}

export async function handleRequest(config) {
  const method = (config.method || 'get').toUpperCase()
  const url = config.url || ''
  const matched = matchRoute(method, url)
  if (!matched) {
    return { status: 404, body: { code: 404, message: `接口不存在：${method} ${url}`, data: null } }
  }

  const db = loadDb()
  const auth = resolveAuth(db, config)
  const roles = matched.route.roles

  if (!roles.includes('public')) {
    if (!auth) return { status: 401, body: { code: 401, message: '登录态无效，请重新登录', data: null } }
    if (!roles.includes(auth.role)) {
      return { status: 403, body: { code: 403, message: '当前角色无权访问该接口，已由后端拦截', data: null } }
    }
  }

  try {
    const result = matched.route.handler({
      db,
      auth,
      params: matched.params,
      query: config.params || {},
      body: parseBody(config)
    })
    saveDb(db)
    return { status: 200, body: { code: 200, message: result.message || '操作成功', data: result.data ?? null } }
  } catch (e) {
    if (e instanceof ApiError) {
      return { status: e.code, body: { code: e.code, message: e.message, data: e.data } }
    }
    console.error('[mock] 处理异常', e)
    return { status: 500, body: { code: 500, message: '服务内部错误（演示 mock）', data: null } }
  }
}

export { fail }
