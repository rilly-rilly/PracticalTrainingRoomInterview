import http from './http'

export const authApi = {
  login: (payload) => http.post('/api/auth/login', payload),
  logout: () => http.post('/api/auth/logout'),
  me: () => http.get('/api/auth/me'),
  demoAccounts: () => http.get('/api/public/demo-accounts'),
  resetDemo: () => http.post('/api/public/reset')
}

export const publicApi = {
  home: () => http.get('/api/public/home'),
  rooms: () => http.get('/api/rooms'),
  room: (id) => http.get(`/api/rooms/${id}`)
}

export const studentApi = {
  intentions: () => http.get('/api/student/intentions'),
  submit: (payload) => http.post('/api/student/intentions', payload),
  status: () => http.get('/api/student/status'),
  confirm: (intentionId) => http.post('/api/student/confirm', { intentionId }),
  adjustments: () => http.get('/api/student/adjustments'),
  final: () => http.get('/api/student/final')
}

export const teacherApi = {
  applicants: (params) => http.get('/api/teacher/applicants', { params }),
  applicant: (id) => http.get(`/api/teacher/applicants/${id}`),
  score: (payload) => http.post('/api/teacher/interviews', payload),
  decide: (payload) => http.post('/api/teacher/decision', payload)
}

export const adminApi = {
  importData: (payload) => http.post('/api/admin/import', payload),
  schedules: () => http.get('/api/admin/schedules'),
  updateSchedule: (id, payload) => http.put(`/api/admin/schedules/${id}`, payload),
  notices: () => http.get('/api/admin/notices'),
  createNotice: (payload) => http.post('/api/admin/notices', payload),
  deleteNotice: (id) => http.delete(`/api/admin/notices/${id}`),
  admissions: () => http.get('/api/admin/admissions'),
  updateQuota: (id, quota) => http.put(`/api/admin/rooms/${id}/quota`, { quota }),
  expire: (payload) => http.post('/api/admin/admissions/expire', payload),
  adjustments: () => http.get('/api/admin/adjustments'),
  runAdjustment: (payload) => http.post('/api/admin/adjustments/run', payload),
  publicity: () => http.get('/api/admin/publicity'),
  publish: () => http.post('/api/admin/publicity/publish'),
  finalize: (payload) => http.post('/api/admin/publicity/finalize', payload),
  logs: () => http.get('/api/admin/logs')
}
