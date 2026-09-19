import { useCallback, useEffect, useRef, useState } from 'react'
import { apiRequest, getErrorMessage } from '../services/apiClient.js'
import { useChatSocket } from './useChatSocket.js'
import {
  broadcastWatermarkRead,
  isDisplayableChatMessage,
  isIncomingMessageBlocked,
  normalizeMessages,
  parseRealtimeReaction,
  parseRealtimeReceipt,
  parseRealtimeTyping,
  promoteStatuses,
  upsertMessage,
  FRIEND_EVENT_TYPES,
  RECEIPT_PREFIX,
  TYPING_PREFIX,
  REACTION_PREFIX,
  WATERMARK_CHANNEL_NAME,
} from '../components/chat/chatUtils.js'

export function useChatRealtime({
  currentUser,
  selectedUser,
  activeSection,
  blockedMessageIntervals,
  language,
  playNotificationSound,
  playInboxSound,
  showToast,
  t,
  setMessagesByUser,
  messagesByUserRef,
  recordMessageEdit,
  removeRateLimitedMessage,
  loadConversation,
  scheduleConnectionSync,
  updatePeerPresence,
}) {
  const [unreadCounts, setUnreadCounts] = useState({})
  const [typingUsers, setTypingUsers] = useState({})
  const [worldMessages, setWorldMessages] = useState([])
  const [worldCursor, setWorldCursor] = useState(null)
  const [worldHasMore, setWorldHasMore] = useState(false)
  const [worldNotifications, setWorldNotifications] = useState([])

  const selectedRef = useRef(selectedUser)
  const activeSectionRef = useRef(activeSection)
  const socketSenderRef = useRef(null)
  const typingTimeoutsRef = useRef(new Map())
  const readAckTimersRef = useRef(new Map())
  const pendingMarkReadRef = useRef(new Set())
  const unreadCountsRef = useRef({})
  const pendingReceiptsRef = useRef(new Map())
  const receiptDebounceTimersRef = useRef(new Map())

  const currentUsernameKey = String(currentUser?.username || '').trim().toLocaleLowerCase('en-US')

  useEffect(() => { selectedRef.current = selectedUser }, [selectedUser])
  useEffect(() => { activeSectionRef.current = activeSection }, [activeSection])
  useEffect(() => { unreadCountsRef.current = unreadCounts }, [unreadCounts])

  const isActivelyViewingConversation = useCallback((username) => (
    activeSectionRef.current === 'chat'
    && selectedRef.current?.username === username
    && typeof document !== 'undefined'
    && document.visibilityState === 'visible'
    && document.hasFocus()
  ), [])

  const sendRealtimeReceiptImmediate = useCallback((recipient, status, sourceMessage = null, timestamp = null) => {
    if (!recipient || !socketSenderRef.current) return false
    return socketSenderRef.current({
      recipient,
      content: `${RECEIPT_PREFIX}${JSON.stringify({
        status,
        statusTimestamp: timestamp || new Date().toISOString(),
        messageId: sourceMessage?.id || null,
        localId: sourceMessage?.localId || null,
      })}`,
      contentType: 'TEXT',
      messageType: 'TYPING',
    })
  }, [])

  const flushPendingReceipts = useCallback(() => {
    pendingReceiptsRef.current.forEach((pending, recipient) => {
      window.clearTimeout(receiptDebounceTimersRef.current.get(recipient))
      receiptDebounceTimersRef.current.delete(recipient)
      sendRealtimeReceiptImmediate(recipient, pending.status, pending.sourceMessage, pending.timestamp)
    })
    pendingReceiptsRef.current.clear()
  }, [sendRealtimeReceiptImmediate])

  const sendRealtimeReceipt = useCallback((recipient, status, sourceMessage = null) => {
    if (!recipient || !socketSenderRef.current) return false
    if (status !== 'READ') return sendRealtimeReceiptImmediate(recipient, status, sourceMessage)
    pendingReceiptsRef.current.set(recipient, { status, sourceMessage, timestamp: new Date().toISOString() })
    if (!receiptDebounceTimersRef.current.has(recipient)) {
      const timer = window.setTimeout(() => {
        receiptDebounceTimersRef.current.delete(recipient)
        const pending = pendingReceiptsRef.current.get(recipient)
        if (pending) {
          pendingReceiptsRef.current.delete(recipient)
          sendRealtimeReceiptImmediate(recipient, pending.status, pending.sourceMessage, pending.timestamp)
        }
      }, 250)
      receiptDebounceTimersRef.current.set(recipient, timer)
    }
    return true
  }, [sendRealtimeReceiptImmediate])

  const markAsRead = useCallback((sender, force = false) => {
    if (!sender) return
    const currentUnread = Number(unreadCountsRef.current[sender] || 0)
    if (!force && currentUnread <= 0 && !readAckTimersRef.current.has(sender)) return
    setUnreadCounts((cur) => ({ ...cur, [sender]: 0 }))
    if (currentUser?.username) broadcastWatermarkRead(currentUser.username, sender, new Date().toISOString())
    pendingMarkReadRef.current.add(sender)
    window.clearTimeout(readAckTimersRef.current.get(sender))
    const timer = window.setTimeout(async () => {
      readAckTimersRef.current.delete(sender)
      pendingMarkReadRef.current.delete(sender)
      try {
        await apiRequest('/api/messages/mark-as-read', { method: 'POST', body: { sender } })
      } catch {
        try {
          const res = await apiRequest('/api/messages/unread-counts')
          setUnreadCounts(res?.data?.unreadCounts || {})
        } catch { /* ignore unread reconciliation error */ }
      }
    }, 220)
    readAckTimersRef.current.set(sender, timer)
  }, [currentUser])

  const flushPendingMarkAsRead = useCallback(() => {
    if (!pendingMarkReadRef.current.size) return
    const senders = Array.from(pendingMarkReadRef.current)
    pendingMarkReadRef.current.clear()
    senders.forEach((sender) => {
      window.clearTimeout(readAckTimersRef.current.get(sender))
      readAckTimersRef.current.delete(sender)
      void apiRequest('/api/messages/mark-as-read', { method: 'POST', body: { sender } }).catch(() => {})
    })
  }, [])

  const sendTypingStatus = useCallback((recipient, active) => {
    if (!recipient || !socketSenderRef.current) return false
    return socketSenderRef.current({
      recipient,
      content: `${TYPING_PREFIX}${JSON.stringify({ active })}`,
      contentType: 'TEXT',
      messageType: 'TYPING',
    })
  }, [])

  const sendReactionControl = useCallback((recipient, message) => {
    if (!recipient || !message?.id || !socketSenderRef.current) return false
    return socketSenderRef.current({
      recipient,
      content: `${REACTION_PREFIX}${JSON.stringify({ message })}`,
      contentType: 'TEXT',
      messageType: 'TYPING',
    })
  }, [])

  const loadWorldHistory = useCallback(async (cursor = null, appendOlder = false) => {
    try {
      const query = new URLSearchParams({ size: '30' })
      if (cursor) query.set('cursor', cursor)
      const res = await apiRequest(`/api/systems/message?${query}`)
      const history = normalizeMessages(res?.data?.content || [])
      setWorldMessages((cur) => {
        const merged = appendOlder ? [...history, ...cur] : [...cur, ...history]
        const unique = new Map(merged.map((item) => [`${item.sender}-${item.timestamp}-${item.content}`, item]))
        return normalizeMessages([...unique.values()])
      })
      setWorldCursor(res?.data?.nextCursor || null)
      setWorldHasMore(Boolean(res?.data?.hasMore))
    } catch (error) {
      showToast(getErrorMessage(error, t('errorGeneric')), 'error')
    }
  }, [showToast, t])

  const handleIncomingMessage = useCallback(async (message) => {
    if (!message?.sender || !message?.recipient || !currentUser) return
    const peer = message.sender === currentUser.username ? message.recipient : message.sender
    if (message.messageType === 'TYPING') {
      const receipt = parseRealtimeReceipt(message.content)
      if (receipt && message.sender !== currentUser.username) {
        setMessagesByUser((cur) => ({
          ...cur,
          [message.sender]: promoteStatuses((cur[message.sender] || []).map((ex) => (
            receipt.messageId && receipt.localId && ex.localId === receipt.localId ? { ...ex, id: receipt.messageId } : ex
          )), currentUser.username, receipt.status, receipt.statusTimestamp),
        }))
        return
      }
      const reactionMessage = parseRealtimeReaction(message.content)
      if (reactionMessage && message.sender !== currentUser.username) {
        const reactionPeer = reactionMessage.sender === currentUser.username ? reactionMessage.recipient : reactionMessage.sender
        setMessagesByUser((cur) => ({ ...cur, [reactionPeer]: upsertMessage(cur[reactionPeer] || [], reactionMessage) }))
        return
      }
      if (message.sender !== currentUser.username) {
        const typing = parseRealtimeTyping(message.content)
        setTypingUsers((cur) => ({ ...cur, [message.sender]: typing?.active !== false }))
        window.clearTimeout(typingTimeoutsRef.current.get(message.sender))
        const timeout = window.setTimeout(() => setTypingUsers((cur) => ({ ...cur, [message.sender]: false })), typing?.active === false ? 0 : 3500)
        typingTimeoutsRef.current.set(message.sender, timeout)
      }
      return
    }
    if (!isDisplayableChatMessage(message)) return
    if (message.sender !== currentUser.username) updatePeerPresence(message.sender, true)
    if (message.sender !== currentUser.username && isIncomingMessageBlocked(blockedMessageIntervals, currentUser.username, peer)) return
    setMessagesByUser((cur) => ({ ...cur, [peer]: upsertMessage(cur[peer] || [], message) }))
    if (message.sender !== currentUser.username) {
      playInboxSound()
      const isViewing = isActivelyViewingConversation(peer)
      sendRealtimeReceipt(peer, isViewing ? 'READ' : 'DELIVERED', message)
      if (isViewing) markAsRead(peer, true)
      else setUnreadCounts((cur) => ({ ...cur, [peer]: (cur[peer] || 0) + 1 }))
    }
  }, [blockedMessageIntervals, currentUser, isActivelyViewingConversation, markAsRead, playInboxSound, sendRealtimeReceipt, setMessagesByUser, updatePeerPresence])

  const handleNotification = useCallback(async (notification) => {
    if (!notification?.type) return
    const isEdit = ['EDIT_MESSAGE', 'MESSAGE_EDITED'].includes(notification.type)
    const isRevoke = ['REVOKE_MESSAGE', 'MESSAGE_REVOKED'].includes(notification.type)
    const isReaction = ['REACT_MESSAGE', 'MESSAGE_REACTED'].includes(notification.type)
    const isMsgUpdate = isEdit || isRevoke || isReaction || notification.type === 'STATUS_MESSAGE'
    const notifPeer = notification.data?.sender === currentUser?.username ? notification.data?.recipient : notification.data?.sender || notification.data?.reader || notification.relatedUsername
    if (isMsgUpdate && isIncomingMessageBlocked(blockedMessageIntervals, currentUser?.username, notifPeer)) return
    if (notification.type === 'FRIEND_REQUEST') {
      playNotificationSound()
      if (notification.message) showToast(notification.message)
    }
    if (FRIEND_EVENT_TYPES.has(notification.type) && !['USER_ONLINE', 'USER_OFFLINE'].includes(notification.type)) scheduleConnectionSync()
    if (['USER_ONLINE', 'USER_OFFLINE'].includes(notification.type)) updatePeerPresence(notification.relatedUsername, notification.type === 'USER_ONLINE')
    if (notification.type === 'STATUS_MESSAGE' && notification.data && currentUser) {
      const { reader, sender, readTimestamp, status, statusTimestamp } = notification.data
      const nextStatus = status || 'READ'
      const nextTimestamp = statusTimestamp || readTimestamp
      if (reader === currentUser.username) {
        if (sender) { setUnreadCounts((cur) => ({ ...cur, [sender]: 0 })); broadcastWatermarkRead(currentUser.username, sender, nextTimestamp) }
      } else {
        setMessagesByUser((cur) => ({ ...cur, [reader]: promoteStatuses(cur[reader], currentUser.username, nextStatus, nextTimestamp) }))
      }
    }
    if ((isEdit || isRevoke || isReaction) && notification.data && currentUser) {
      const data = isEdit ? { ...notification.data, edited: true, isEdited: true }
        : isRevoke ? { ...notification.data, content: '', fileUrl: null, fileName: null, fileSize: null, reactions: null, deleted: true, isDeleted: true }
        : notification.data
      const peer = data.sender === currentUser.username ? data.recipient : data.sender
      if (isEdit) {
        const prev = (messagesByUserRef.current[peer] || []).find((m) => m.id === data.id)
        if (prev && prev.content !== data.content) recordMessageEdit(prev)
      }
      setMessagesByUser((cur) => ({ ...cur, [peer]: upsertMessage(cur[peer] || [], data) }))
    }
  }, [blockedMessageIntervals, currentUser, messagesByUserRef, playNotificationSound, recordMessageEdit, scheduleConnectionSync, setMessagesByUser, showToast, updatePeerPresence])

  const handleWorldMessage = useCallback((message) => {
    if (!String(message?.content || '').trim()) return
    setWorldNotifications((cur) => [{ ...message, receivedAt: message.timestamp || new Date().toISOString() }, ...cur].slice(0, 50))
    if (String(message.sender || '').toLocaleLowerCase('en-US') !== currentUsernameKey) playNotificationSound()
    showToast(message.content)
    setWorldMessages((cur) => normalizeMessages([...cur, message]))
  }, [currentUsernameKey, playNotificationSound, showToast])

  const handleSocketError = useCallback((error) => {
    const errorCode = String(error?.errorCode || error?.code || error?.status || '').toUpperCase()
    if (errorCode === 'RATE_LIMITED' || errorCode === '429') {
      removeRateLimitedMessage(error)
      showToast(getErrorMessage(error, t('socketError')), 'error')
      return
    }
    const failedRequest = error?.request || error?.data?.request
    if (failedRequest?.localId && failedRequest?.recipient) {
      setMessagesByUser((cur) => ({
        ...cur,
        [failedRequest.recipient]: (cur[failedRequest.recipient] || []).map((m) => m.localId === failedRequest.localId ? { ...m, clientFailed: true, status: 'FAILED', failureAttempt: (m.failureAttempt || 0) + 1 } : m),
      }))
    }
    showToast(getErrorMessage(error, t('socketError')), 'error')
  }, [removeRateLimitedMessage, setMessagesByUser, showToast, t])

  const handleSocketConnected = useCallback(() => {
    if (selectedRef.current && activeSectionRef.current === 'chat') {
      void loadConversation(selectedRef.current, true)
      const blocked = isIncomingMessageBlocked(blockedMessageIntervals, currentUser?.username, selectedRef.current.username)
      if (!blocked && isActivelyViewingConversation(selectedRef.current.username)) {
        markAsRead(selectedRef.current.username)
        sendRealtimeReceipt(selectedRef.current.username, 'READ')
      }
    }
  }, [blockedMessageIntervals, currentUser?.username, isActivelyViewingConversation, loadConversation, markAsRead, sendRealtimeReceipt])

  const { connectionState, sendPrivateMessage, sendWorldMessage } = useChatSocket({
    enabled: Boolean(currentUser), language, subscribeToWorld: true,
    onMessage: handleIncomingMessage, onNotification: handleNotification,
    onWorldMessage: handleWorldMessage, onError: handleSocketError, onConnected: handleSocketConnected,
  })
  useEffect(() => { socketSenderRef.current = sendPrivateMessage }, [sendPrivateMessage])

  useEffect(() => {
    void loadWorldHistory()
    void apiRequest('/api/messages/unread-counts').then((res) => setUnreadCounts(res?.data?.unreadCounts || {})).catch(() => {})
  }, [loadWorldHistory])

  useEffect(() => {
    const selected = selectedUser
    const username = selected?.username
    if (activeSection !== 'chat' || !selected || !username) return
    const blocked = isIncomingMessageBlocked(blockedMessageIntervals, currentUser?.username, username)
    if (!blocked && isActivelyViewingConversation(username)) {
      markAsRead(username)
      sendRealtimeReceipt(username, 'READ')
    }
  }, [activeSection, blockedMessageIntervals, currentUser?.username, isActivelyViewingConversation, markAsRead, selectedUser, selectedUser?.username, sendRealtimeReceipt])

  useEffect(() => {
    const markVisibleAsRead = () => {
      const selected = selectedRef.current
      if (!selected || !isActivelyViewingConversation(selected.username)) return
      if (isIncomingMessageBlocked(blockedMessageIntervals, currentUser?.username, selected.username)) return
      if (Number(unreadCounts[selected.username] || 0) <= 0) return
      markAsRead(selected.username)
      sendRealtimeReceipt(selected.username, 'READ')
    }
    window.addEventListener('focus', markVisibleAsRead)
    document.addEventListener('visibilitychange', markVisibleAsRead)
    return () => {
      window.removeEventListener('focus', markVisibleAsRead)
      document.removeEventListener('visibilitychange', markVisibleAsRead)
    }
  }, [blockedMessageIntervals, currentUser?.username, isActivelyViewingConversation, markAsRead, sendRealtimeReceipt, unreadCounts])

  useEffect(() => {
    if (typeof window === 'undefined' || !window.BroadcastChannel) return
    const channel = new BroadcastChannel(WATERMARK_CHANNEL_NAME)
    channel.onmessage = (event) => {
      const payload = event?.data
      if (!payload || payload.type !== 'WATERMARK_READ') return
      const { reader, sender, readTimestamp, status } = payload
      if (!reader || !sender || reader !== currentUser?.username) return
      setUnreadCounts((cur) => ({ ...cur, [sender]: 0 }))
      const nextStatus = status || 'READ'
      const nextTimestamp = readTimestamp || new Date().toISOString()
      setMessagesByUser((cur) => ({ ...cur, [sender]: promoteStatuses(cur[sender], currentUser.username, nextStatus, nextTimestamp) }))
    }
    return () => channel.close()
  }, [currentUser?.username, setMessagesByUser])

  useEffect(() => {
    const handleUnload = () => { flushPendingReceipts(); flushPendingMarkAsRead() }
    window.addEventListener('beforeunload', handleUnload)
    window.addEventListener('pagehide', handleUnload)
    return () => {
      window.removeEventListener('beforeunload', handleUnload)
      window.removeEventListener('pagehide', handleUnload)
      flushPendingReceipts(); flushPendingMarkAsRead()
    }
  }, [flushPendingMarkAsRead, flushPendingReceipts])

  return {
    connectionState,
    sendPrivateMessage,
    sendWorldMessage,
    unreadCounts,
    setUnreadCounts,
    typingUsers,
    worldMessages,
    worldCursor,
    worldHasMore,
    worldNotifications,
    loadWorldHistory,
    markAsRead,
    sendRealtimeReceipt,
    sendTypingStatus,
    sendReactionControl,
  }
}

export default useChatRealtime
