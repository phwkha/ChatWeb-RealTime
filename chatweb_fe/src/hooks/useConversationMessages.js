import { useCallback, useEffect, useRef, useState } from 'react'
import { apiRequest, generateUUID, getErrorMessage } from '../services/apiClient.js'
import {
  getMediaContentType,
  mergeMessageLists,
  messageEditHistoryKey,
  readMessageEditHistory,
  upsertMessage,
  wasMessageSentWhileBlocked,
  EDIT_HISTORY_STORAGE_KEY,
  MAX_MEDIA_SIZE_BYTES,
  MESSAGE_HISTORY_FALLBACK_SIZE,
  MESSAGE_PAGE_SIZE,
} from '../components/chat/chatUtils.js'

export function useConversationMessages({
  user,
  selectedUser,
  connectionState,
  blockedMessageIntervals,
  sendPrivateMessage,
  sendTypingStatus,
  sendReactionControl,
  showToast,
  t,
  selectedUserIsTyping,
}) {
  const [messagesByUser, setMessagesByUser] = useState({})
  const [conversationPages, setConversationPages] = useState({})
  const [loadingOlderMessages, setLoadingOlderMessages] = useState(false)
  const [loadingConversation, setLoadingConversation] = useState(false)
  const [messageDraft, setMessageDraft] = useState('')
  const [emojiPickerOpen, setEmojiPickerOpen] = useState(false)
  const [uploadingMedia, setUploadingMedia] = useState(false)
  const [rateLimitRemainingByUser, setRateLimitRemainingByUser] = useState({})
  const [reactionPickerMessageId, setReactionPickerMessageId] = useState(null)
  const [reactionSubmittingId, setReactionSubmittingId] = useState(null)
  const [detailMessageId, setDetailMessageId] = useState(null)
  const [editHistoryMessageId, setEditHistoryMessageId] = useState(null)
  const [messageEditHistory, setMessageEditHistory] = useState(readMessageEditHistory)
  const [editingMessageId, setEditingMessageId] = useState(null)
  const [editingMessageContent, setEditingMessageContent] = useState('')
  const [messageActionPending, setMessageActionPending] = useState(false)
  const [revokeTargetMessage, setRevokeTargetMessage] = useState(null)

  const selectedRef = useRef(selectedUser)
  const messagesByUserRef = useRef({})
  const messagesEndRef = useRef(null)
  const messageStreamRef = useRef(null)
  const preserveScrollHeightRef = useRef(null)
  const loadingOlderMessagesRef = useRef(false)
  const initialLoadScrollRef = useRef(false)
  const pendingOutgoingMessagesRef = useRef([])
  const rateLimitHandledRecipientsRef = useRef(new Set())
  const rateLimitResetTimersRef = useRef(new Map())
  const mediaInputRef = useRef(null)
  const messageInputRef = useRef(null)
  const typingPublishTimersRef = useRef(new Map())
  const typingLastSentRef = useRef(new Map())

  const currentUsernameKey = String(user?.username || '').trim().toLocaleLowerCase('en-US')
  const selectedUsername = selectedUser?.username || ''
  const activeMessages = selectedUser ? (messagesByUser[selectedUsername] || []) : []
  const rateLimitRemaining = rateLimitRemainingByUser[selectedUsername] || 0

  useEffect(() => {
    selectedRef.current = selectedUser
  }, [selectedUser])

  useEffect(() => {
    messagesByUserRef.current = messagesByUser
  }, [messagesByUser])

  useEffect(() => {
    if (!Object.values(rateLimitRemainingByUser).some((remaining) => remaining > 0)) return undefined
    const timer = window.setInterval(() => {
      setRateLimitRemainingByUser((current) => Object.fromEntries(
        Object.entries(current)
          .map(([username, remaining]) => [username, Math.max(0, remaining - 1)])
          .filter(([, remaining]) => remaining > 0),
      ))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [rateLimitRemainingByUser])

  const recordMessageEdit = useCallback((message, editedAt = new Date().toISOString()) => {
    if (!message?.id || !String(message.content || '').trim() || !currentUsernameKey) return
    const historyKey = messageEditHistoryKey(currentUsernameKey, message.id)
    setMessageEditHistory((current) => {
      const entries = current[historyKey] || []
      if (entries[entries.length - 1]?.content === message.content) return current
      const next = {
        ...current,
        [historyKey]: [...entries, { content: message.content, editedAt }].slice(-20),
      }
      localStorage.setItem(EDIT_HISTORY_STORAGE_KEY, JSON.stringify(next))
      return next
    })
  }, [currentUsernameKey])

  const loadConversation = useCallback(async (person, silent = false, cursor = null) => {
    if (!person || !user) return
    if (!cursor) initialLoadScrollRef.current = true
    if (!silent) setLoadingConversation(true)
    try {
      const fetchConversationPage = async (size) => {
        const query = new URLSearchParams({ user2: person.username, size: String(size) })
        if (cursor) query.set('cursor', cursor)
        return apiRequest(`/api/messages/private?${query}`)
      }
      let response
      try {
        response = await fetchConversationPage(MESSAGE_PAGE_SIZE)
      } catch (error) {
        if (Number(error?.status || error?.code) !== 500) throw error
        response = await fetchConversationPage(MESSAGE_HISTORY_FALLBACK_SIZE)
      }
      const visibleHistory = (response?.data?.content || []).filter((message) => (
        !wasMessageSentWhileBlocked(message, blockedMessageIntervals, user.username, person.username)
      ))
      setMessagesByUser((current) => ({
        ...current,
        [person.username]: mergeMessageLists(current[person.username], visibleHistory),
      }))
      setConversationPages((current) => ({
        ...current,
        [person.username]: {
          nextCursor: response?.data?.nextCursor || null,
          hasMore: Boolean(response?.data?.hasMore && response?.data?.nextCursor),
        },
      }))
      return visibleHistory.length
    } catch (error) {
      if (!silent || cursor) showToast(getErrorMessage(error, t('errorGeneric')), 'error')
      return -1
    } finally {
      if (!silent) setLoadingConversation(false)
    }
  }, [blockedMessageIntervals, showToast, t, user])

  const loadOlderConversation = useCallback(async () => {
    const person = selectedRef.current
    const page = conversationPages[person?.username]
    if (!person || !page?.hasMore || !page.nextCursor || loadingOlderMessagesRef.current) return
    preserveScrollHeightRef.current = messageStreamRef.current
      ? { height: messageStreamRef.current.scrollHeight, top: messageStreamRef.current.scrollTop }
      : null
    loadingOlderMessagesRef.current = true
    setLoadingOlderMessages(true)
    try {
      const loadedCount = await loadConversation(person, true, page.nextCursor)
      if (loadedCount <= 0) preserveScrollHeightRef.current = null
    } finally {
      loadingOlderMessagesRef.current = false
      setLoadingOlderMessages(false)
    }
  }, [conversationPages, loadConversation])

  const handleMessageStreamScroll = useCallback((event) => {
    if (initialLoadScrollRef.current) return
    if (event.currentTarget.scrollTop <= 80) void loadOlderConversation()
  }, [loadOlderConversation])

  useEffect(() => {
    const preserved = preserveScrollHeightRef.current
    if (preserved && messageStreamRef.current) {
      messageStreamRef.current.scrollTop = preserved.top
        + messageStreamRef.current.scrollHeight - preserved.height
      preserveScrollHeightRef.current = null
      return
    }
    const isInitialLoad = initialLoadScrollRef.current
    const stream = messageStreamRef.current
    if (stream) {
      stream.scrollTo({ top: stream.scrollHeight, behavior: isInitialLoad ? 'instant' : 'smooth' })
    }
    if (isInitialLoad) {
      initialLoadScrollRef.current = false
      return
    }
    if (messageStreamRef.current?.scrollHeight <= messageStreamRef.current?.clientHeight
      && conversationPages[selectedUser?.username]?.hasMore) {
      void loadOlderConversation()
    }
  }, [activeMessages.length, conversationPages, loadOlderConversation, selectedUser?.username, selectedUserIsTyping])

  useEffect(() => {
    if (!reactionPickerMessageId && !detailMessageId && !editHistoryMessageId && !emojiPickerOpen) return undefined
    const closePicker = (event) => {
      if (!event.target.closest('.message-reaction-anchor')) {
        setReactionPickerMessageId(null)
        setDetailMessageId(null)
      }
      if (!event.target.closest('.message-edit-history-anchor')) setEditHistoryMessageId(null)
      if (!event.target.closest('.composer-emoji-anchor')) setEmojiPickerOpen(false)
    }
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') {
        setReactionPickerMessageId(null)
        setDetailMessageId(null)
        setEditHistoryMessageId(null)
        setEmojiPickerOpen(false)
      }
    }
    document.addEventListener('pointerdown', closePicker)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closePicker)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [detailMessageId, editHistoryMessageId, emojiPickerOpen, reactionPickerMessageId])

  const trackOutgoingMessage = useCallback((recipient, localId) => {
    const cutoff = Date.now() - 15000
    pendingOutgoingMessagesRef.current = [
      ...pendingOutgoingMessagesRef.current.filter((item) => item.createdAt > cutoff),
      { recipient, localId, createdAt: Date.now() },
    ]
  }, [])

  const removeRateLimitedMessage = useCallback((error) => {
    const failedRequest = error?.request || error?.data?.request
    const cutoff = Date.now() - 15000
    const pending = pendingOutgoingMessagesRef.current
      .filter((item) => item.createdAt > cutoff)
    const failedRecipient = failedRequest?.recipient || pending[pending.length - 1]?.recipient
    if (!failedRecipient || rateLimitHandledRecipientsRef.current.has(failedRecipient)) {
      pendingOutgoingMessagesRef.current = pending
      return
    }
    const failedLocalId = failedRequest?.localId
      || pending[pending.length - 1]?.localId
    rateLimitHandledRecipientsRef.current.add(failedRecipient)
    setRateLimitRemainingByUser((current) => ({ ...current, [failedRecipient]: 60 }))
    window.clearTimeout(rateLimitResetTimersRef.current.get(failedRecipient))
    rateLimitResetTimersRef.current.set(failedRecipient, window.setTimeout(() => {
      rateLimitHandledRecipientsRef.current.delete(failedRecipient)
      rateLimitResetTimersRef.current.delete(failedRecipient)
    }, 60000))
    pendingOutgoingMessagesRef.current = pending.filter((item) => item.recipient !== failedRecipient)
    if (!failedLocalId || !failedRecipient) return
    setMessagesByUser((current) => ({
      ...current,
      [failedRecipient]: (current[failedRecipient] || []).filter((message) => message.localId !== failedLocalId),
    }))
  }, [])

  const submitMessage = async (event) => {
    event.preventDefault()
    const content = messageDraft.trim()
    if (!content || !selectedUser || !user || rateLimitRemaining > 0) return
    window.clearTimeout(typingPublishTimersRef.current.get(selectedUser.username))
    typingPublishTimersRef.current.delete(selectedUser.username)
    typingLastSentRef.current.delete(selectedUser.username)
    sendTypingStatus(selectedUser.username, false)
    const localId = generateUUID()
    const optimisticMessage = {
      localId, sender: user.username, recipient: selectedUser.username, content,
      contentType: 'TEXT', messageType: 'CHAT', timestamp: new Date().toISOString(), status: 'SENDING',
    }
    setMessagesByUser((current) => ({
      ...current,
      [selectedUser.username]: upsertMessage(current[selectedUser.username] || [], optimisticMessage),
    }))
    setMessageDraft('')
    const sent = sendPrivateMessage({
      recipient: selectedUser.username, content, contentType: 'TEXT', messageType: 'CHAT', localId,
    })
    if (sent) trackOutgoingMessage(selectedUser.username, localId)
    if (!sent) {
      setMessagesByUser((current) => ({
        ...current,
        [selectedUser.username]: (current[selectedUser.username] || []).map((message) => (
          message.localId === localId ? { ...message, clientFailed: true } : message
        )),
      }))
      showToast(t('messageFailed'), 'error')
    } else {
      setMessagesByUser((current) => ({
        ...current,
        [selectedUser.username]: (current[selectedUser.username] || []).map((message) => (
          message.localId === localId ? { ...message, status: 'SENT' } : message
        )),
      }))
    }
  }

  const retryFailedMessage = (message) => {
    if (!message?.localId || !selectedUser || connectionState !== 'connected') return
    const previousLocalId = message.localId
    const retryLocalId = generateUUID()
    setMessagesByUser((current) => ({
      ...current,
      [selectedUser.username]: (current[selectedUser.username] || []).map((item) => (
        item.localId === previousLocalId
          ? { ...item, localId: retryLocalId, clientFailed: false, status: 'SENDING', failureAttempt: (item.failureAttempt || 0) + 1 }
          : item
      )),
    }))
    const sent = sendPrivateMessage({
      recipient: selectedUser.username,
      content: message.content || '',
      contentType: message.contentType || 'TEXT',
      messageType: 'CHAT',
      fileUrl: message.fileUrl || null,
      fileName: message.fileName || null,
      fileSize: message.fileSize || null,
      localId: retryLocalId,
    })
    if (sent) trackOutgoingMessage(selectedUser.username, retryLocalId)
    setMessagesByUser((current) => ({
      ...current,
      [selectedUser.username]: (current[selectedUser.username] || []).map((item) => (
        item.localId === retryLocalId
          ? { ...item, status: sent ? 'SENT' : 'SENDING', clientFailed: !sent }
          : item
      )),
    }))
    if (!sent) showToast(t('messageFailed'), 'error')
  }

  const handleMediaSelection = async (event) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file || !selectedUser || !user || rateLimitRemaining > 0) return

    const contentType = getMediaContentType(file)
    if (!contentType) {
      showToast(t('unsupportedMedia'), 'error')
      return
    }
    if (file.size > MAX_MEDIA_SIZE_BYTES) {
      showToast(t('mediaTooLarge'), 'error')
      return
    }
    if (connectionState !== 'connected') {
      showToast(t('socketError'), 'error')
      return
    }

    const targetUser = selectedUser
    const fieldName = contentType === 'IMAGE' ? 'image' : 'video'
    const endpoint = contentType === 'IMAGE' ? '/api/chat/image' : '/api/chat/video'
    const body = new FormData()
    body.append(fieldName, file)
    setUploadingMedia(true)

    try {
      const response = await apiRequest(endpoint, { method: 'POST', body })
      const fileUrl = typeof response?.data === 'string' ? response.data : ''
      if (!fileUrl) throw new Error(t('uploadFailed'))

      const localId = generateUUID()
      const mediaMessage = {
        localId,
        sender: user.username,
        recipient: targetUser.username,
        content: '',
        contentType,
        messageType: 'CHAT',
        fileUrl,
        fileName: file.name,
        fileSize: file.size,
        timestamp: new Date().toISOString(),
        status: 'SENDING',
      }
      setMessagesByUser((current) => ({
        ...current,
        [targetUser.username]: upsertMessage(current[targetUser.username] || [], mediaMessage),
      }))

      const sent = sendPrivateMessage({
        recipient: targetUser.username,
        content: '',
        contentType,
        messageType: 'CHAT',
        fileUrl,
        fileName: file.name,
        fileSize: file.size,
        localId,
      })
      if (sent) trackOutgoingMessage(targetUser.username, localId)
      setMessagesByUser((current) => ({
        ...current,
        [targetUser.username]: (current[targetUser.username] || []).map((message) => (
          message.localId === localId
            ? { ...message, status: sent ? 'SENT' : 'SENDING', clientFailed: !sent }
            : message
        )),
      }))
      if (!sent) showToast(t('messageFailed'), 'error')
    } catch (error) {
      showToast(getErrorMessage(error, t('uploadFailed')), 'error')
    } finally {
      setUploadingMedia(false)
    }
  }

  const handleDraftChange = (event) => {
    if (rateLimitRemaining > 0) return
    const nextDraft = event.target.value
    setMessageDraft(nextDraft)
    if (!selectedUser) return
    const recipient = selectedUser.username
    window.clearTimeout(typingPublishTimersRef.current.get(recipient))
    if (!nextDraft.trim()) {
      typingPublishTimersRef.current.delete(recipient)
      typingLastSentRef.current.delete(recipient)
      sendTypingStatus(recipient, false)
      return
    }
    const now = Date.now()
    const lastSent = typingLastSentRef.current.get(recipient) || 0
    if (now - lastSent >= 2000) {
      sendTypingStatus(recipient, true)
      typingLastSentRef.current.set(recipient, now)
    }
    const timeout = window.setTimeout(() => {
      typingPublishTimersRef.current.delete(recipient)
    }, 2200)
    typingPublishTimersRef.current.set(recipient, timeout)
  }

  const insertMessageEmoji = (emoji) => {
    const input = messageInputRef.current
    const start = input?.selectionStart ?? messageDraft.length
    const end = input?.selectionEnd ?? start
    const nextDraft = `${messageDraft.slice(0, start)}${emoji}${messageDraft.slice(end)}`
    const nextCursor = start + emoji.length
    setMessageDraft(nextDraft)
    setEmojiPickerOpen(false)
    window.requestAnimationFrame(() => {
      input?.focus()
      input?.setSelectionRange(nextCursor, nextCursor)
    })
  }

  const toggleReaction = async (message, reactionType) => {
    if (!message?.id || !selectedUser || !user || reactionSubmittingId === message.id) return
    const previousMessage = message
    const previousReaction = message.reactions?.[user.username] || null
    const nextReaction = previousReaction === reactionType ? null : reactionType
    const nextReactions = { ...(message.reactions || {}) }
    if (nextReaction) nextReactions[user.username] = nextReaction
    else delete nextReactions[user.username]
    const optimisticMessage = { ...message, reactions: nextReactions, reacted: Object.keys(nextReactions).length > 0 }

    setReactionPickerMessageId(null)
    setReactionSubmittingId(message.id)
    setMessagesByUser((current) => ({
      ...current,
      [selectedUser.username]: upsertMessage(current[selectedUser.username] || [], optimisticMessage),
    }))

    try {
      const response = await apiRequest('/api/messages/reaction', {
        method: 'POST',
        body: { messageId: message.id, recipient: selectedUser.username, reactionType: nextReaction },
      })
      const updatedMessage = response?.data || optimisticMessage
      setMessagesByUser((current) => ({
        ...current,
        [selectedUser.username]: upsertMessage(current[selectedUser.username] || [], updatedMessage),
      }))
      sendReactionControl(selectedUser.username, updatedMessage)
    } catch (error) {
      setMessagesByUser((current) => ({
        ...current,
        [selectedUser.username]: upsertMessage(current[selectedUser.username] || [], previousMessage),
      }))
      showToast(getErrorMessage(error, t('reactionFailed')), 'error')
    } finally {
      setReactionSubmittingId(null)
    }
  }

  const beginMessageEdit = (message) => {
    setEditingMessageId(message.id)
    setEditingMessageContent(message.content || '')
    setReactionPickerMessageId(null)
  }

  const saveMessageEdit = async (event, message) => {
    event.preventDefault()
    const newContent = editingMessageContent.trim()
    if (!newContent || !selectedUser || messageActionPending) return
    setMessageActionPending(true)
    try {
      const response = await apiRequest('/api/messages/edit', {
        method: 'PUT',
        body: {
          messageId: message.id,
          recipient: selectedUser.username,
          newContent,
        },
      })
      const updated = { ...response?.data, edited: true, isEdited: true }
      recordMessageEdit(message)
      setMessagesByUser((current) => ({
        ...current,
        [selectedUser.username]: upsertMessage(current[selectedUser.username] || [], updated),
      }))
      setEditingMessageId(null)
      setEditingMessageContent('')
      showToast(response?.message || t('messageEdited'))
    } catch (error) {
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
    } finally {
      setMessageActionPending(false)
    }
  }

  const revokeChatMessage = async () => {
    const message = revokeTargetMessage
    if (!message?.id || !selectedUser) return
    setMessageActionPending(true)
    try {
      const response = await apiRequest('/api/messages/revoke', {
        method: 'DELETE', body: { messageId: message.id, recipient: selectedUser.username },
      })
      const revoked = { ...message, content: '', fileUrl: null, fileName: null, deleted: true, isDeleted: true, reactions: null }
      setMessagesByUser((current) => ({
        ...current,
        [selectedUser.username]: upsertMessage(current[selectedUser.username] || [], revoked),
      }))
      setRevokeTargetMessage(null)
      showToast(response?.message || t('messageRevoked'))
    } catch (error) {
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
    } finally {
      setMessageActionPending(false)
    }
  }

  useEffect(() => {
    const rateLimitResetTimers = rateLimitResetTimersRef.current
    const typingPublishTimers = typingPublishTimersRef.current
    const typingLastSent = typingLastSentRef.current
    return () => {
      rateLimitResetTimers.forEach((timer) => window.clearTimeout(timer))
      typingPublishTimers.forEach((timer) => window.clearTimeout(timer))
      typingLastSent.clear()
    }
  }, [])

  return {
    messagesByUser,
    setMessagesByUser,
    conversationPages,
    setConversationPages,
    loadingOlderMessages,
    loadingConversation,
    activeMessages,
    rateLimitRemaining,
    messageDraft,
    setMessageDraft,
    emojiPickerOpen,
    setEmojiPickerOpen,
    uploadingMedia,
    reactionPickerMessageId,
    setReactionPickerMessageId,
    reactionSubmittingId,
    detailMessageId,
    setDetailMessageId,
    editHistoryMessageId,
    setEditHistoryMessageId,
    messageEditHistory,
    editingMessageId,
    setEditingMessageId,
    editingMessageContent,
    setEditingMessageContent,
    messageActionPending,
    revokeTargetMessage,
    setRevokeTargetMessage,
    messageStreamRef,
    messagesEndRef,
    mediaInputRef,
    messageInputRef,
    messagesByUserRef,
    initialLoadScrollRef,
    loadConversation,
    loadOlderConversation,
    handleMessageStreamScroll,
    submitMessage,
    retryFailedMessage,
    handleMediaSelection,
    handleDraftChange,
    insertMessageEmoji,
    toggleReaction,
    beginMessageEdit,
    saveMessageEdit,
    revokeChatMessage,
    recordMessageEdit,
    removeRateLimitedMessage,
  }
}

export default useConversationMessages
