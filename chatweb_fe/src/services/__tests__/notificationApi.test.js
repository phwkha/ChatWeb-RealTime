import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as apiClient from '../apiClient.js'
import {
  getNotifications,
  getUnreadNotificationCount,
  markNotificationAsRead,
  markAllNotificationsAsRead,
} from '../notificationApi.js'

describe('notificationApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('fetches cursor-paginated notifications', async () => {
    const mockResponse = {
      code: 200,
      message: 'success',
      data: {
        content: [
          { id: 1, content: 'Notification 1', isRead: false },
          { id: 2, content: 'Notification 2', isRead: true },
        ],
        nextCursor: 'abc123cursor',
        hasMore: true,
      },
    }

    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue(mockResponse)

    const result = await getNotifications({ cursor: 'cursorX', size: 15 })

    expect(apiRequestSpy).toHaveBeenCalledWith('/api/notifications?cursor=cursorX&size=15')
    expect(result).toEqual({
      content: mockResponse.data.content,
      nextCursor: 'abc123cursor',
      hasMore: true,
    })
  })

  it('fetches default first page without cursor', async () => {
    const mockResponse = {
      code: 200,
      data: {
        content: [],
        nextCursor: null,
        hasMore: false,
      },
    }
    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue(mockResponse)

    const result = await getNotifications()

    expect(apiRequestSpy).toHaveBeenCalledWith('/api/notifications?size=20')
    expect(result.content).toEqual([])
    expect(result.hasMore).toBe(false)
  })

  it('fetches unread notification count', async () => {
    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({
      code: 200,
      data: 5,
    })

    const count = await getUnreadNotificationCount()

    expect(apiRequestSpy).toHaveBeenCalledWith('/api/notifications/unread-counts')
    expect(count).toBe(5)
  })

  it('marks a single notification as read', async () => {
    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({
      code: 200,
      data: null,
    })

    const result = await markNotificationAsRead(42)

    expect(apiRequestSpy).toHaveBeenCalledWith('/api/notifications/42/read', { method: 'PATCH' })
    expect(result).toBeNull()
  })

  it('marks all notifications as read', async () => {
    const apiRequestSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({
      code: 200,
      data: 8,
    })

    const result = await markAllNotificationsAsRead()

    expect(apiRequestSpy).toHaveBeenCalledWith('/api/notifications/read-all', { method: 'PATCH' })
    expect(result).toBe(8)
  })
})
