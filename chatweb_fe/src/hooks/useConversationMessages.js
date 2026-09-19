import { useCallback, useEffect, useRef, useState } from 'react'
import { apiRequest, generateUUID, getErrorMessage } from '../services/apiClient.js'
import {
  getMediaContentType,
  isMessageDeleted,
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
  activeSection = 'chat',
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
  const [replyingToMessage, setReplyingToMessage] = useState(null)
  const [replyMessageCache, setReplyMessageCache] = useState({})
  const [highlightedMessageId, setHighlightedMessageId] = useState(null)

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
  const lastLoadedUsernameRef = useRef(null)
  const scrolledToBottomForUserRef = useRef(null)
  const replyCacheRef = useRef(new Map())
  const fetchingReplyIdsRef = useRef(new Set())
  const highlightTimeoutRef = useRef(null)

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

  const scrollToBottom = useCallback((instant = true) => {
    const stream = messageStreamRef.current
    if (!stream) return
    if (instant) {
      stream.scrollTop = stream.scrollHeight
    } else {
      stream.scrollTo({ top: stream.scrollHeight, behavior: 'smooth' })
    }
    window.requestAnimationFrame(() => {
      if (messageStreamRef.current) {
        messageStreamRef.current.scrollTop = messageStreamRef.current.scrollHeight
      }
    })
    window.setTimeout(() => {
      if (messageStreamRef.current) {
        messageStreamRef.current.scrollTop = messageStreamRef.current.scrollHeight
      }
    }, 60)
  }, [])

  const beginReply = useCallback((message) => {
    if (!message || !message.id || isMessageDeleted(message)) return
    setReplyingToMessage(message)
    window.requestAnimationFrame(() => {
      messageInputRef.current?.focus()
    })
  }, [])

  const cancelReply = useCallback(() => {
    setReplyingToMessage(null)
  }, [])

  useEffect(() => {
    if (replyingToMessage) {
      window.requestAnimationFrame(() => {
        messageInputRef.current?.focus()
      })
    }
  }, [replyingToMessage])

  useEffect(() => {
    setReplyingToMessage(null)
  }, [selectedUser?.username])

  const fetchReplyMessage = useCallback(async (replyToId) => {
    if (!replyToId) return null
    const cacheKey = String(replyToId)
    if (replyCacheRef.current.has(cacheKey)) {
      return replyCacheRef.current.get(cacheKey)
    }
    const targetUsername = selectedRef.current?.username
    const foundInActive = targetUsername
      ? messagesByUserRef.current[targetUsername]?.find((m) => String(m.id) === cacheKey)
      : null
    if (foundInActive) {
      replyCacheRef.current.set(cacheKey, foundInActive)
      setReplyMessageCache((prev) => ({ ...prev, [cacheKey]: foundInActive }))
      return foundInActive
    }
    if (fetchingReplyIdsRef.current.has(cacheKey)) {
      return null
    }
    fetchingReplyIdsRef.current.add(cacheKey)
    try {
      const response = await apiRequest(`/api/messages/${cacheKey}`)
      const fetched = response?.data || null
      if (fetched) {
        if (replyCacheRef.current.size >= 500) {
          const oldest = replyCacheRef.current.keys().next().value
          if (oldest) replyCacheRef.current.delete(oldest)
        }
        replyCacheRef.current.set(cacheKey, fetched)
        setReplyMessageCache((prev) => ({ ...prev, [cacheKey]: fetched }))
        return fetched
      } else {
        const placeholder = { id: cacheKey, notFound: true }
        replyCacheRef.current.set(cacheKey, placeholder)
        setReplyMessageCache((prev) => ({ ...prev, [cacheKey]: placeholder }))
        return placeholder
      }
    } catch {
      const placeholder = { id: cacheKey, notFound: true }
      replyCacheRef.current.set(cacheKey, placeholder)
      setReplyMessageCache((prev) => ({ ...prev, [cacheKey]: placeholder }))
      return placeholder
    } finally {
      fetchingReplyIdsRef.current.delete(cacheKey)
    }
  }, [])

  useEffect(() => {
    if (!activeMessages.length) return
    const existingIds = new Set(activeMessages.map((m) => m.id ? String(m.id) : null).filter(Boolean))
    const missingIds = activeMessages
      .map((m) => m.replyToId ? String(m.replyToId) : null)
      .filter((id) => (
        Boolean(id) &&
        !existingIds.has(id) &&
        !replyCacheRef.current.has(id) &&
        !fetchingReplyIdsRef.current.has(id)
      ))

    if (!missingIds.length) return
    const uniqueMissing = [...new Set(missingIds)]
    uniqueMissing.forEach((id) => {
      void fetchReplyMessage(id)
    })
  }, [activeMessages, fetchReplyMessage])

  useEffect(() => {
    if (!activeMessages.length) return
    activeMessages.forEach((msg) => {
      if (msg.id && replyCacheRef.current.has(String(msg.id))) {
        const key = String(msg.id)
        const cached = replyCacheRef.current.get(key)
        if (cached && (cached.content !== msg.content || isMessageDeleted(cached) !== isMessageDeleted(msg))) {
          replyCacheRef.current.set(key, msg)
          setReplyMessageCache((prev) => ({ ...prev, [key]: msg }))
        }
      }
    })
  }, [activeMessages])

  const scrollToQuotedMessage = useCallback((replyToId) => {
    if (!replyToId) return
    const targetId = String(replyToId)
    const el = document.getElementById(`chat-message-${targetId}`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      window.clearTimeout(highlightTimeoutRef.current)
      setHighlightedMessageId(targetId)
      highlightTimeoutRef.current = window.setTimeout(() => {
        setHighlightedMessageId(null)
      }, 1800)
    } else {
      showToast(t('originalMessageNotFound'))
    }
  }, [showToast, t])

  const loadConversation = useCallback(async (person, silent = false, cursor = null) => {
    if (!person || !user) return
    const isInitialLoad = !cursor
    if (isInitialLoad) initialLoadScrollRef.current = true
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
      if (isInitialLoad) initialLoadScrollRef.current = false
      if (!silent || cursor) showToast(getErrorMessage(error, t('errorGeneric')), 'error')
      return -1
    } finally {
      if (!silent) setLoadingConversation(false)
    }
  }, [blockedMessageIntervals, showToast, t, user])

  useEffect(() => {
    if (!selectedUser?.username || (activeSection && activeSection !== 'chat')) {
      lastLoadedUsernameRef.current = null
      scrolledToBottomForUserRef.current = null
      initialLoadScrollRef.current = false
      preserveScrollHeightRef.current = null
    }
  }, [activeSection, selectedUser?.username])

  useEffect(() => {
    const targetUsername = selectedUser?.username
    if (!targetUsername || !user?.username) return
    if (activeSection && activeSection !== 'chat') return

    if (lastLoadedUsernameRef.current === targetUsername) return
    lastLoadedUsernameRef.current = targetUsername

    preserveScrollHeightRef.current = null
    scrolledToBottomForUserRef.current = null
    initialLoadScrollRef.current = true

    const hasCached = Boolean(messagesByUserRef.current[targetUsername]?.length)
    void loadConversation(selectedUser, hasCached)
  }, [activeSection, loadConversation, selectedUser, selectedUser?.username, user?.username])

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
    if (scrolledToBottomForUserRef.current !== selectedRef.current?.username) return
    if (loadingConversation || loadingOlderMessagesRef.current || initialLoadScrollRef.current) return
    if (event.currentTarget.scrollTop <= 80) void loadOlderConversation()
  }, [loadingConversation, loadOlderConversation])

  useEffect(() => {
    const stream = messageStreamRef.current
    if (!stream) return
    const targetUsername = selectedUser?.username
    if (!targetUsername) return

    const preserved = preserveScrollHeightRef.current
    if (preserved) {
      stream.scrollTop = preserved.top + stream.scrollHeight - preserved.height
      preserveScrollHeightRef.current = null
      return
    }

    const needsScrollToBottom = scrolledToBottomForUserRef.current !== targetUsername || initialLoadScrollRef.current

    if (needsScrollToBottom) {
      const hasMessages = activeMessages.length > 0
      const isDoneLoading = !loadingConversation && conversationPages[targetUsername] !== undefined
      if (hasMessages || isDoneLoading) {
        stream.scrollTop = stream.scrollHeight
        window.requestAnimationFrame(() => {
          if (messageStreamRef.current) {
            messageStreamRef.current.scrollTop = messageStreamRef.current.scrollHeight
          }
          scrolledToBottomForUserRef.current = targetUsername
          initialLoadScrollRef.current = false
        })
        window.setTimeout(() => {
          if (messageStreamRef.current && selectedRef.current?.username === targetUsername) {
            messageStreamRef.current.scrollTop = messageStreamRef.current.scrollHeight
          }
        }, 60)
      }
      return
    }

    const isNearBottom = stream.scrollHeight - stream.scrollTop - stream.clientHeight < 250
    if (isNearBottom) {
      stream.scrollTo({ top: stream.scrollHeight, behavior: 'smooth' })
    }
  }, [activeMessages.length, conversationPages, loadingConversation, selectedUser?.username, selectedUserIsTyping])

  useEffect(() => {
    if (!reactionPickerMessageId && !detailMessageId && !editHistoryMessageId && !emojiPickerOpen && !replyingToMessage) return undefined
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
        if (emojiPickerOpen) {
          setEmojiPickerOpen(false)
          return
        }
        if (reactionPickerMessageId || detailMessageId || editHistoryMessageId) {
          setReactionPickerMessageId(null)
          setDetailMessageId(null)
          setEditHistoryMessageId(null)
          return
        }
        if (replyingToMessage) {
          setReplyingToMessage(null)
        }
      }
    }
    document.addEventListener('pointerdown', closePicker)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closePicker)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [detailMessageId, editHistoryMessageId, emojiPickerOpen, reactionPickerMessageId, replyingToMessage])

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
    const replyToId = replyingToMessage?.id ? String(replyingToMessage.id) : null
    if (replyingToMessage?.id) {
      const key = String(replyingToMessage.id)
      replyCacheRef.current.set(key, replyingToMessage)
      setReplyMessageCache((prev) => ({ ...prev, [key]: replyingToMessage }))
    }
    const optimisticMessage = {
      localId, sender: user.username, recipient: selectedUser.username, content,
      contentType: 'TEXT', messageType: 'CHAT', timestamp: new Date().toISOString(), status: 'SENDING',
      replyToId,
    }
    setMessagesByUser((current) => ({
      ...current,
      [selectedUser.username]: upsertMessage(current[selectedUser.username] || [], optimisticMessage),
    }))
    setMessageDraft('')
    setReplyingToMessage(null)
    scrollToBottom(true)
    const sent = sendPrivateMessage({
      recipient: selectedUser.username, content, contentType: 'TEXT', messageType: 'CHAT', localId,
      replyToId,
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
      replyToId: message.replyToId || null,
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
      const replyToId = replyingToMessage?.id ? String(replyingToMessage.id) : null
      if (replyingToMessage?.id) {
        const key = String(replyingToMessage.id)
        replyCacheRef.current.set(key, replyingToMessage)
        setReplyMessageCache((prev) => ({ ...prev, [key]: replyingToMessage }))
      }
      setReplyingToMessage(null)
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
        replyToId,
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
        replyToId,
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
      if (message.id) {
        const idKey = String(message.id)
        if (replyCacheRef.current.has(idKey)) {
          replyCacheRef.current.set(idKey, updated)
          setReplyMessageCache((prev) => ({ ...prev, [idKey]: updated }))
        }
      }
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
      if (message.id) {
        const idKey = String(message.id)
        if (replyCacheRef.current.has(idKey)) {
          replyCacheRef.current.set(idKey, revoked)
          setReplyMessageCache((prev) => ({ ...prev, [idKey]: revoked }))
        }
      }
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
      window.clearTimeout(highlightTimeoutRef.current)
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
    replyingToMessage,
    setReplyingToMessage,
    beginReply,
    cancelReply,
    replyMessageCache,
    fetchReplyMessage,
    highlightedMessageId,
    scrollToQuotedMessage,
    messageStreamRef,
    messagesEndRef,
    mediaInputRef,
    messageInputRef,
    messagesByUserRef,
    initialLoadScrollRef,
    scrollToBottom,
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
