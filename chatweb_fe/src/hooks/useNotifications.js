import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getNotifications,
  getUnreadNotificationCount,
  markNotificationAsRead,
  markAllNotificationsAsRead,
} from '../services/notificationApi.js'

export function useNotifications({ enabled = true, playNotificationSound, showToast } = {}) {
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [nextCursor, setNextCursor] = useState(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)

  const notificationsRef = useRef(notifications)
  useEffect(() => {
    notificationsRef.current = notifications
  }, [notifications])

  const callbacksRef = useRef({ playNotificationSound, showToast })
  useEffect(() => {
    callbacksRef.current = { playNotificationSound, showToast }
  }, [playNotificationSound, showToast])

  const fetchInitial = useCallback(async () => {
    if (!enabled) return
    setLoading(true)
    try {
      const [listRes, countRes] = await Promise.all([
        getNotifications({ size: 20 }),
        getUnreadNotificationCount().catch(() => 0),
      ])
      setNotifications(listRes.content || [])
      setNextCursor(listRes.nextCursor)
      setHasMore(listRes.hasMore)
      setUnreadCount(countRes)
    } catch {
      // Ignore initial notification load errors
    } finally {
      setLoading(false)
    }
  }, [enabled])

  useEffect(() => {
    void fetchInitial()
  }, [fetchInitial])

  const loadMore = useCallback(async () => {
    if (!hasMore || loadingMore || !nextCursor) return
    setLoadingMore(true)
    try {
      const res = await getNotifications({ cursor: nextCursor, size: 20 })
      setNotifications((prev) => {
        const existingIds = new Set(prev.map((n) => n.id))
        const incoming = (res.content || []).filter((n) => !existingIds.has(n.id))
        return [...prev, ...incoming]
      })
      setNextCursor(res.nextCursor)
      setHasMore(res.hasMore)
    } catch {
      // Ignore load more errors
    } finally {
      setLoadingMore(false)
    }
  }, [hasMore, loadingMore, nextCursor])

  const markAsRead = useCallback(async (id) => {
    if (!id) return
    const wasUnread = notificationsRef.current.some((item) => item.id === id && !item.isRead)
    setNotifications((prev) =>
      prev.map((item) => (item.id === id ? { ...item, isRead: true } : item))
    )
    if (wasUnread) {
      setUnreadCount((prev) => Math.max(0, prev - 1))
    }

    try {
      await markNotificationAsRead(id)
    } catch {
      // Re-fetch unread count on failure to ensure consistency
      getUnreadNotificationCount().then(setUnreadCount).catch(() => {})
    }
  }, [])

  const markAllAsRead = useCallback(async () => {
    setNotifications((prev) => prev.map((item) => ({ ...item, isRead: true })))
    setUnreadCount(0)

    try {
      await markAllNotificationsAsRead()
    } catch {
      getUnreadNotificationCount().then(setUnreadCount).catch(() => {})
    }
  }, [])

  const handleRealtimeNotification = useCallback((socketPayload) => {
    if (!socketPayload) return null
    const rawData = socketPayload.data || {}
    const type = socketPayload.type || rawData.type

    // Filter notification-relevant types
    const NOTIF_TYPES = new Set(['FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'REACT_MESSAGE'])
    const isRelevant = NOTIF_TYPES.has(type) || socketPayload.targetType || rawData.targetType
    if (!isRelevant && !socketPayload.content && !socketPayload.message && !rawData.content) {
      return null
    }

    const id = socketPayload.id || rawData.id || Date.now()
    const content = socketPayload.content || socketPayload.message || rawData.content || ''
    const senderUsername = socketPayload.senderUsername || rawData.senderUsername || socketPayload.relatedUsername || rawData.sender || ''

    const newNotification = {
      id,
      type,
      targetType: socketPayload.targetType || rawData.targetType || 'USER',
      targetId: socketPayload.targetId || rawData.targetId || senderUsername,
      content,
      isRead: false,
      createdAt: socketPayload.createdAt || rawData.createdAt || new Date().toISOString(),
      senderUsername,
      senderFirstName: socketPayload.senderFirstName || rawData.senderFirstName || '',
      senderLastName: socketPayload.senderLastName || rawData.senderLastName || '',
      senderAvatar: socketPayload.senderAvatar || rawData.senderAvatar || null,
    }

    setNotifications((prev) => {
      if (prev.some((item) => item.id === newNotification.id)) return prev
      return [newNotification, ...prev]
    })
    setUnreadCount((prev) => prev + 1)

    if (callbacksRef.current.playNotificationSound) {
      callbacksRef.current.playNotificationSound()
    }
    if (content && callbacksRef.current.showToast) {
      callbacksRef.current.showToast(content)
    }

    return newNotification
  }, [])

  return {
    notifications,
    unreadCount,
    setUnreadCount,
    nextCursor,
    hasMore,
    loading,
    loadingMore,
    loadMore,
    markAsRead,
    markAllAsRead,
    handleRealtimeNotification,
    refreshNotifications: fetchInitial,
  }
}

export default useNotifications
