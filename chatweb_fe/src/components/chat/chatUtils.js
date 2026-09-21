export const FRIEND_EVENT_TYPES = new Set([
  'FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'YOU_ACCEPTED', 'UNFRIENDED',
  'REQUEST_CANCELLED', 'REQUEST_REJECTED', 'USER_ONLINE', 'USER_OFFLINE',
])
export const RECEIPT_PREFIX = '__CHATWEB_RECEIPT__:'
export const REACTION_PREFIX = '__CHATWEB_REACTION__:'
export const TYPING_PREFIX = '__CHATWEB_TYPING__:'
export const STATUS_RANK = { SENDING: 0, SENT: 1, DELIVERED: 2, READ: 3 }
export const REACTION_OPTIONS = [
  { type: 'LIKE', emoji: '👍' },
  { type: 'HEART', emoji: '❤️' },
  { type: 'LAUGH', emoji: '😂' },
  { type: 'SAD', emoji: '😢' },
  { type: 'ANGRY', emoji: '😡' },
]
export const MESSAGE_EMOJI_OPTIONS = ['😀', '😂', '😍', '😊', '😎', '😢', '😡', '😮', '👍', '👏', '🙏', '🔥', '🎉', '❤️', '✨', '💯']
export const REACTION_EMOJI = Object.fromEntries(REACTION_OPTIONS.map((reaction) => [reaction.type, reaction.emoji]))
export const BLOCKED_MESSAGES_STORAGE_KEY = 'chatweb-blocked-message-intervals'
export const EDIT_HISTORY_STORAGE_KEY = 'chatweb-message-edit-history'
export const WATERMARK_CHANNEL_NAME = 'chatweb_watermark_sync'
export const MAX_MEDIA_SIZE_BYTES = 20 * 1024 * 1024
export const MESSAGE_PAGE_SIZE = 30
export const MESSAGE_HISTORY_FALLBACK_SIZE = 1000
export const MESSAGE_SEARCH_PAGE_SIZE = 30

export function broadcastWatermarkRead(reader, sender, readTimestamp) {
  if (typeof window === 'undefined' || !window.BroadcastChannel) return
  try {
    const channel = new BroadcastChannel(WATERMARK_CHANNEL_NAME)
    channel.postMessage({
      type: 'WATERMARK_READ',
      reader,
      sender,
      readTimestamp: readTimestamp || new Date().toISOString(),
      status: 'READ',
    })
    channel.close()
  } catch {
    // Ignore BroadcastChannel errors in unsupported/restricted environments
  }
}

export function getMediaContentType(file) {
  if (file?.type?.startsWith('image/')) return 'IMAGE'
  if (file?.type?.startsWith('video/')) return 'VIDEO'
  return null
}

export function normalizeSearchValue(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('vi-VN')
    .trim()
}

export function isPersonOnline(person) {
  return [person?.online, person?.isOnline].some((value) => (
    value === true || value === 1 || String(value).toLowerCase() === 'true'
  ))
}

export function isMessageDeleted(message) {
  const explicitFlag = [message?.deleted, message?.isDeleted, message?.is_deleted].some((value) => (
    value === true || value === 1 || String(value).toLocaleLowerCase('en-US') === 'true'
  ))
  if (explicitFlag) return true

  const isPersistedChatMessage = Boolean(message?.id)
    && (!message.messageType || String(message.messageType).toUpperCase() === 'CHAT')
  const hasNoPayload = !String(message?.content || '').trim() && !message?.fileUrl
  return isPersistedChatMessage && hasNoPayload
}

export function isMessageEdited(message) {
  return [message?.edited, message?.isEdited, message?.is_edited].some((value) => (
    value === true || value === 1 || String(value).toLocaleLowerCase('en-US') === 'true'
  ))
}

export function isDisplayableChatMessage(message) {
  if (!message || (message.messageType && message.messageType !== 'CHAT')) return false
  return Boolean(isMessageDeleted(message) || String(message.content || '').trim() || message.fileUrl)
}

export function normalizeMessages(items) {
  return [...(items || [])]
    .filter(isDisplayableChatMessage)
    .sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
}

export function upsertMessage(list, incoming) {
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
  const replyToId = incoming.replyToId !== undefined ? incoming.replyToId : next[matchIndex].replyToId
  const mergedMessage = { ...next[matchIndex], ...incoming, replyToId, status, clientFailed: false }
  if (isMessageEdited(next[matchIndex]) && !isMessageEdited(mergedMessage)) {
    mergedMessage.edited = true
    mergedMessage.isEdited = true
  }
  next[matchIndex] = mergedMessage
  return normalizeMessages(next)
}

export function mergeMessageLists(current, incoming) {
  return (incoming || []).reduce((merged, message) => upsertMessage(merged, message), current || [])
}

export function parseRealtimeReceipt(content) {
  if (!String(content || '').startsWith(RECEIPT_PREFIX)) return null
  try {
    const receipt = JSON.parse(String(content).slice(RECEIPT_PREFIX.length))
    return ['DELIVERED', 'READ'].includes(receipt?.status) ? receipt : null
  } catch {
    return null
  }
}

export function parseRealtimeReaction(content) {
  if (!String(content || '').startsWith(REACTION_PREFIX)) return null
  try {
    const reaction = JSON.parse(String(content).slice(REACTION_PREFIX.length))
    return reaction?.message?.id ? reaction.message : null
  } catch {
    return null
  }
}

