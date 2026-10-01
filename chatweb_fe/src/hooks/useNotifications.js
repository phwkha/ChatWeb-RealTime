import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getNotifications,
  getUnreadNotificationCount,
  markNotificationAsRead,
  markAllNotificationsAsRead,
} from '../services/notificationApi.js'
import { PERSISTED_NOTIFICATION_TYPES, formatNotificationContent } from '../components/chat/chatUtils.js'

export function useNotifications({ enabled = true, activeSection, currentUser, playNotificationSound, showToast, t } = {}) {
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

  const callbacksRef = useRef({ playNotificationSound, showToast, t })
  useEffect(() => {
    callbacksRef.current = { playNotificationSound, showToast, t }
  }, [playNotificationSound, showToast, t])

  const listFetchedRef = useRef(false)

  const fetchUnreadCount = useCallback(async () => {
    if (!enabled) return
    try {
      const count = await getUnreadNotificationCount()
      setUnreadCount(count)
    } catch {
      // Ignore count fetch errors
    }
  }, [enabled])

  const fetchInitialList = useCallback(async () => {
    if (!enabled) return
    setLoading(true)
    try {
      const listResult = await getNotifications({ size: 20 })
      const rawContent = listResult?.content || []
      const validNotifications = rawContent.filter((item) => (
        PERSISTED_NOTIFICATION_TYPES.has(String(item?.type || '').toUpperCase())
      ))
      setNotifications(validNotifications)
      setNextCursor(listResult?.nextCursor || null)
      setHasMore(Boolean(listResult?.hasMore))
      listFetchedRef.current = true
    } catch {
      // Ignore list fetch errors
    } finally {
      setLoading(false)
    }
  }, [enabled])

  // Always fetch unread notification count on mount for badge
  useEffect(() => {
    void fetchUnreadCount()
  }, [fetchUnreadCount])

  // Lazy fetch full notifications list only when in notifications section (or if section is unspecified)
  useEffect(() => {
    const shouldFetchList = activeSection === 'notifications' || activeSection === undefined
    if (shouldFetchList && !listFetchedRef.current) {
      void fetchInitialList()
    }
  }, [activeSection, fetchInitialList])

  const loadMore = useCallback(async () => {
    if (!hasMore || loadingMore || !nextCursor) return
    setLoadingMore(true)
    try {
      const res = await getNotifications({ cursor: nextCursor, size: 20 })
      setNotifications((prev) => {
        const existingIds = new Set(prev.map((n) => n.id))
        const incoming = (res.content || []).filter((n) => (
          !existingIds.has(n.id) && PERSISTED_NOTIFICATION_TYPES.has(String(n?.type || '').toUpperCase())
        ))
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
    if (!wasUnread) return
    setUnreadCount((prev) => Math.max(0, prev - 1))

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
    const rawType = socketPayload.type || rawData.type
    const type = String(rawType || '').trim().toUpperCase()

    // Filter strictly to notification types stored in BE
    if (!PERSISTED_NOTIFICATION_TYPES.has(type)) {
      return null
    }

    const senderUsername = socketPayload.senderUsername
      || rawData.senderUsername
      || socketPayload.relatedUsername
      || rawData.sender
      || ''

    // If currentUser is present, do not notify self actions (e.g. self reaction)
    if (currentUser?.username && senderUsername && currentUser.username.toLowerCase() === senderUsername.toLowerCase()) {
      return null
    }

    const id = socketPayload.notificationId
      || socketPayload.id
      || rawData.notificationId
      || (type !== 'REACT_MESSAGE' ? rawData.id : null)
      || Date.now()
    const content = socketPayload.content || socketPayload.message || rawData.content || ''

    const newNotification = {
      id,
      type,
      targetType: socketPayload.targetType || rawData.targetType || (type === 'REACT_MESSAGE' ? 'MESSAGE' : 'USER'),
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
      const toastText = formatNotificationContent(newNotification, callbacksRef.current.t) || content
      callbacksRef.current.showToast(toastText)
    }

    return newNotification
  }, [currentUser?.username])

  const refreshNotifications = useCallback(async () => {
    void fetchUnreadCount()
    if (activeSection === 'notifications') {
      void fetchInitialList()
    }
  }, [activeSection, fetchInitialList, fetchUnreadCount])

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
    refreshNotifications,
  }
}

export default useNotifications
