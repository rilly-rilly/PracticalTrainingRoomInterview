import { reactive } from 'vue'

export const toasts = reactive([])
let seq = 0

export function pushToast(message, type = 'info', code = null) {
  const id = ++seq
  toasts.push({ id, message, type, code })
  setTimeout(() => removeToast(id), 3800)
}

export function removeToast(id) {
  const idx = toasts.findIndex((t) => t.id === id)
  if (idx >= 0) toasts.splice(idx, 1)
}