export function parseRealtimeTyping(content) {
  if (!String(content || '').startsWith(TYPING_PREFIX)) return null
  try {
    const typing = JSON.parse(String(content).slice(TYPING_PREFIX.length))
    return typeof typing?.active === 'boolean' ? typing : null
  } catch {
    return null
  }
}

export function summarizeReactions(reactions) {
  const counts = new Map()
  Object.values(reactions || {}).forEach((type) => counts.set(type, (counts.get(type) || 0) + 1))
  return [...counts.entries()].map(([type, count]) => ({ type, count, emoji: REACTION_EMOJI[type] || '✨' }))
}

export function readBlockedMessageIntervals() {
  try {
    return JSON.parse(localStorage.getItem(BLOCKED_MESSAGES_STORAGE_KEY) || '{}')
  } catch {
    return {}
  }
}

export function readMessageEditHistory() {
  try {
    const history = JSON.parse(localStorage.getItem(EDIT_HISTORY_STORAGE_KEY) || '{}')
    return history && typeof history === 'object' ? history : {}
  } catch {
    return {}
  }
}

export function messageEditHistoryKey(username, messageId) {
  return `${String(username || '').toLocaleLowerCase('en-US')}:${messageId}`
}

export function conversationPreferenceKey(username, peerUsername) {
  return `${String(username || '').toLocaleLowerCase('en-US')}:${String(peerUsername || '').toLocaleLowerCase('en-US')}`
}

export function isIncomingMessageBlocked(preferences, username, peerUsername) {
  const intervals = preferences[conversationPreferenceKey(username, peerUsername)] || []
  return intervals.some((interval) => interval.to == null)
}

export function wasMessageSentWhileBlocked(message, preferences, username, peerUsername) {
  if (message?.sender !== peerUsername) return false
  const timestamp = new Date(message.timestamp).getTime()
  const intervals = preferences[conversationPreferenceKey(username, peerUsername)] || []
  return intervals.some((interval) => timestamp >= interval.from && (interval.to == null || timestamp <= interval.to))
}

export function promoteStatuses(messages, currentUsername, status, statusTimestamp) {
  const cutoff = new Date(statusTimestamp).getTime()
  return (messages || []).map((message) => {
    if (message.sender !== currentUsername || new Date(message.timestamp).getTime() > cutoff) return message
    return (STATUS_RANK[status] || 0) > (STATUS_RANK[message.status] || 0) ? { ...message, status } : message
  })
}

export function formatTime(timestamp, language) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  const locale = language === 'vi' ? 'vi-VN' : language === 'ja' ? 'ja-JP' : 'en-US'
  return new Intl.DateTimeFormat(locale, {
    hour: '2-digit', minute: '2-digit',
  }).format(date)
}

export function formatDateTime(timestamp, language) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  const locale = language === 'vi' ? 'vi-VN' : language === 'ja' ? 'ja-JP' : 'en-US'
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date)
}

export function formatRelativeTime(timestamp, language = 'vi') {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000)

  if (diffInSeconds < 60) {
    if (language === 'vi') return 'vừa xong'
    if (language === 'ja') return 'たった今'
    return 'just now'
  }

  const locale = language === 'vi' ? 'vi-VN' : language === 'ja' ? 'ja-JP' : 'en-US'
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })

  const diffInMinutes = Math.floor(diffInSeconds / 60)
  if (diffInMinutes < 60) {
    return rtf.format(-diffInMinutes, 'minute')
  }

  const diffInHours = Math.floor(diffInMinutes / 60)
  if (diffInHours < 24) {
    return rtf.format(-diffInHours, 'hour')
  }

  const diffInDays = Math.floor(diffInHours / 24)
  if (diffInDays < 7) {
    return rtf.format(-diffInDays, 'day')
  }

  return formatDateTime(timestamp, language)
}

export function initialChatSection() {
  if (typeof window === 'undefined') return 'chat'
  const section = new URLSearchParams(window.location.search).get('section')
  return ['chat', 'friends', 'notifications'].includes(section) ? section : 'chat'
}

export function getQuotedSenderName(message, currentUser, selectedUser, t) {
  if (!message) return ''
  if (currentUser?.username && message.sender === currentUser.username) {
    return t ? t('you') : 'You'
  }
  if (selectedUser?.username && message.sender === selectedUser.username) {
    const fullName = [selectedUser.firstName, selectedUser.lastName].filter(Boolean).join(' ').trim()
    return fullName || selectedUser.nickname || selectedUser.fullName || selectedUser.username || ''
  }
  return message.sender || ''
}

export function getQuotedMessagePreview(message, t) {
  if (!message) return ''
  if (message.notFound) {
    return t ? t('originalMessageNotFound') : 'Original message not found'
  }
  if (isMessageDeleted(message)) {
    return t ? t('deletedMessage') : 'Message deleted'
  }
  const contentType = String(message.contentType || 'TEXT').toUpperCase()
  if (contentType === 'IMAGE') {
    return message.fileName || (t ? t('sharedImage') : 'Shared image')
  }
  if (contentType === 'VIDEO') {
    return message.fileName || (t ? t('video') : 'Video')
  }
  return message.content || ''
}
