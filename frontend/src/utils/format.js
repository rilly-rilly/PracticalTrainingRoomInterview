function pad(n) {
  return String(n).padStart(2, '0')
}

export function formatDateTime(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function formatDate(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function toLocalInput(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function fromLocalInput(value) {
  if (!value) return NaN
  return new Date(value).getTime()
}

export function countdownText(endAt) {
  if (!endAt) return '—'
  const diff = endAt - Date.now()
  if (diff <= 0) return '已截止'
  const totalMinutes = Math.floor(diff / 60000)
  const days = Math.floor(totalMinutes / (60 * 24))
  const hours = Math.floor((totalMinutes % (60 * 24)) / 60)
  const minutes = totalMinutes % 60
  if (days > 0) return `剩余 ${days} 天 ${hours} 小时`
  if (hours > 0) return `剩余 ${hours} 小时 ${minutes} 分`
  return `剩余 ${minutes} 分`
}

export function windowStateText(w) {
  if (!w) return '未配置'
  if (w.isOpen) return '进行中'
  return Date.now() < w.startAt ? '未开始' : '已结束'
}
