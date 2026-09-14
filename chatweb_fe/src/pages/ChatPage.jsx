import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import ChatIcon from '../components/chat/ChatIcon.jsx'
import AppRail from '../components/chat/AppRail.jsx'
import { useAuth } from '../context/auth-context.js'
import { useLanguage } from '../context/language-context.js'
import { useChatSocket } from '../hooks/useChatSocket.js'
import { useNotificationSound } from '../hooks/useNotificationSound.js'
import { apiRequest, getErrorMessage } from '../services/apiClient.js'
import '../styles/chat.css'

const FRIEND_EVENT_TYPES = new Set([
  'FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'YOU_ACCEPTED', 'UNFRIENDED',
  'REQUEST_CANCELLED', 'REQUEST_REJECTED', 'USER_ONLINE', 'USER_OFFLINE',
])
const RECEIPT_PREFIX = '__CHATWEB_RECEIPT__:'
const REACTION_PREFIX = '__CHATWEB_REACTION__:'
const TYPING_PREFIX = '__CHATWEB_TYPING__:'
const STATUS_RANK = { SENDING: 0, SENT: 1, DELIVERED: 2, READ: 3 }
const REACTION_OPTIONS = [
  { type: 'LIKE', emoji: '👍' },
  { type: 'HEART', emoji: '❤️' },
  { type: 'LAUGH', emoji: '😂' },
  { type: 'SAD', emoji: '😢' },
  { type: 'ANGRY', emoji: '😡' },
]
const MESSAGE_EMOJI_OPTIONS = ['😀', '😂', '😍', '😊', '😎', '😢', '😡', '😮', '👍', '👏', '🙏', '🔥', '🎉', '❤️', '✨', '💯']
const REACTION_EMOJI = Object.fromEntries(REACTION_OPTIONS.map((reaction) => [reaction.type, reaction.emoji]))
const BLOCKED_MESSAGES_STORAGE_KEY = 'chatweb-blocked-message-intervals'
const EDIT_HISTORY_STORAGE_KEY = 'chatweb-message-edit-history'
const MAX_MEDIA_SIZE_BYTES = 20 * 1024 * 1024
const MESSAGE_PAGE_SIZE = 30
const MESSAGE_HISTORY_FALLBACK_SIZE = 1000
const MESSAGE_SEARCH_PAGE_SIZE = 30

function getMediaContentType(file) {
  if (file?.type?.startsWith('image/')) return 'IMAGE'
  if (file?.type?.startsWith('video/')) return 'VIDEO'
  return null
}

function initials(person) {
  const source = [person?.firstName, person?.lastName].filter(Boolean).join(' ') || person?.username || 'CW'
  return source.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

function displayName(person) {
  return [person?.firstName, person?.lastName].filter(Boolean).join(' ').trim() || person?.username || ''
}

function normalizeSearchValue(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('vi-VN')
    .trim()
}

function isPersonOnline(person) {
  return [person?.online, person?.isOnline].some((value) => (
    value === true || value === 1 || String(value).toLowerCase() === 'true'
  ))
}

function isAdminAccount(person) {
  const username = String(person?.username || '').toLocaleLowerCase('en-US')
  const name = displayName(person).toLocaleLowerCase('en-US')
  return username === 'admin' || name.includes('super admin')
}

function Avatar({ person, size = 'medium', showStatus = false }) {
  const [failed, setFailed] = useState(false)
  return (
    <span className={`cw-avatar cw-avatar--${size}`}>
      {person?.avatar && !failed
        ? <img src={person.avatar} alt="" onError={() => setFailed(true)} />
        : <span>{initials(person)}</span>}
      {showStatus && <i className={isPersonOnline(person) ? 'is-online' : ''} />}
    </span>
  )
}

function normalizeMessages(items) {
  return [...(items || [])]
    .filter(isDisplayableChatMessage)
    .sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
}

function isMessageDeleted(message) {
  const explicitFlag = [message?.deleted, message?.isDeleted, message?.is_deleted].some((value) => (
    value === true || value === 1 || String(value).toLocaleLowerCase('en-US') === 'true'
  ))
  if (explicitFlag) return true

  const isPersistedChatMessage = Boolean(message?.id)
    && (!message.messageType || String(message.messageType).toUpperCase() === 'CHAT')
  const hasNoPayload = !String(message?.content || '').trim() && !message?.fileUrl
  return isPersistedChatMessage && hasNoPayload
}

function isMessageEdited(message) {
  return [message?.edited, message?.isEdited, message?.is_edited].some((value) => (
    value === true || value === 1 || String(value).toLocaleLowerCase('en-US') === 'true'
  ))
}

function isDisplayableChatMessage(message) {
  if (!message || (message.messageType && message.messageType !== 'CHAT')) return false
  return Boolean(isMessageDeleted(message) || String(message.content || '').trim() || message.fileUrl)
}

function upsertMessage(list, incoming) {
  const matchIndex = list.findIndex((item) => (
    (incoming.id && item.id === incoming.id)
    || (incoming.localId && item.localId === incoming.localId)
  ))
  if (matchIndex < 0) return normalizeMessages([...list, incoming])
  const next = [...list]
  const currentStatus = next[matchIndex].status
  const incomingStatus = incoming.status
  const status = (STATUS_RANK[currentStatus] || 0) > (STATUS_RANK[incomingStatus] || 0)
    ? currentStatus
    : incomingStatus
  const mergedMessage = { ...next[matchIndex], ...incoming, status, clientFailed: false }
  if (isMessageEdited(next[matchIndex]) && !isMessageEdited(mergedMessage)) {
    mergedMessage.edited = true
    mergedMessage.isEdited = true
  }
  next[matchIndex] = mergedMessage
  return normalizeMessages(next)
}

function mergeMessageLists(current, incoming) {
  return (incoming || []).reduce((merged, message) => upsertMessage(merged, message), current || [])
}

function parseRealtimeReceipt(content) {
  if (!String(content || '').startsWith(RECEIPT_PREFIX)) return null
  try {
    const receipt = JSON.parse(String(content).slice(RECEIPT_PREFIX.length))
    return ['DELIVERED', 'READ'].includes(receipt?.status) ? receipt : null
  } catch {
    return null
  }
}

function parseRealtimeReaction(content) {
  if (!String(content || '').startsWith(REACTION_PREFIX)) return null
  try {
    const reaction = JSON.parse(String(content).slice(REACTION_PREFIX.length))
    return reaction?.message?.id ? reaction.message : null
  } catch {
    return null
  }
}

function parseRealtimeTyping(content) {
  if (!String(content || '').startsWith(TYPING_PREFIX)) return null
  try {
    const typing = JSON.parse(String(content).slice(TYPING_PREFIX.length))
    return typeof typing?.active === 'boolean' ? typing : null
  } catch {
    return null
  }
}

function summarizeReactions(reactions) {
  const counts = new Map()
  Object.values(reactions || {}).forEach((type) => counts.set(type, (counts.get(type) || 0) + 1))
  return [...counts.entries()].map(([type, count]) => ({ type, count, emoji: REACTION_EMOJI[type] || '✨' }))
}

function readBlockedMessageIntervals() {
  try {
    return JSON.parse(localStorage.getItem(BLOCKED_MESSAGES_STORAGE_KEY) || '{}')
  } catch {
    return {}
  }
}

function readMessageEditHistory() {
  try {
    const history = JSON.parse(localStorage.getItem(EDIT_HISTORY_STORAGE_KEY) || '{}')
    return history && typeof history === 'object' ? history : {}
  } catch {
    return {}
  }
}

function messageEditHistoryKey(username, messageId) {
  return `${String(username || '').toLocaleLowerCase('en-US')}:${messageId}`
}

function conversationPreferenceKey(username, peerUsername) {
  return `${String(username || '').toLocaleLowerCase('en-US')}:${String(peerUsername || '').toLocaleLowerCase('en-US')}`
}

function isIncomingMessageBlocked(preferences, username, peerUsername) {
  const intervals = preferences[conversationPreferenceKey(username, peerUsername)] || []
  return intervals.some((interval) => interval.to == null)
}

function wasMessageSentWhileBlocked(message, preferences, username, peerUsername) {
  if (message?.sender !== peerUsername) return false
  const timestamp = new Date(message.timestamp).getTime()
  const intervals = preferences[conversationPreferenceKey(username, peerUsername)] || []
  return intervals.some((interval) => timestamp >= interval.from && (interval.to == null || timestamp <= interval.to))
}

function promoteStatuses(messages, currentUsername, status, statusTimestamp) {
  const cutoff = new Date(statusTimestamp).getTime()
  return (messages || []).map((message) => {
    if (message.sender !== currentUsername || new Date(message.timestamp).getTime() > cutoff) return message
    return (STATUS_RANK[status] || 0) > (STATUS_RANK[message.status] || 0) ? { ...message, status } : message
  })
}

function formatTime(timestamp, language) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat(language === 'vi' ? 'vi-VN' : 'en-US', {
    hour: '2-digit', minute: '2-digit',
  }).format(date)
}

function formatDateTime(timestamp, language) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat(language === 'vi' ? 'vi-VN' : 'en-US', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date)
}

function initialChatSection() {
  const section = new URLSearchParams(window.location.search).get('section')
  return ['chat', 'friends', 'notifications'].includes(section) ? section : 'chat'
}

