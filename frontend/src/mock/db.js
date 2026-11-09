import { buildSeed } from './seed'

const STORAGE_KEY = 'trainee-selection-demo-v2'

let cached = null

export function loadDb() {
  if (cached) return cached
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      cached = JSON.parse(raw)
      if (cached && cached.version === 2) return cached
    }
  } catch (e) {
    console.warn('[mock] 读取本地数据失败，将重新初始化', e)
  }
  cached = buildSeed()
  saveDb(cached)
  return cached
}

export function saveDb(db) {
  cached = db
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(db))
  } catch (e) {
    console.warn('[mock] 本地存储写入失败', e)
  }
}

export function resetDb() {
  cached = buildSeed()
  saveDb(cached)
  return cached
}

export function nextId(db, prefix) {
  db.seq = (db.seq || 100) + 1
  return `${prefix}-${db.seq}`
}

export function nowMs() {
  return Date.now()
}

export function pushLog(db, operatorRole, operatorName, action, detail) {
  db.logs.unshift({
    id: nextId(db, 'log'),
    operatorRole,
    operatorName,
    action,
    detail,
    createdAt: nowMs()
  })
}
