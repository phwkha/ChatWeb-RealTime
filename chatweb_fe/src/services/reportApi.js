import { apiRequest } from './apiClient.js'

function queryString(values) {
  const query = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) query.set(key, value)
  })
  return query.toString()
}

export const reportApi = {
  createReport: (body) => apiRequest('/api/reports', { method: 'POST', body }),
  getMyReports: (page = 0, size = 10, sortDir = 'desc') =>
    apiRequest(`/api/reports/me?page=${page}&size=${size}&sortDir=${sortDir}`),
  cancelReport: (id) => apiRequest(`/api/reports/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  // Admin endpoints
  getAdminReports: (filters = {}) => apiRequest(`/api/admin/reports?${queryString(filters)}`),
  getAdminReportStatistics: () => apiRequest('/api/admin/reports/statistics'),
  getAdminReport: (id) => apiRequest(`/api/admin/reports/${encodeURIComponent(id)}`),
  resolveAdminReport: (id, body) =>
    apiRequest(`/api/admin/reports/${encodeURIComponent(id)}/resolve`, { method: 'PUT', body }),
  deleteAdminReport: (id) =>
    apiRequest(`/api/admin/reports/${encodeURIComponent(id)}`, { method: 'DELETE' }),
}