function ChatPage() {
  const { user } = useAuth()
  const { language, t } = useLanguage()
  const playNotificationSound = useNotificationSound()
  const playInboxSound = useNotificationSound('inbox')
  const [friends, setFriends] = useState([])
  const [friendRequests, setFriendRequests] = useState([])
  const [sentRequests, setSentRequests] = useState([])
  const [blockedUsers, setBlockedUsers] = useState([])
  const [selectedUser, setSelectedUser] = useState(null)
  const [messagesByUser, setMessagesByUser] = useState({})
  const [conversationPages, setConversationPages] = useState({})
  const [loadingOlderMessages, setLoadingOlderMessages] = useState(false)
  const [unreadCounts, setUnreadCounts] = useState({})
  const [worldMessages, setWorldMessages] = useState([])
  const [worldCursor, setWorldCursor] = useState(null)
  const [worldHasMore, setWorldHasMore] = useState(false)
  const [worldNotifications, setWorldNotifications] = useState([])
  const [activeSection, setActiveSection] = useState(initialChatSection)
  const [conversationQuery, setConversationQuery] = useState('')
  const [worldOpen, setWorldOpen] = useState(() => new URLSearchParams(window.location.search).get('world') === '1')
  const [searchQuery, setSearchQuery] = useState('')
  const [searchType, setSearchType] = useState('username')
  const [searchResults, setSearchResults] = useState([])
  const [suggestions, setSuggestions] = useState([])
  const [searching, setSearching] = useState(false)
  const [loadingSuggestions, setLoadingSuggestions] = useState(false)
  const [loadingConversation, setLoadingConversation] = useState(false)
  const [messageDraft, setMessageDraft] = useState('')
  const [emojiPickerOpen, setEmojiPickerOpen] = useState(false)
  const [uploadingMedia, setUploadingMedia] = useState(false)
  const [worldDraft, setWorldDraft] = useState('')
  const [typingUsers, setTypingUsers] = useState({})
  const [rateLimitRemainingByUser, setRateLimitRemainingByUser] = useState({})
  const [toast, setToast] = useState(null)
  const [reactionPickerMessageId, setReactionPickerMessageId] = useState(null)
  const [reactionSubmittingId, setReactionSubmittingId] = useState(null)
  const [detailMessageId, setDetailMessageId] = useState(null)
  const [editHistoryMessageId, setEditHistoryMessageId] = useState(null)
  const [messageContextMenu, setMessageContextMenu] = useState(null)
  const [messageEditHistory, setMessageEditHistory] = useState(readMessageEditHistory)
  const [conversationMenuOpen, setConversationMenuOpen] = useState(false)
  const [messageSearchOpen, setMessageSearchOpen] = useState(false)
  const [messageSearchQuery, setMessageSearchQuery] = useState('')
  const [messageSearchResults, setMessageSearchResults] = useState([])
  const [messageSearchHasMore, setMessageSearchHasMore] = useState(false)
  const [messageSearchCursor, setMessageSearchCursor] = useState(null)
  const [messageSearchLoading, setMessageSearchLoading] = useState(false)
  const [searchTargetMessageId, setSearchTargetMessageId] = useState(null)
  const [confirmConversationAction, setConfirmConversationAction] = useState(null)
  const [reportDialogOpen, setReportDialogOpen] = useState(false)
  const [reportReason, setReportReason] = useState('SPAM')
  const [reportDetails, setReportDetails] = useState('')
  const [conversationActionPending, setConversationActionPending] = useState(false)
  const [editingMessageId, setEditingMessageId] = useState(null)
  const [editingMessageContent, setEditingMessageContent] = useState('')
  const [messageActionPending, setMessageActionPending] = useState(false)
  const [revokeTargetMessage, setRevokeTargetMessage] = useState(null)
  const [blockedMessageIntervals, setBlockedMessageIntervals] = useState(readBlockedMessageIntervals)
  const selectedRef = useRef(null)
  const activeSectionRef = useRef(activeSection)
  const messagesByUserRef = useRef({})
  const messagesEndRef = useRef(null)
  const messageStreamRef = useRef(null)
  const preserveScrollHeightRef = useRef(null)
  const loadingOlderMessagesRef = useRef(false)
  const pendingOutgoingMessagesRef = useRef([])
  const rateLimitHandledRecipientsRef = useRef(new Set())
  const rateLimitResetTimersRef = useRef(new Map())
  const mediaInputRef = useRef(null)
  const messageInputRef = useRef(null)
  const typingTimeoutsRef = useRef(new Map())
  const typingPublishTimersRef = useRef(new Map())
  const typingLastSentRef = useRef(new Map())
  const readAckTimersRef = useRef(new Map())
  const socketSenderRef = useRef(null)
  const friendSyncTimersRef = useRef([])
  const conversationMenuRef = useRef(null)
  const searchHighlightTimerRef = useRef(null)

  const permissions = useMemo(() => new Set(user?.permissions || []), [user?.permissions])
  const isAdmin = String(user?.role || '').toUpperCase().includes('ADMIN') || permissions.has('ADMIN_SEND-MESSAGE')
  const currentUsernameKey = String(user?.username || '').trim().toLocaleLowerCase('en-US')
  const friendNames = useMemo(() => new Set(friends.map((friend) => friend.username)), [friends])
  const sentNames = useMemo(() => new Set(sentRequests.map((person) => person.username)), [sentRequests])
  const blockedNames = useMemo(() => new Set(blockedUsers.map((person) => person.username)), [blockedUsers])
  const incomingRequestNames = useMemo(() => new Set(friendRequests.map((person) => person.username)), [friendRequests])
  const totalUnreadMessages = useMemo(() => friends.reduce((total, friend) => {
    const unreadCount = Number(unreadCounts[friend.username])
    return total + (Number.isFinite(unreadCount) && unreadCount > 0 ? unreadCount : 0)
  }, 0), [friends, unreadCounts])
  const filteredFriends = useMemo(() => {
    const query = normalizeSearchValue(conversationQuery)
    if (!query) return friends
    return friends.filter((friend) => normalizeSearchValue(
      `${displayName(friend)} ${friend.username || ''}`,
    ).includes(query))
  }, [conversationQuery, friends])
  const visibleSuggestions = useMemo(() => suggestions
    .filter((person) => String(person.username || '').trim().toLocaleLowerCase('en-US') !== currentUsernameKey
      && !isAdminAccount(person)
      && !friendNames.has(person.username)
      && !sentNames.has(person.username)
      && !blockedNames.has(person.username)
      && !incomingRequestNames.has(person.username))
    .sort((left, right) => Number(isPersonOnline(right)) - Number(isPersonOnline(left)))
    .slice(0, 8), [blockedNames, currentUsernameKey, friendNames, incomingRequestNames, sentNames, suggestions])
  const activeMessages = selectedUser ? (messagesByUser[selectedUser.username] || []) : []
  const selectedUsername = selectedUser?.username || ''
  const selectedUserIsTyping = Boolean(typingUsers[selectedUsername])
  const rateLimitRemaining = rateLimitRemainingByUser[selectedUsername] || 0
  const latestWorldMessage = worldMessages.length ? worldMessages[worldMessages.length - 1] : null
  const selectedConversationKey = conversationPreferenceKey(user?.username, selectedUser?.username)
  const selectedConversationBlocked = isIncomingMessageBlocked(blockedMessageIntervals, user?.username, selectedUser?.username)

  useEffect(() => {
    selectedRef.current = selectedUser
  }, [selectedUser])

  useEffect(() => {
    activeSectionRef.current = activeSection
  }, [activeSection])

  useEffect(() => {
    messagesByUserRef.current = messagesByUser
  }, [messagesByUser])

  const changeActiveSection = useCallback((section) => {
    activeSectionRef.current = section
    setActiveSection(section)
  }, [])

  useEffect(() => {
    if (!conversationMenuOpen) return undefined
    const closeMenu = (event) => {
      if (!conversationMenuRef.current?.contains(event.target)) {
        setConversationMenuOpen(false)
      }
    }
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') {
        setConversationMenuOpen(false)
      }
    }
    document.addEventListener('pointerdown', closeMenu)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeMenu)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [conversationMenuOpen])

  const showToast = useCallback((message, tone = 'success') => {
    setToast({ message, tone, id: Date.now() })
  }, [])

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

  useEffect(() => {
    if (!toast) return undefined
    const timeout = window.setTimeout(() => setToast(null), 3600)
    return () => window.clearTimeout(timeout)
  }, [toast])

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

  const loadConnections = useCallback(async () => {
    const [friendsResult, requestsResult, sentResult, blockedResult] = await Promise.allSettled([
      apiRequest('/api/friends?size=100'),
      apiRequest('/api/friends/requests?size=100'),
      apiRequest('/api/friends/sent?size=100'),
      apiRequest('/api/friends/blocked?size=100'),
    ])
    if (friendsResult.status === 'fulfilled') setFriends(friendsResult.value?.data?.content || [])
    if (requestsResult.status === 'fulfilled') setFriendRequests(requestsResult.value?.data?.content || [])
    if (sentResult.status === 'fulfilled') setSentRequests(sentResult.value?.data?.content || [])
    if (blockedResult.status === 'fulfilled') setBlockedUsers(blockedResult.value?.data?.content || [])
  }, [])

  const scheduleConnectionSync = useCallback(() => {
    friendSyncTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    friendSyncTimersRef.current = [window.setTimeout(() => {
      void loadConnections()
    }, 250)]
  }, [loadConnections])

  const loadUnreadCounts = useCallback(async () => {
    try {
      const response = await apiRequest('/api/messages/unread-counts')
      setUnreadCounts(response?.data?.unreadCounts || {})
    } catch {
      // Realtime still works if unread reconciliation is temporarily unavailable.
    }
  }, [])

  const loadSuggestions = useCallback(async () => {
    setLoadingSuggestions(true)
    try {
      const response = await apiRequest('/api/search/users?size=24&sortDir=asc')
      setSuggestions(response?.data?.content || [])
    } catch (error) {
      showToast(getErrorMessage(error, t('errorGeneric')), 'error')
    } finally {
      setLoadingSuggestions(false)
    }
  }, [showToast, t])

  const loadWorldHistory = useCallback(async (cursor = null, appendOlder = false) => {
    try {
      const query = new URLSearchParams({ size: '30' })
      if (cursor) query.set('cursor', cursor)
      const response = await apiRequest(`/api/systems/message?${query}`)
      const history = normalizeMessages(response?.data?.content || [])
      setWorldMessages((current) => {
        const merged = appendOlder ? [...history, ...current] : [...current, ...history]
        const unique = new Map(merged.map((item) => [`${item.sender}-${item.timestamp}-${item.content}`, item]))
        return normalizeMessages([...unique.values()])
      })
      setWorldCursor(response?.data?.nextCursor || null)
      setWorldHasMore(Boolean(response?.data?.hasMore))
    } catch (error) {
      showToast(getErrorMessage(error, t('errorGeneric')), 'error')
    }
  }, [showToast, t])

  const loadConversation = useCallback(async (person, silent = false, cursor = null) => {
    if (!person || !user) return
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
    if (event.currentTarget.scrollTop <= 80) void loadOlderConversation()
  }, [loadOlderConversation])

  const searchConversationMessages = useCallback(async (keyword, signal = undefined, cursor = null, append = false) => {
    const person = selectedRef.current
    const normalizedKeyword = keyword.trim()
    if (!person || !user || !normalizedKeyword) return

    setMessageSearchLoading(true)
    try {
      const query = new URLSearchParams({
        user2: person.username,
        keyword: normalizedKeyword,
        size: String(MESSAGE_SEARCH_PAGE_SIZE),
      })
      if (cursor) query.set('cursor', cursor)
      const response = await apiRequest(`/api/messages/search?${query}`, signal ? { signal } : {})
      if (signal?.aborted || selectedRef.current?.username !== person.username) return
      const pageMessages = response?.data?.content || []
      if (selectedRef.current?.username !== person.username) return
      const results = pageMessages.filter((message) => (
        isDisplayableChatMessage(message)
        && !isMessageDeleted(message)
        && !wasMessageSentWhileBlocked(message, blockedMessageIntervals, user.username, person.username)
      )).sort((left, right) => new Date(right.timestamp) - new Date(left.timestamp))
      setMessageSearchResults((current) => {
        if (!append) return results
        const unique = new Map([...current, ...results].map((message) => [message.id, message]))
        return [...unique.values()].sort((left, right) => new Date(right.timestamp) - new Date(left.timestamp))
      })
      const nextCursor = response?.data?.nextCursor || null
      setMessageSearchCursor(nextCursor)
      setMessageSearchHasMore(Boolean(response?.data?.hasMore && nextCursor))
    } catch (error) {
      if (error.name !== 'AbortError') showToast(getErrorMessage(error, t('messageSearchFailed')), 'error')
    } finally {
      if (!signal?.aborted) setMessageSearchLoading(false)
    }
  }, [blockedMessageIntervals, showToast, t, user])

  const markAsRead = useCallback((sender) => {
    if (!sender) return
    setUnreadCounts((current) => ({ ...current, [sender]: 0 }))
    window.clearTimeout(readAckTimersRef.current.get(sender))
    const timer = window.setTimeout(async () => {
      readAckTimersRef.current.delete(sender)
      try {
        await apiRequest('/api/messages/mark-as-read', { method: 'POST', body: { sender } })
      } catch {
        void loadUnreadCounts()
      }
    }, 220)
    readAckTimersRef.current.set(sender, timer)
  }, [loadUnreadCounts])

  const sendRealtimeReceipt = useCallback((recipient, status, sourceMessage = null) => {
    if (!recipient || !socketSenderRef.current) return false
    return socketSenderRef.current({
      recipient,
      content: `${RECEIPT_PREFIX}${JSON.stringify({
        status,
        statusTimestamp: new Date().toISOString(),
        messageId: sourceMessage?.id || null,
        localId: sourceMessage?.localId || null,
      })}`,
      contentType: 'TEXT',
      messageType: 'TYPING',
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

  const isActivelyViewingConversation = useCallback((username) => (
    activeSectionRef.current === 'chat'
    && selectedRef.current?.username === username
    && document.visibilityState === 'visible'
    && document.hasFocus()
  ), [])

  const updatePeerPresence = useCallback((username, online) => {
    const normalizedUsername = String(username || '').toLocaleLowerCase('en-US')
    if (!normalizedUsername) return
    setFriends((current) => current.map((friend) => (
      friend.username?.toLocaleLowerCase('en-US') === normalizedUsername
        ? { ...friend, online, isOnline: online }
        : friend
    )))
    setSelectedUser((current) => current?.username?.toLocaleLowerCase('en-US') === normalizedUsername
      ? { ...current, online, isOnline: online }
      : current)
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

  const handleIncomingMessage = useCallback(async (message) => {
    if (!message?.sender || !message?.recipient || !user) return
    const peer = message.sender === user.username ? message.recipient : message.sender

    if (message.messageType === 'TYPING') {
      const receipt = parseRealtimeReceipt(message.content)
      if (receipt && message.sender !== user.username) {
        setMessagesByUser((current) => ({
          ...current,
          [message.sender]: promoteStatuses(
            (current[message.sender] || []).map((existingMessage) => (
              receipt.messageId && receipt.localId && existingMessage.localId === receipt.localId
                ? { ...existingMessage, id: receipt.messageId }
                : existingMessage
            )), user.username, receipt.status, receipt.statusTimestamp,
          ),
        }))
        return
      }
      const reactionMessage = parseRealtimeReaction(message.content)
      if (reactionMessage && message.sender !== user.username) {
        const reactionPeer = reactionMessage.sender === user.username
          ? reactionMessage.recipient
          : reactionMessage.sender
        setMessagesByUser((current) => ({
          ...current,
          [reactionPeer]: upsertMessage(current[reactionPeer] || [], reactionMessage),
        }))
        return
      }
      if (message.sender !== user.username) {
        const typing = parseRealtimeTyping(message.content)
        setTypingUsers((current) => ({ ...current, [message.sender]: typing?.active !== false }))
        window.clearTimeout(typingTimeoutsRef.current.get(message.sender))
        const timeout = window.setTimeout(() => {
          setTypingUsers((current) => ({ ...current, [message.sender]: false }))
        }, typing?.active === false ? 0 : 3500)
        typingTimeoutsRef.current.set(message.sender, timeout)
      }
      return
    }

    const displayMessage = message
    if (!isDisplayableChatMessage(displayMessage)) return
    if (displayMessage.sender !== user.username) updatePeerPresence(displayMessage.sender, true)
    if (displayMessage.sender !== user.username
      && isIncomingMessageBlocked(blockedMessageIntervals, user.username, peer)) return

    setMessagesByUser((current) => ({
      ...current,
      [peer]: upsertMessage(current[peer] || [], displayMessage),
    }))

    if (displayMessage.sender !== user.username) {
      playInboxSound()
      const isActivelyReading = isActivelyViewingConversation(peer)
      sendRealtimeReceipt(peer, isActivelyReading ? 'READ' : 'DELIVERED', displayMessage)
      if (isActivelyReading) markAsRead(peer)
      else setUnreadCounts((current) => ({ ...current, [peer]: (current[peer] || 0) + 1 }))
    }
  }, [blockedMessageIntervals, isActivelyViewingConversation, markAsRead, playInboxSound, sendRealtimeReceipt, updatePeerPresence, user])

  const handleNotification = useCallback(async (notification) => {
    if (!notification?.type) return
    const isEditNotification = ['EDIT_MESSAGE', 'MESSAGE_EDITED'].includes(notification.type)
    const isRevokeNotification = ['REVOKE_MESSAGE', 'MESSAGE_REVOKED'].includes(notification.type)
    const isReactionNotification = ['REACT_MESSAGE', 'MESSAGE_REACTED'].includes(notification.type)
    const isMessageUpdate = isEditNotification || isRevokeNotification
      || isReactionNotification || notification.type === 'STATUS_MESSAGE'
    const notificationPeer = notification.data?.sender === user?.username
      ? notification.data?.recipient
      : notification.data?.sender || notification.data?.reader || notification.relatedUsername
    const isBlockedMessageUpdate = isMessageUpdate
      && isIncomingMessageBlocked(blockedMessageIntervals, user?.username, notificationPeer)
    if (isBlockedMessageUpdate) return
    if (notification.type === 'FRIEND_REQUEST') {
      playNotificationSound()
      if (notification.message) showToast(notification.message)
    }
    if (FRIEND_EVENT_TYPES.has(notification.type)
      && !['USER_ONLINE', 'USER_OFFLINE'].includes(notification.type)) scheduleConnectionSync()

    if (['USER_ONLINE', 'USER_OFFLINE'].includes(notification.type)) {
      const online = notification.type === 'USER_ONLINE'
      updatePeerPresence(notification.relatedUsername, online)
    }

    if (notification.type === 'STATUS_MESSAGE' && notification.data && user) {
      const { reader, readTimestamp, status, statusTimestamp } = notification.data
      const nextStatus = status || 'READ'
      const nextTimestamp = statusTimestamp || readTimestamp
      setMessagesByUser((current) => ({
        ...current,
        [reader]: promoteStatuses(current[reader], user.username, nextStatus, nextTimestamp),
      }))
    }

    if ((isEditNotification || isRevokeNotification || isReactionNotification) && notification.data && user) {
      const data = isEditNotification
        ? { ...notification.data, edited: true, isEdited: true }
        : isRevokeNotification
          ? {
              ...notification.data,
              content: '',
              fileUrl: null,
              fileName: null,
              fileSize: null,
              reactions: null,
              deleted: true,
              isDeleted: true,
            }
          : notification.data
      const peer = data.sender === user.username ? data.recipient : data.sender
      if (isEditNotification) {
        const previousMessage = (messagesByUserRef.current[peer] || []).find((message) => message.id === data.id)
        if (previousMessage && previousMessage.content !== data.content) recordMessageEdit(previousMessage)
      }
      setMessagesByUser((current) => ({ ...current, [peer]: upsertMessage(current[peer] || [], data) }))
    }
  }, [blockedMessageIntervals, playNotificationSound, recordMessageEdit, scheduleConnectionSync, showToast, updatePeerPresence, user])

  const handleWorldMessage = useCallback((message) => {
    if (!String(message?.content || '').trim()) return
    setWorldNotifications((current) => [
      { ...message, receivedAt: message.timestamp || new Date().toISOString() },
      ...current,
    ].slice(0, 50))
    if (String(message.sender || '').toLocaleLowerCase('en-US') !== currentUsernameKey) playNotificationSound()
    showToast(message.content)
    setWorldMessages((current) => normalizeMessages([...current, message]))
  }, [currentUsernameKey, playNotificationSound, showToast])

  const handleSocketError = useCallback((error) => {
    const failedRequest = error?.request || error?.data?.request
    const errorCode = String(error?.errorCode || error?.code || error?.status || '').toUpperCase()
    if (errorCode === 'RATE_LIMITED' || errorCode === '429') {
      removeRateLimitedMessage(error)
      showToast(getErrorMessage(error, t('socketError')), 'error')
      return
    }
    if (failedRequest?.localId && failedRequest?.recipient) {
      setMessagesByUser((current) => ({
        ...current,
        [failedRequest.recipient]: (current[failedRequest.recipient] || []).map((message) => (
          message.localId === failedRequest.localId
            ? { ...message, clientFailed: true, status: 'FAILED', failureAttempt: (message.failureAttempt || 0) + 1 }
            : message
        )),
      }))
    }
    showToast(getErrorMessage(error, t('socketError')), 'error')
  }, [removeRateLimitedMessage, showToast, t])

  const handleSocketConnected = useCallback(() => {
    if (selectedRef.current && activeSectionRef.current === 'chat') {
      void loadConversation(selectedRef.current, true)
      const blocked = isIncomingMessageBlocked(
        blockedMessageIntervals, user?.username, selectedRef.current.username,
      )
      if (!blocked && isActivelyViewingConversation(selectedRef.current.username)) {
        markAsRead(selectedRef.current.username)
        sendRealtimeReceipt(selectedRef.current.username, 'READ')
      }
    }
  }, [blockedMessageIntervals, isActivelyViewingConversation, loadConversation, markAsRead, sendRealtimeReceipt, user?.username])

  const { connectionState, sendPrivateMessage, sendWorldMessage } = useChatSocket({
    enabled: Boolean(user), language, subscribeToWorld: true,
    onMessage: handleIncomingMessage, onNotification: handleNotification,
    onWorldMessage: handleWorldMessage, onError: handleSocketError, onConnected: handleSocketConnected,
  })
  const currentUserWithPresence = { ...user, isOnline: connectionState === 'connected' }

  useEffect(() => {
    socketSenderRef.current = sendPrivateMessage
  }, [sendPrivateMessage])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect -- initial data hydration synchronizes external APIs.
    void loadConnections()
    void loadUnreadCounts()
    void loadWorldHistory()
  }, [loadConnections, loadUnreadCounts, loadWorldHistory])

  useEffect(() => {
    if (activeSection !== 'friends' || searchQuery.trim() || suggestions.length) return
    // oxlint-disable-next-line react/set-state-in-effect -- opening the discovery panel hydrates remote suggestions.
    void loadSuggestions()
  }, [activeSection, loadSuggestions, searchQuery, suggestions.length])

  useEffect(() => {
    const currentSelection = selectedRef.current
    if (activeSection !== 'chat' || !currentSelection || currentSelection.username !== selectedUsername) return
    // oxlint-disable-next-line react/set-state-in-effect -- switching conversations synchronizes external history.
    void loadConversation(currentSelection)
    if (!selectedConversationBlocked && isActivelyViewingConversation(selectedUsername)) {
      markAsRead(selectedUsername)
      sendRealtimeReceipt(selectedUsername, 'READ')
    }
  }, [activeSection, isActivelyViewingConversation, loadConversation, markAsRead, selectedConversationBlocked, selectedUsername, sendRealtimeReceipt])

  useEffect(() => {
    const markVisibleConversationAsRead = () => {
      const selected = selectedRef.current
      if (!selected || !isActivelyViewingConversation(selected.username)) return
      if (isIncomingMessageBlocked(blockedMessageIntervals, user?.username, selected.username)) return
      if (Number(unreadCounts[selected.username] || 0) <= 0) return
      markAsRead(selected.username)
      sendRealtimeReceipt(selected.username, 'READ')
    }
    window.addEventListener('focus', markVisibleConversationAsRead)
    document.addEventListener('visibilitychange', markVisibleConversationAsRead)
    return () => {
      window.removeEventListener('focus', markVisibleConversationAsRead)
      document.removeEventListener('visibilitychange', markVisibleConversationAsRead)
    }
  }, [blockedMessageIntervals, isActivelyViewingConversation, markAsRead, sendRealtimeReceipt, unreadCounts, user?.username])

  useEffect(() => () => {
    window.clearTimeout(searchHighlightTimerRef.current)
    friendSyncTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    readAckTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    typingTimeoutsRef.current.forEach((timer) => window.clearTimeout(timer))
    typingPublishTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    typingLastSentRef.current.clear()
    rateLimitResetTimersRef.current.forEach((timer) => window.clearTimeout(timer))
  }, [])

  useEffect(() => {
    const preserved = preserveScrollHeightRef.current
    if (preserved && messageStreamRef.current) {
      messageStreamRef.current.scrollTop = preserved.top
        + messageStreamRef.current.scrollHeight - preserved.height
      preserveScrollHeightRef.current = null
      return
    }
    const stream = messageStreamRef.current
    if (stream) {
      stream.scrollTo({ top: stream.scrollHeight, behavior: 'smooth' })
    }
    if (messageStreamRef.current?.scrollHeight <= messageStreamRef.current?.clientHeight
      && conversationPages[selectedUser?.username]?.hasMore) {
      void loadOlderConversation()
    }
  }, [activeMessages.length, conversationPages, loadOlderConversation, selectedUser?.username, selectedUserIsTyping])

  useEffect(() => {
    const query = searchQuery.trim()
    if (activeSection !== 'friends' || !query) {
      // oxlint-disable-next-line react/set-state-in-effect -- reset remote search state when its input closes.
      setSearchResults([])
      setSearching(false)
      return undefined
    }

    const controller = new AbortController()
    const timeout = window.setTimeout(async () => {
      setSearching(true)
      try {
        const response = await apiRequest(`/api/search/users?keyword=${encodeURIComponent(query)}&size=30`, { signal: controller.signal })
        const normalizedQuery = query.toLocaleLowerCase(language)
        const filtered = (response?.data?.content || []).filter((person) => {
          if (String(person.username || '').trim().toLocaleLowerCase('en-US') === currentUsernameKey) return false
          if (isAdminAccount(person)) return false
          if (searchType === 'username') return person.username?.toLocaleLowerCase(language).includes(normalizedQuery)
          return displayName(person).toLocaleLowerCase(language).includes(normalizedQuery)
        })
        setSearchResults(filtered)
      } catch (error) {
        if (error.name !== 'AbortError') showToast(getErrorMessage(error, t('errorGeneric')), 'error')
      } finally {
        setSearching(false)
      }
    }, 300)

    return () => {
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [activeSection, currentUsernameKey, language, searchQuery, searchType, showToast, t])

  useEffect(() => {
    const keyword = messageSearchQuery.trim()
    if (!messageSearchOpen || !keyword || !selectedUser?.username) {
      // oxlint-disable-next-line react/set-state-in-effect -- reset search results when the drawer closes or query clears.
      setMessageSearchResults([])
      setMessageSearchHasMore(false)
      setMessageSearchCursor(null)
      setMessageSearchLoading(false)
      return undefined
    }

    const controller = new AbortController()
    // oxlint-disable-next-line react/set-state-in-effect -- clear stale results before the debounced request starts.
    setMessageSearchResults([])
    setMessageSearchHasMore(false)
    setMessageSearchCursor(null)
    setMessageSearchLoading(true)
    const timeout = window.setTimeout(() => {
      void searchConversationMessages(keyword, controller.signal)
    }, 300)
    return () => {
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [messageSearchOpen, messageSearchQuery, searchConversationMessages, selectedUser?.username])

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

  useEffect(() => {
    if (!messageContextMenu) return undefined
    const closeMenu = (event) => {
      if (!event.target.closest('.message-context-menu')) setMessageContextMenu(null)
    }
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setMessageContextMenu(null)
    }
    document.addEventListener('pointerdown', closeMenu)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeMenu)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [messageContextMenu])

  const selectFriend = (friend) => {
    setReactionPickerMessageId(null)
    setDetailMessageId(null)
    setEditHistoryMessageId(null)
    setMessageContextMenu(null)
    selectedRef.current = friend
    setSelectedUser(friend)
    changeActiveSection('chat')
    setWorldOpen(false)
  }

  const addFriend = async (person) => {
    if (String(person?.username || '').trim().toLocaleLowerCase('en-US') === currentUsernameKey) {
      setSuggestions((current) => current.filter((candidate) => candidate.username !== person.username))
      showToast(t('cannotFriendSelf'), 'error')
      return
    }
    try {
      const response = await apiRequest('/api/friends/request', { method: 'POST', body: { targetUsername: person.username } })
      setSentRequests((current) => [...current, person])
      showToast(response?.message || t('requested'))
    } catch (error) {
      showToast(getErrorMessage(error, t('errorGeneric')), 'error')
    }
  }

  const unblockServerUser = async (person) => {
    try {
      const response = await apiRequest(`/api/friends/unblock/${encodeURIComponent(person.username)}`, { method: 'POST' })
      setBlockedUsers((current) => current.filter((blockedUser) => blockedUser.username !== person.username))
      const preferenceKey = conversationPreferenceKey(user?.username, person.username)
      const intervals = [...(blockedMessageIntervals[preferenceKey] || [])]
      const lastInterval = intervals[intervals.length - 1]
      // oxlint-disable-next-line react/purity -- timestamp is captured only from an explicit user action.
      if (lastInterval?.to == null) lastInterval.to = Date.now()
      saveBlockedMessageIntervals({ ...blockedMessageIntervals, [preferenceKey]: intervals })
      scheduleConnectionSync()
      showToast(response?.message || t('unblockedUserSuccess'))
    } catch (error) {
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
    }
  }

  const acceptFriend = async (person) => {
    try {
      const response = await apiRequest('/api/friends/accept', { method: 'POST', body: { targetUsername: person.username } })
      showToast(response?.message || t('accept'))
      await loadConnections()
      selectFriend(person)
    } catch (error) {
      showToast(getErrorMessage(error, t('errorGeneric')), 'error')
    }
  }

  const removeFriendRelation = async (person, successKey) => {
    try {
      const response = await apiRequest(`/api/friends/${encodeURIComponent(person.username)}`, { method: 'DELETE' })
      await loadConnections()
      showToast(response?.message || t(successKey))
    } catch (error) {
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
    }
  }

  const submitMessage = async (event) => {
    event.preventDefault()
    const content = messageDraft.trim()
    if (!content || !selectedUser || !user || rateLimitRemaining > 0) return
    window.clearTimeout(typingPublishTimersRef.current.get(selectedUser.username))
    typingPublishTimersRef.current.delete(selectedUser.username)
    typingLastSentRef.current.delete(selectedUser.username)
    sendTypingStatus(selectedUser.username, false)
    const localId = crypto.randomUUID()
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
      // Older backend images only echo CHAT messages to the recipient.
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
    const retryLocalId = crypto.randomUUID()
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

      const localId = crypto.randomUUID()
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
    setMessageContextMenu(null)
    setEditingMessageId(message.id)
    setEditingMessageContent(message.content || '')
    setReactionPickerMessageId(null)
  }

  const openMessageContextMenu = (event, message, messageKey) => {
    event.preventDefault()
    if (!message?.id || isMessageDeleted(message)) {
      setMessageContextMenu(null)
      return
    }
    const menuWidth = 236
    const menuHeight = 220
    setReactionPickerMessageId(null)
    setDetailMessageId(null)
    setEditHistoryMessageId(null)
    setMessageContextMenu({
      message,
      messageKey,
      x: Math.max(8, Math.min(event.clientX, window.innerWidth - menuWidth - 8)),
      y: Math.max(8, Math.min(event.clientY, window.innerHeight - menuHeight - 8)),
    })
  }

  const copyMessageContent = async (message) => {
    try {
      if (!message?.content || !navigator.clipboard) throw new Error('Clipboard unavailable')
      await navigator.clipboard.writeText(message.content)
      setMessageContextMenu(null)
      showToast(t('messageCopied'))
    } catch {
      showToast(t('copyMessageFailed'), 'error')
    }
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

  const removeConversationLocally = (username) => {
    setFriends((current) => current.filter((friend) => friend.username !== username))
    setMessagesByUser((current) => {
      const nextMessages = { ...current }
      delete nextMessages[username]
      return nextMessages
    })
    selectedRef.current = null
    setSelectedUser(null)
    setConversationMenuOpen(false)
    setConfirmConversationAction(null)
  }

  const saveBlockedMessageIntervals = (nextPreferences) => {
    setBlockedMessageIntervals(nextPreferences)
    localStorage.setItem(BLOCKED_MESSAGES_STORAGE_KEY, JSON.stringify(nextPreferences))
  }

  const unblockSelectedConversation = async () => {
    if (!selectedUser) return
    try {
      await apiRequest(`/api/friends/unblock/${encodeURIComponent(selectedUser.username)}`, { method: 'POST' })
      setBlockedUsers((current) => current.filter((person) => person.username !== selectedUser.username))
    } catch (error) {
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
      return
    }
    const intervals = [...(blockedMessageIntervals[selectedConversationKey] || [])]
    const lastInterval = intervals[intervals.length - 1]
    // oxlint-disable-next-line react/purity -- timestamp is captured only from an explicit user action.
    if (lastInterval?.to == null) lastInterval.to = Date.now()
    saveBlockedMessageIntervals({ ...blockedMessageIntervals, [selectedConversationKey]: intervals })
    setConversationMenuOpen(false)
    showToast(t('unblockedMessagesSuccess'))
  }

  const performConversationAction = async () => {
    if (!selectedUser || !confirmConversationAction || conversationActionPending) return
    const targetUsername = selectedUser.username
    if (confirmConversationAction === 'block') {
      setConversationActionPending(true)
      try {
        const response = await apiRequest(`/api/friends/block/${encodeURIComponent(targetUsername)}`, { method: 'POST' })
        // oxlint-disable-next-line react/purity -- timestamp is captured only from an explicit user action.
        const intervals = [...(blockedMessageIntervals[selectedConversationKey] || []), { from: Date.now(), to: null }]
        saveBlockedMessageIntervals({ ...blockedMessageIntervals, [selectedConversationKey]: intervals })
        setBlockedUsers((current) => current.some((person) => person.username === targetUsername) ? current : [...current, selectedUser])
        removeConversationLocally(targetUsername)
        scheduleConnectionSync()
        showToast(response?.message || t('blockedSuccess'))
      } catch (error) {
        showToast(getErrorMessage(error, t('actionFailed')), 'error')
      } finally {
        setConversationActionPending(false)
      }
      return
    }
    setConversationActionPending(true)
    try {
      const response = await apiRequest(
        `/api/friends/${encodeURIComponent(targetUsername)}`,
        { method: 'DELETE' },
      )
      removeConversationLocally(targetUsername)
      scheduleConnectionSync()
      showToast(response?.message || t('unfriend'))
    } catch (error) {
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
    } finally {
      setConversationActionPending(false)
    }
  }

  const submitWorldMessage = (event) => {
    event.preventDefault()
    const content = worldDraft.trim()
    if (!content || !isAdmin) return
    if (sendWorldMessage({ content, survivalTime: null })) setWorldDraft('')
    else showToast(t('socketError'), 'error')
  }

  const openMessageSearch = () => {
    setConversationMenuOpen(false)
    setMessageSearchQuery('')
    setMessageSearchResults([])
    setMessageSearchHasMore(false)
    setMessageSearchCursor(null)
    setMessageSearchOpen(true)
  }

  const focusSearchResult = (message) => {
    const person = selectedRef.current
    if (!person || !message?.id) return
    setMessagesByUser((current) => ({
      ...current,
      [person.username]: upsertMessage(current[person.username] || [], message),
    }))
    setMessageSearchOpen(false)
    setDetailMessageId(message.id)
    setSearchTargetMessageId(message.id)
    window.clearTimeout(searchHighlightTimerRef.current)
    window.setTimeout(() => {
      document.getElementById(`chat-message-${message.id}`)?.scrollIntoView({
        behavior: 'smooth', block: 'center',
      })
    }, 80)
    searchHighlightTimerRef.current = window.setTimeout(() => setSearchTargetMessageId(null), 2200)
  }

  return (
    <main className="chat-app">
      <AppRail
        activeSection={activeSection}
        online={connectionState === 'connected'}
        totalUnreadMessages={totalUnreadMessages}
        friendRequestCount={friendRequests.length}
        worldNotificationCount={worldNotifications.length}
        onSelectSection={changeActiveSection}
        onOpenWorld={() => setWorldOpen(true)}
      />

      <aside className={`conversation-sidebar${activeSection !== 'chat' ? ' is-section-hidden' : ''}`}>
        <div className="conversation-sidebar__heading">
          <div><span>{t('appName')}</span><h1>{t('conversations')}</h1></div>
        </div>
        <div className="conversation-search">
          <ChatIcon name="search" size={18} />
          <input
            type="search"
            value={conversationQuery}
            onChange={(event) => setConversationQuery(event.target.value)}
            placeholder={t('searchConversations')}
            aria-label={t('searchConversations')}
            autoComplete="off"
          />
        </div>
        <div className="conversation-filter"><button className="is-active" type="button">{t('all')}</button><span>{filteredFriends.length}</span></div>
        <div className="friend-list">
          {friends.length === 0 && <div className="empty-friends"><ChatIcon name="users" size={26} /><p>{t('noFriends')}</p><button type="button" onClick={() => changeActiveSection('friends')}>{t('search')}</button></div>}
          {friends.length > 0 && filteredFriends.length === 0 && <div className="empty-friends"><ChatIcon name="search" size={26} /><p>{t('noConversationResults')}</p></div>}
          {filteredFriends.map((friend) => (
            <button key={friend.username} className={`friend-row${selectedUser?.username === friend.username ? ' is-active' : ''}`} type="button" onClick={() => selectFriend(friend)}>
              <Avatar person={friend} showStatus />
              <span className="friend-row__body">
                <span><strong>{displayName(friend)}</strong><time>{unreadCounts[friend.username] ? t('newMessage') : ''}</time></span>
                <small>{typingUsers[friend.username] ? t('typing') : `@${friend.username}`}</small>
              </span>
              {Boolean(unreadCounts[friend.username]) && <b>{unreadCounts[friend.username] > 99 ? '99+' : unreadCounts[friend.username]}</b>}
            </button>
          ))}
        </div>
        <div className="sidebar-profile">
          <Avatar person={currentUserWithPresence} size="small" showStatus />
          <span><strong>{displayName(user)}</strong><small>{isAdmin ? t('admin') : t('member')}</small></span>
        </div>
      </aside>

      <section className={`chat-main${activeSection !== 'chat' ? ' is-section-hidden' : ''}`}>
        <button className="world-ticker" type="button" onClick={() => setWorldOpen(true)}>
          <span className="world-ticker__icon"><ChatIcon name="globe" size={17} /></span>
          <strong>{t('worldShort')}</strong>
          <span className="world-ticker__viewport">
            <span className="world-ticker__track" key={`${latestWorldMessage?.timestamp || 'empty'}-${language}`}>
              <i>{latestWorldMessage?.sender || t('admin')}</i>
              <span>{latestWorldMessage?.content || t('worldEmpty')}</span>
            </span>
          </span>
          <ChatIcon name="history" size={17} />
        </button>

        {selectedUser ? (
          <>
            <header className="chat-header">
              <button className="mobile-back" type="button" aria-label="Back" onClick={() => {
                selectedRef.current = null
                setSelectedUser(null)
              }}><ChatIcon name="arrowLeft" /></button>
              <Avatar person={selectedUser} size="medium" showStatus />
              <span><strong>{displayName(selectedUser)}</strong><small className={isPersonOnline(selectedUser) ? 'is-online' : ''}>{isPersonOnline(selectedUser) ? t('online') : t('offline')}</small></span>
              <div className={`connection-pill connection-pill--${connectionState}`}><i />{t(connectionState === 'connected' ? 'connected' : connectionState === 'disconnected' ? 'disconnected' : 'reconnecting')}</div>
              <div className="conversation-actions" ref={conversationMenuRef}>
                <button className="conversation-actions__trigger" type="button" aria-label={t('conversationOptions')} aria-expanded={conversationMenuOpen} onClick={() => {
                  setConversationMenuOpen((current) => !current)
                }}><ChatIcon name="more" /></button>
                {conversationMenuOpen && (
                  <div className="conversation-menu">
                    <button type="button" onClick={openMessageSearch}><ChatIcon name="search" size={17} /><span>{t('searchMessages')}</span></button>
                    <span className="conversation-menu__divider" />
                    {selectedConversationBlocked ? (
                      <button type="button" onClick={unblockSelectedConversation}><ChatIcon name="block" size={17} /><span>{t('unblockMessages')}</span></button>
                    ) : (
                      <button type="button" onClick={() => {
                        setConversationMenuOpen(false)
                        setConfirmConversationAction('block')
                      }}><ChatIcon name="block" size={17} /><span>{t('blockMessages')}</span></button>
                    )}
                    <button type="button" onClick={() => {
                      setConversationMenuOpen(false)
                      setConfirmConversationAction('unfriend')
                    }}><ChatIcon name="trash" size={17} /><span>{t('unfriend')}</span></button>
                    <button className="is-danger" type="button" onClick={() => {
                      setConversationMenuOpen(false)
                      setReportDialogOpen(true)
                    }}><ChatIcon name="flag" size={17} /><span>{t('reportUser')}</span></button>
                  </div>
                )}
              </div>
            </header>
            <div className="message-stream" ref={messageStreamRef} onScroll={handleMessageStreamScroll}>
              {loadingConversation && <div className="message-loading"><i /><i /><i /></div>}
              {!loadingConversation && conversationPages[selectedUser.username]?.hasMore && (
                <button className="load-older" type="button" disabled={loadingOlderMessages} onClick={loadOlderConversation}>
                  <ChatIcon name="history" size={16} />{loadingOlderMessages ? t('loadingOlder') : t('loadOlder')}
                </button>
              )}
              {!loadingConversation && activeMessages.length === 0 && (
                <div className="conversation-start"><Avatar person={selectedUser} size="large" /><h2>{displayName(selectedUser)}</h2><p>@{selectedUser.username}</p></div>
              )}
              {activeMessages.map((message, index) => {
                const mine = message.sender === user?.username
                const previous = activeMessages[index - 1]
                const grouped = previous?.sender === message.sender
                const reactionSummary = summarizeReactions(message.reactions)
                const currentUserReaction = message.reactions?.[user?.username]
                const messageKey = message.id || message.localId || `${message.timestamp}-${index}`
                const showActions = detailMessageId === messageKey
                const showDetails = showActions || index === activeMessages.length - 1
                const editHistory = message.id
                  ? (messageEditHistory[messageEditHistoryKey(user?.username, message.id)] || [])
                  : []
                return (
                  <div
                    id={message.id ? `chat-message-${message.id}` : undefined}
                    key={`${messageKey}-${message.failureAttempt || 0}`}
                    className={`message-row${mine ? ' is-mine' : ''}${grouped ? ' is-grouped' : ''}${searchTargetMessageId === message.id ? ' is-search-target' : ''}`}
                    onContextMenu={(event) => openMessageContextMenu(event, message, messageKey)}
                  >
                    {!mine && !grouped && <Avatar person={selectedUser} size="tiny" />}
                    <div className="message-content message-reaction-anchor">
                      {editHistoryMessageId === messageKey && (
                        <aside className="message-edit-history message-edit-history-anchor" aria-label={t('editHistoryTitle')}>
                          <header><strong>{t('editHistoryTitle')}</strong><button type="button" aria-label={t('cancel')} onClick={() => setEditHistoryMessageId(null)}><ChatIcon name="close" size={13} /></button></header>
                          {editHistory.length > 0 ? (
                            <div>{[...editHistory].reverse().map((entry, historyIndex) => (
                              <article key={`${entry.editedAt}-${historyIndex}`}>
                                <span>{t('previousVersion')}</span>
                                <p>{entry.content}</p>
                                <time>{formatDateTime(entry.editedAt, language)}</time>
                              </article>
                            ))}</div>
                          ) : <p className="message-edit-history__empty">{t('editHistoryUnavailable')}</p>}
                        </aside>
                      )}
                      <div
                        className={`message-bubble${message.clientFailed ? ' is-failed' : ''}${isMessageDeleted(message) ? ' is-deleted' : ''}${!message.id || isMessageDeleted(message) ? ' is-static' : ''}`}
                        role={message.id && !isMessageDeleted(message) ? 'button' : undefined}
                        tabIndex={message.id && !isMessageDeleted(message) ? 0 : undefined}
                        aria-label={message.id && !isMessageDeleted(message) ? t('reactToMessage') : undefined}
                        onClick={message.id && !isMessageDeleted(message) ? () => {
                          setReactionPickerMessageId((current) => current === messageKey ? null : messageKey)
                          setDetailMessageId((current) => current === messageKey ? null : messageKey)
                        } : undefined}
                        onKeyDown={message.id && !isMessageDeleted(message) ? (event) => {
                          if (!['Enter', ' '].includes(event.key)) return
                          event.preventDefault()
                          setReactionPickerMessageId((current) => current === messageKey ? null : messageKey)
                          setDetailMessageId((current) => current === messageKey ? null : messageKey)
                        } : undefined}
                      >
                        {isMessageDeleted(message) ? t('deletedMessage') : (
                          <>
                            {String(message.contentType || '').toUpperCase() === 'IMAGE' && message.fileUrl && (
                              <img className="message-media message-media--image" src={message.fileUrl} alt={message.fileName || t('sharedImage')} loading="lazy" />
                            )}
                            {String(message.contentType || '').toUpperCase() === 'VIDEO' && message.fileUrl && (
                              <video className="message-media message-media--video" src={message.fileUrl} controls preload="metadata" onClick={(event) => event.stopPropagation()}>
                                {t('videoUnsupported')}
                              </video>
                            )}
                            {message.content && <span className="message-text">{message.content}</span>}
                            {message.fileName && <span className="message-file-name">{message.fileName}</span>}
                          </>
                        )}
                      </div>
                      {(isMessageEdited(message) || editHistory.length > 0) && !isMessageDeleted(message) && (
                        <button
                          className="message-edited-label message-edit-history-anchor"
                          type="button"
                          aria-expanded={editHistoryMessageId === messageKey}
                          onClick={(event) => {
                            event.stopPropagation()
                            setReactionPickerMessageId(null)
                            if (editHistoryMessageId !== messageKey) {
                              event.currentTarget.closest('.message-row')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
                            }
                            setEditHistoryMessageId((current) => current === messageKey ? null : messageKey)
                          }}
                        >{t('editedLabel')}</button>
                      )}
                      {reactionPickerMessageId === messageKey && (
                        <div className="reaction-picker" role="menu" aria-label={t('reactToMessage')}>
                          {REACTION_OPTIONS.map((reaction) => (
                            <button
                              key={reaction.type}
                              className={currentUserReaction === reaction.type ? 'is-active' : ''}
                              type="button"
                              role="menuitem"
                              disabled={reactionSubmittingId === message.id}
                              aria-label={reaction.type}
                              onClick={() => toggleReaction(message, reaction.type)}
                            >{reaction.emoji}</button>
                          ))}
                        </div>
                      )}
                      {reactionSummary.length > 0 && (
                        <button className="message-reactions" type="button" onClick={() => {
                          setReactionPickerMessageId(messageKey)
                          setDetailMessageId(messageKey)
                        }} aria-label={t('reactToMessage')}>
                          {reactionSummary.map((reaction) => <span key={reaction.type}>{reaction.emoji}{reaction.count > 1 && <b>{reaction.count}</b>}</span>)}
                        </button>
                      )}
                      {message.clientFailed && (
                        <div className="message-send-error" role="alert">
                          <span>{t('messageFailed')}</span>
                          <button type="button" disabled={connectionState !== 'connected'} onClick={() => retryFailedMessage(message)}>{t('retry')}</button>
                        </div>
                      )}
                      {showActions && !isMessageDeleted(message) && (
                        editingMessageId === message.id ? (
                          <form className="message-edit-form" onSubmit={(event) => saveMessageEdit(event, message)}>
                            <input autoFocus maxLength="10000" value={editingMessageContent} onChange={(event) => setEditingMessageContent(event.target.value)} />
                            <button type="submit" disabled={messageActionPending || !editingMessageContent.trim()}>{t('save')}</button>
                            <button type="button" onClick={() => setEditingMessageId(null)}>{t('cancel')}</button>
                          </form>
                        ) : (
                          <div className="message-actions">
                            {mine && String(message.contentType || 'TEXT').toUpperCase() === 'TEXT' && <button type="button" disabled={messageActionPending} onClick={() => beginMessageEdit(message)}>{t('editMessage')}</button>}
                            {mine && <button className="is-danger" type="button" disabled={messageActionPending} onClick={() => setRevokeTargetMessage(message)}>{t('revokeMessage')}</button>}
                          </div>
                        )
                      )}
                      {showDetails && <small className="message-details">{formatTime(message.timestamp, language)}{mine && <> · {message.clientFailed ? t('messageFailed') : t(message.status === 'READ' ? 'read' : message.status === 'DELIVERED' ? 'delivered' : message.status === 'SENDING' ? 'sending' : 'sent')}</>}</small>}
                    </div>
                  </div>
                )
              })}
              {typingUsers[selectedUser.username] && (
                <div className="typing-indicator">
                  <Avatar person={selectedUser} size="tiny" />
                  <span className="typing-indicator__bubble">
                    <i /><i /><i />
                    <small>{t('typing')}</small>
                  </span>
                </div>
              )}
              <div ref={messagesEndRef} />
              {messageContextMenu && (
                <div
                  className="message-context-menu"
                  role="menu"
                  aria-label={t('messageMenu')}
                  style={{ left: messageContextMenu.x, top: messageContextMenu.y }}
                  onContextMenu={(event) => event.preventDefault()}
                >
                  <span className="message-context-menu__label">{t('reactToMessage')}</span>
                  <div className="message-context-menu__reactions">
                    {REACTION_OPTIONS.map((reaction) => (
                      <button
                        key={reaction.type}
                        className={messageContextMenu.message.reactions?.[user?.username] === reaction.type ? 'is-active' : ''}
                        type="button"
                        role="menuitem"
                        aria-label={reaction.type}
                        disabled={reactionSubmittingId === messageContextMenu.message.id}
                        onClick={() => {
                          setMessageContextMenu(null)
                          void toggleReaction(messageContextMenu.message, reaction.type)
                        }}
                      >{reaction.emoji}</button>
                    ))}
                  </div>
                  <span className="message-context-menu__divider" />
                  {Boolean(messageContextMenu.message.content) && (
                    <button type="button" role="menuitem" onClick={() => void copyMessageContent(messageContextMenu.message)}><ChatIcon name="copy" size={16} /><span>{t('copyMessage')}</span></button>
                  )}
                  {messageContextMenu.message.sender === user?.username && String(messageContextMenu.message.contentType || 'TEXT').toUpperCase() === 'TEXT' && (
                    <button type="button" role="menuitem" disabled={messageActionPending} onClick={() => beginMessageEdit(messageContextMenu.message)}><ChatIcon name="edit" size={16} /><span>{t('editMessage')}</span></button>
                  )}
                  {messageContextMenu.message.sender === user?.username && (
                    <button className="is-danger" type="button" role="menuitem" disabled={messageActionPending} onClick={() => {
                      setMessageContextMenu(null)
                      setRevokeTargetMessage(messageContextMenu.message)
                    }}><ChatIcon name="trash" size={16} /><span>{t('revokeMessage')}</span></button>
                  )}
                </div>
              )}
            </div>
            <form className="message-composer-bar" onSubmit={submitMessage}>
              {rateLimitRemaining > 0 && (
                <div className="message-composer-lock" role="status">
                  <strong>{t('rateLimitActive')}</strong>
                  <span>{rateLimitRemaining}s</span>
                </div>
              )}
              <input ref={mediaInputRef} className="media-input" type="file" accept="image/*,video/*" onChange={handleMediaSelection} />
              <button
                className={`attachment-button${uploadingMedia ? ' is-uploading' : ''}`}
                type="button"
                aria-label={uploadingMedia ? t('uploadingMedia') : t('uploadMedia')}
                title={uploadingMedia ? t('uploadingMedia') : t('uploadMedia')}
                disabled={uploadingMedia || connectionState !== 'connected' || rateLimitRemaining > 0}
                onClick={() => mediaInputRef.current?.click()}
              ><ChatIcon name="image" /></button>
              <input ref={messageInputRef} value={messageDraft} onChange={handleDraftChange} placeholder={rateLimitRemaining > 0 ? t('rateLimitActive') : t('messagePlaceholder')} aria-label={t('messagePlaceholder')} disabled={rateLimitRemaining > 0} />
              <span className="composer-emoji-anchor">
                <button type="button" aria-label="Chọn emoji" aria-expanded={emojiPickerOpen} disabled={rateLimitRemaining > 0} onClick={() => setEmojiPickerOpen((open) => !open)}><ChatIcon name="smile" /></button>
                {emojiPickerOpen && (
                  <div className="composer-emoji-picker" role="menu" aria-label="Chọn emoji">
                    {MESSAGE_EMOJI_OPTIONS.map((emoji) => (
                      <button key={emoji} type="button" role="menuitem" aria-label={`Chèn ${emoji}`} onClick={() => insertMessageEmoji(emoji)}>{emoji}</button>
                    ))}
                  </div>
                )}
              </span>
              <button className="composer-send" type="submit" aria-label={t('send')} disabled={!messageDraft.trim() || connectionState !== 'connected' || rateLimitRemaining > 0}><ChatIcon name="send" size={18} /></button>
            </form>
          </>
        ) : (
          <div className="chat-welcome">
            <div className="chat-welcome__art"><span><ChatIcon name="chat" size={42} /></span><i /><b /></div>
            <span className="chat-welcome__eyebrow">CHATWEB · REALTIME</span>
            <h2>{t('welcomeTitle')}, {user?.firstName || user?.username}!</h2>
            <p>{t('welcomeBody')}</p>
            <button type="button" onClick={() => changeActiveSection('friends')}><ChatIcon name="search" size={18} />{t('searchPeople')}</button>
          </div>
        )}
      </section>

      {activeSection === 'friends' && (
        <section className="app-section-page friends-section-page" aria-label={t('friends')}>
          <header className="app-section-page__header"><span><ChatIcon name="users" size={22} /></span><div><small>CHATWEB</small><h1>{t('friends')}</h1><p>{t('friendsPageBody')}</p></div></header>
          <div className="app-section-page__content">
            <div className="search-input"><ChatIcon name="search" /><input autoFocus value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} placeholder={t('searchHint')} /></div>
            <div className="search-types">
              <button className={searchType === 'username' ? 'is-active' : ''} type="button" onClick={() => setSearchType('username')}>@ {t('username')}</button>
              <button className={searchType === 'displayName' ? 'is-active' : ''} type="button" onClick={() => setSearchType('displayName')}>{t('displayName')}</button>
            </div>
            {!searchQuery && <div className="relationship-lists">
              <div className="panel-section"><h3>{t('pendingRequests')} <span>{friendRequests.length}</span></h3>{friendRequests.map((person) => <PersonResult key={person.username} person={person} actionLabel={t('accept')} onAction={() => acceptFriend(person)} secondaryActionLabel={t('rejectRequest')} onSecondaryAction={() => removeFriendRelation(person, 'requestRejected')} />)}{!friendRequests.length && <p className="relationship-empty">{t('noPendingRequests')}</p>}</div>
              <div className="panel-section"><h3>{t('sentRequests')} <span>{sentRequests.length}</span></h3>{sentRequests.map((person) => <PersonResult key={person.username} person={person} actionLabel={t('requested')} disabled secondaryActionLabel={t('cancelRequest')} onSecondaryAction={() => removeFriendRelation(person, 'requestCancelled')} />)}{!sentRequests.length && <p className="relationship-empty">{t('noSentRequests')}</p>}</div>
              <div className="panel-section blocked-users-section"><h3>{t('blockedUsers')} <span>{blockedUsers.length}</span></h3>{blockedUsers.map((person) => <PersonResult key={person.username} person={person} actionLabel={t('unblockUser')} onAction={() => unblockServerUser(person)} />)}{!blockedUsers.length && <p className="relationship-empty">{t('noBlockedUsers')}</p>}</div>
            </div>}
            {!searchQuery && (
              <div className="panel-section suggestion-section">
                <div className="panel-section__heading">
                  <h3>{t('suggestedForYou')} <span>{visibleSuggestions.length}</span></h3>
                  <button type="button" onClick={loadSuggestions} disabled={loadingSuggestions}>{t('refreshSuggestions')}</button>
                </div>
                {loadingSuggestions && <div className="panel-loading"><i /><i /><i /></div>}
                {!loadingSuggestions && visibleSuggestions.map((person) => (
                  <PersonResult key={person.username} person={person} actionLabel={t('addFriend')} onAction={() => addFriend(person)} />
                ))}
                {!loadingSuggestions && visibleSuggestions.length === 0 && <div className="suggestion-empty"><ChatIcon name="users" size={24} /><p>{t('noSuggestions')}</p></div>}
              </div>
            )}
            <div className="panel-section search-results">
              {searching && <div className="panel-loading"><i /><i /><i /></div>}
              {!searching && searchQuery && searchResults.length === 0 && <div className="panel-empty"><ChatIcon name="search" size={30} /><p>{t('noResults')}</p></div>}
              {!searching && searchResults.map((person) => (
                <PersonResult key={person.username} person={person}
                  actionLabel={blockedNames.has(person.username) ? t('unblockUser') : friendNames.has(person.username) ? t('friends') : sentNames.has(person.username) ? t('requested') : t('addFriend')}
                  disabled={!blockedNames.has(person.username) && (friendNames.has(person.username) || sentNames.has(person.username))}
                  onAction={() => blockedNames.has(person.username) ? unblockServerUser(person) : addFriend(person)} onSelect={friendNames.has(person.username) ? () => selectFriend(person) : undefined} />
              ))}
            </div>
          </div>
        </section>
      )}

      {messageSearchOpen && selectedUser && (
        <div className="panel-backdrop" onMouseDown={(event) => event.target === event.currentTarget && setMessageSearchOpen(false)}>
          <section className="side-panel message-search-panel" role="dialog" aria-modal="true" aria-label={t('searchMessages')}>
            <header><div><span>@{selectedUser.username}</span><h2>{t('searchMessages')}</h2></div><button type="button" onClick={() => setMessageSearchOpen(false)}><ChatIcon name="close" /></button></header>
            <div className="search-input"><ChatIcon name="search" /><input autoFocus type="search" value={messageSearchQuery} onChange={(event) => setMessageSearchQuery(event.target.value)} placeholder={t('searchMessagesHint')} /></div>
            <div className="message-search-results">
              {!messageSearchQuery.trim() && <div className="panel-empty"><ChatIcon name="search" size={30} /><p>{t('typeToSearchMessages')}</p></div>}
              {messageSearchLoading && messageSearchResults.length === 0 && <div className="panel-loading"><i /><i /><i /></div>}
              {!messageSearchLoading && messageSearchQuery.trim() && messageSearchResults.length === 0 && <div className="panel-empty"><ChatIcon name="search" size={30} /><p>{t('noMessageResults')}</p></div>}
              {messageSearchResults.map((message) => (
                <button className="message-search-result" type="button" key={message.id} onClick={() => focusSearchResult(message)}>
                  <span><strong>{message.sender === user?.username ? t('you') : displayName(selectedUser)}</strong><time>{formatDateTime(message.timestamp, language)}</time></span>
                  <p>{message.content}</p>
                </button>
              ))}
              {messageSearchHasMore && <button className="load-older" type="button" disabled={messageSearchLoading} onClick={() => searchConversationMessages(messageSearchQuery, undefined, messageSearchCursor, true)}><ChatIcon name="history" size={16} />{t('loadMoreResults')}</button>}
            </div>
          </section>
        </div>
      )}

      {activeSection === 'notifications' && (
        <section className="app-section-page notifications-section-page" aria-label={t('notifications')}>
          <header className="app-section-page__header"><span><ChatIcon name="bell" size={22} /></span><div><small>CHATWEB</small><h1>{t('notifications')}</h1><p>{t('notificationsPageBody')}</p></div></header>
          <div className="app-section-page__content notifications-page__content">
            <div className="panel-section notifications-page__list">
              {friendRequests.map((person) => <PersonResult key={person.username} person={person} actionLabel={t('accept')} onAction={() => acceptFriend(person)} />)}
              {worldNotifications.map((message, index) => <button className="notification-card" type="button" key={`${message.receivedAt}-${index}`} onClick={() => setWorldOpen(true)}><span><ChatIcon name="globe" size={17} /></span><div><strong>{message.sender || t('admin')}</strong><p>{message.content}</p><time>{formatTime(message.receivedAt, language)}</time></div></button>)}
              {!friendRequests.length && !worldNotifications.length && <div className="panel-empty"><ChatIcon name="bell" size={30} /><p>{t('noNotifications')}</p></div>}
            </div>
          </div>
        </section>
      )}

      {worldOpen && (
        <div className="panel-backdrop" onMouseDown={(event) => event.target === event.currentTarget && setWorldOpen(false)}>
          <section className="side-panel world-panel" role="dialog" aria-modal="true" aria-label={t('worldHistory')}>
            <header><div><span>LIVE · CHATWEB</span><h2>{t('worldHistory')}</h2></div><button type="button" onClick={() => setWorldOpen(false)}><ChatIcon name="close" /></button></header>
            <div className="world-feed">
              {worldHasMore && <button className="load-older" type="button" onClick={() => loadWorldHistory(worldCursor, true)}><ChatIcon name="history" size={16} />{t('loadOlder')}</button>}
              {!worldMessages.length && <div className="panel-empty"><ChatIcon name="globe" size={30} /><p>{t('worldEmpty')}</p></div>}
              {worldMessages.map((message, index) => <article className="world-card" key={`${message.timestamp}-${index}`}><span><ChatIcon name="globe" size={18} /></span><div><strong>{message.sender || t('admin')}<em>{t('admin')}</em></strong><p>{message.content}</p><time>{formatTime(message.timestamp, language)}</time></div></article>)}
            </div>
            {isAdmin && (
              <form className="world-composer" onSubmit={submitWorldMessage}>
                <label>{t('adminBroadcast')}</label>
                <div><input value={worldDraft} onChange={(event) => setWorldDraft(event.target.value)} placeholder={t('broadcastPlaceholder')} /><button type="submit" disabled={!worldDraft.trim()}><ChatIcon name="send" size={17} />{t('publish')}</button></div>
              </form>
            )}
          </section>
        </div>
      )}

      {confirmConversationAction && selectedUser && (
        <div className="dialog-backdrop" onMouseDown={(event) => event.target === event.currentTarget && setConfirmConversationAction(null)}>
          <section className="conversation-dialog" role="alertdialog" aria-modal="true" aria-labelledby="conversation-confirm-title">
            <span className={`conversation-dialog__icon${confirmConversationAction === 'block' ? ' is-danger' : ''}`}><ChatIcon name={confirmConversationAction === 'block' ? 'block' : 'trash'} size={22} /></span>
            <h2 id="conversation-confirm-title">{t(confirmConversationAction === 'block' ? 'blockConfirmTitle' : 'unfriendConfirmTitle')}</h2>
            <p>{t(confirmConversationAction === 'block' ? 'blockConfirmBody' : 'unfriendConfirmBody')}</p>
            <strong>@{selectedUser.username}</strong>
            <div className="conversation-dialog__actions">
              <button type="button" onClick={() => setConfirmConversationAction(null)}>{t('cancel')}</button>
              <button className="is-danger" type="button" disabled={conversationActionPending} onClick={performConversationAction}>{t('confirm')}</button>
            </div>
          </section>
        </div>
      )}

      {revokeTargetMessage && selectedUser && (
        <div className="dialog-backdrop" onMouseDown={(event) => event.target === event.currentTarget && !messageActionPending && setRevokeTargetMessage(null)}>
          <section className="conversation-dialog" role="alertdialog" aria-modal="true" aria-labelledby="revoke-message-title">
            <span className="conversation-dialog__icon is-danger"><ChatIcon name="trash" size={22} /></span>
            <h2 id="revoke-message-title">{t('revokeMessageTitle')}</h2>
            <p>{t('revokeMessageBody')}</p>
            <div className="revoke-message-preview">{revokeTargetMessage.content || revokeTargetMessage.fileName || t('sharedImage')}</div>
            <div className="conversation-dialog__actions">
              <button type="button" disabled={messageActionPending} onClick={() => setRevokeTargetMessage(null)}>{t('cancel')}</button>
              <button className="is-danger" type="button" disabled={messageActionPending} onClick={revokeChatMessage}>{messageActionPending ? t('revokingMessage') : t('revokeMessage')}</button>
            </div>
          </section>
        </div>
      )}

      {reportDialogOpen && selectedUser && (
        <div className="dialog-backdrop" onMouseDown={(event) => event.target === event.currentTarget && setReportDialogOpen(false)}>
          <section className="conversation-dialog report-dialog" role="dialog" aria-modal="true" aria-labelledby="report-dialog-title">
            <span className="conversation-dialog__icon is-danger"><ChatIcon name="flag" size={22} /></span>
            <h2 id="report-dialog-title">{t('reportTitle')}</h2>
            <p>@{selectedUser.username}</p>
            <label><span>{t('reportReason')}</span><select value={reportReason} onChange={(event) => setReportReason(event.target.value)}><option value="SPAM">{t('reportSpam')}</option><option value="HARASSMENT">{t('reportHarassment')}</option><option value="INAPPROPRIATE">{t('reportInappropriate')}</option><option value="IMPERSONATION">{t('reportImpersonation')}</option><option value="OTHER">{t('reportOther')}</option></select></label>
            <label><span>{t('reportDetails')}</span><textarea rows="4" value={reportDetails} onChange={(event) => setReportDetails(event.target.value)} /></label>
            <div className="report-dialog__notice">{t('reportUnavailable')}</div>
            <div className="conversation-dialog__actions">
              <button type="button" onClick={() => {
                setReportDialogOpen(false)
                setReportReason('SPAM')
                setReportDetails('')
              }}>{t('cancel')}</button>
              <button className="is-danger" type="button" disabled>{t('submitReport')}</button>
            </div>
          </section>
        </div>
      )}

      {toast && <div className={`chat-toast chat-toast--${toast.tone}`} role="status"><span>{toast.tone === 'error' ? '!' : '✓'}</span>{toast.message}</div>}
    </main>
  )
}

function PersonResult({ person, actionLabel, onAction, onSelect, disabled = false, secondaryActionLabel = '', onSecondaryAction }) {
  return (
    <article className="person-result">
      <button className="person-result__identity" type="button" onClick={onSelect} disabled={!onSelect}>
        <Avatar person={person} showStatus /><span><strong>{displayName(person)}</strong><small>@{person.username}</small></span>
      </button>
      <span className="person-result__actions">
        {secondaryActionLabel && <button className="person-result__secondary" type="button" onClick={onSecondaryAction}>{secondaryActionLabel}</button>}
        <button className="person-result__action" type="button" disabled={disabled} onClick={onAction}>{disabled && <ChatIcon name="check" size={15} />}{actionLabel}</button>
      </span>
    </article>
  )
}

export default ChatPage
