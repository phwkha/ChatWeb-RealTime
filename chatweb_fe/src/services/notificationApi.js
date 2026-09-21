import { apiRequest } from './apiClient.js'

export async function getNotifications({ cursor = null, size = 20 } = {}) {
  const params = new URLSearchParams()
  if (cursor) params.append('cursor', cursor)
  if (size != null) params.append('size', String(size))
  const query = params.toString() ? `?${params.toString()}` : ''
  const res = await apiRequest(`/api/notifications${query}`)
  return {
    content: res?.data?.content || [],
    nextCursor: res?.data?.nextCursor || null,
    hasMore: Boolean(res?.data?.hasMore),
  }
}

export async function getUnreadNotificationCount() {
  const res = await apiRequest('/api/notifications/unread-counts')
  return Number(res?.data || 0)
}

export async function markNotificationAsRead(id) {
  const res = await apiRequest(`/api/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH' })
  return res?.data ?? null
}

export async function markAllNotificationsAsRead() {
  const res = await apiRequest('/api/notifications/read-all', { method: 'PATCH' })
  return Number(res?.data || 0)
}

export const notificationApi = {
  getNotifications,
  getUnreadNotificationCount,
  markNotificationAsRead,
  markAllNotificationsAsRead,
}

export default notificationApi
