import { apiRequest } from './apiClient.js'

function queryString(values) {
  const query = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) query.set(key, value)
  })
  return query.toString()
}

export const adminApi = {
  getUsers: (filters = {}) => apiRequest(`/api/admin/users?${queryString(filters)}`),
  getOnlineUsers: (page = 0, size = 20) => apiRequest(`/api/admin/users/online?page=${page}&size=${size}`),
  getUser: (username) => apiRequest(`/api/admin/users/${encodeURIComponent(username)}`),
  createUser: (body) => apiRequest('/api/admin/users', { method: 'POST', body }),
  updateUser: (username, body) => apiRequest(`/api/admin/users/${encodeURIComponent(username)}`, { method: 'PUT', body }),
  deleteUser: (username) => apiRequest(`/api/admin/users/${encodeURIComponent(username)}`, { method: 'DELETE' }),
  lockUser: (username) => apiRequest(`/api/admin/users/${encodeURIComponent(username)}/lock`, { method: 'POST' }),
  unlockUser: (username) => apiRequest(`/api/admin/users/${encodeURIComponent(username)}/unlock`, { method: 'POST' }),
  deleteAvatar: (username) => apiRequest(`/api/admin/users/${encodeURIComponent(username)}/avatar`, { method: 'DELETE' }),
  getAddresses: (username) => apiRequest(`/api/admin/users/${encodeURIComponent(username)}/addresses`),
  getAddress: (username, id) => apiRequest(`/api/admin/users/${encodeURIComponent(username)}/addresses/${encodeURIComponent(id)}`),
  updateAddress: (username, id, body) => apiRequest(`/api/admin/users/${encodeURIComponent(username)}/addresses/${encodeURIComponent(id)}`, { method: 'PUT', body }),
  deleteAddress: (username, id) => apiRequest(`/api/admin/users/${encodeURIComponent(username)}/addresses/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  getRoles: () => apiRequest('/api/roles'),
  getPermissions: () => apiRequest('/api/roles/permissions'),
  createRole: (body) => apiRequest('/api/roles', { method: 'POST', body }),
  updateRole: (id, body) => apiRequest(`/api/roles/${encodeURIComponent(id)}`, { method: 'PUT', body }),
  deleteRole: (id) => apiRequest(`/api/roles/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  sendEmail: (body) => apiRequest('/api/email/send', { method: 'POST', body }),
  advancedSearch: ({ user = [], address = [], page = 0, size = 20 } = {}) => {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    user.filter(Boolean).forEach((item) => query.append('user', item))
    address.filter(Boolean).forEach((item) => query.append('address', item))
    return apiRequest(`/api/search/users/filter?${query}`)
  },
}
