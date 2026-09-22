import { renderHook, act, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as notificationApi from '../../services/notificationApi.js'
import { useNotifications } from '../useNotifications.js'

describe('useNotifications', () => {
  const mockInitialNotifications = [
    { id: 101, content: 'User A sent a friend request', isRead: false, type: 'FRIEND_REQUEST', senderUsername: 'userA' },
    { id: 102, content: 'User B reacted to your message', isRead: true, type: 'REACT_MESSAGE', senderUsername: 'userB' },
  ]

  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(notificationApi, 'getNotifications').mockResolvedValue({
      content: [...mockInitialNotifications],
      nextCursor: 'next-123',
      hasMore: true,
    })
    vi.spyOn(notificationApi, 'getUnreadNotificationCount').mockResolvedValue(1)
    vi.spyOn(notificationApi, 'markNotificationAsRead').mockResolvedValue(null)
    vi.spyOn(notificationApi, 'markAllNotificationsAsRead').mockResolvedValue(1)
  })

  it('fetches initial notifications and unread count on mount', async () => {
    const { result } = renderHook(() => useNotifications({ enabled: true }))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
      expect(result.current.notifications).toHaveLength(2)
      expect(result.current.unreadCount).toBe(1)
      expect(result.current.hasMore).toBe(true)
      expect(result.current.nextCursor).toBe('next-123')
    })
  })

  it('loads more notifications via loadMore', async () => {
    const { result } = renderHook(() => useNotifications({ enabled: true }))

    await waitFor(() => expect(result.current.loading).toBe(false))

    vi.spyOn(notificationApi, 'getNotifications').mockResolvedValueOnce({
      content: [{ id: 103, content: 'User C accepted friend request', isRead: true, type: 'FRIEND_ACCEPTED' }],
      nextCursor: null,
      hasMore: false,
    })

    await act(async () => {
      await result.current.loadMore()
    })

    expect(result.current.notifications).toHaveLength(3)
    expect(result.current.hasMore).toBe(false)
    expect(result.current.nextCursor).toBeNull()
  })

  it('optimistically marks a notification as read and decrements unreadCount', async () => {
    const { result } = renderHook(() => useNotifications({ enabled: true }))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.unreadCount).toBe(1)

    await act(async () => {
      await result.current.markAsRead(101)
    })

    const updated = result.current.notifications.find((n) => n.id === 101)
    expect(updated.isRead).toBe(true)
    expect(result.current.unreadCount).toBe(0)
    expect(notificationApi.markNotificationAsRead).toHaveBeenCalledWith(101)
  })

  it('optimistically marks all notifications as read and resets unreadCount to 0', async () => {
    const { result } = renderHook(() => useNotifications({ enabled: true }))

    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.markAllAsRead()
    })

    expect(result.current.unreadCount).toBe(0)
    expect(result.current.notifications.every((n) => n.isRead)).toBe(true)
    expect(notificationApi.markAllNotificationsAsRead).toHaveBeenCalled()
  })

  it('handles incoming real-time notification from WebSocket', async () => {
    const playSound = vi.fn()
    const showToast = vi.fn()

    const { result } = renderHook(() =>
      useNotifications({
        enabled: true,
        playNotificationSound: playSound,
        showToast,
      })
    )

    await waitFor(() => expect(result.current.loading).toBe(false))

    const socketPayload = {
      id: 999,
      type: 'FRIEND_REQUEST',
      content: 'User D sent you a friend request',
      senderUsername: 'userD',
      senderFirstName: 'User',
      senderLastName: 'D',
    }

    act(() => {
      result.current.handleRealtimeNotification(socketPayload)
    })

    expect(result.current.notifications[0].id).toBe(999)
    expect(result.current.notifications[0].isRead).toBe(false)
    expect(result.current.unreadCount).toBe(2)
    expect(playSound).toHaveBeenCalled()
    expect(showToast).toHaveBeenCalledWith('User D sent you a friend request')
  })

  it('ignores non-notification socket events like USER_ONLINE or REQUEST_SENT_SUCCESS', async () => {
    const playSound = vi.fn()
    const showToast = vi.fn()

    const { result } = renderHook(() =>
      useNotifications({
        enabled: true,
        playNotificationSound: playSound,
        showToast,
      })
    )

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.unreadCount).toBe(1)
    expect(result.current.notifications).toHaveLength(2)

    act(() => {
      const ignored1 = result.current.handleRealtimeNotification({
        type: 'USER_ONLINE',
        message: 'userX is online',
        relatedUsername: 'userX',
      })
      const ignored2 = result.current.handleRealtimeNotification({
        type: 'REQUEST_SENT_SUCCESS',
        message: 'Friend request sent',
      })
      expect(ignored1).toBeNull()
      expect(ignored2).toBeNull()
    })

    // unreadCount and notifications list should not have changed
    expect(result.current.unreadCount).toBe(1)
    expect(result.current.notifications).toHaveLength(2)
    expect(playSound).not.toHaveBeenCalled()
    expect(showToast).not.toHaveBeenCalled()
  })

  it('sets unreadCount even when getNotifications fails on mount', async () => {
    vi.spyOn(notificationApi, 'getNotifications').mockRejectedValueOnce(new Error('500 Internal Server Error'))
    vi.spyOn(notificationApi, 'getUnreadNotificationCount').mockResolvedValueOnce(4)

    const { result } = renderHook(() => useNotifications({ enabled: true }))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.notifications).toEqual([])
    expect(result.current.unreadCount).toBe(4)
  })
})
