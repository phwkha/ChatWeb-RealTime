import { useCallback, useEffect, useRef, useState } from 'react'
import { apiRequest, getErrorMessage } from '../services/apiClient.js'
import {
  isDisplayableChatMessage,
  isMessageDeleted,
  wasMessageSentWhileBlocked,
  MESSAGE_SEARCH_PAGE_SIZE,
} from '../components/chat/chatUtils.js'

export function useMessageSearch({
  user,
  selectedUser,
  blockedMessageIntervals,
  showToast,
  t,
  onFocusMessage,
}) {
  const [messageSearchOpen, setMessageSearchOpen] = useState(false)
  const [messageSearchQuery, setMessageSearchQuery] = useState('')
  const [messageSearchResults, setMessageSearchResults] = useState([])
  const [messageSearchHasMore, setMessageSearchHasMore] = useState(false)
  const [messageSearchCursor, setMessageSearchCursor] = useState(null)
  const [messageSearchLoading, setMessageSearchLoading] = useState(false)
  const [searchTargetMessageId, setSearchTargetMessageId] = useState(null)

  const selectedRef = useRef(selectedUser)
  const searchHighlightTimerRef = useRef(null)

  useEffect(() => {
    selectedRef.current = selectedUser
  }, [selectedUser])

  const searchConversationMessages = useCallback(async (
    keyword,
    signal = undefined,
    cursor = null,
    append = false,
  ) => {
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

  useEffect(() => {
    const keyword = messageSearchQuery.trim()
    if (!messageSearchOpen || !keyword || !selectedUser?.username) {
      setMessageSearchResults([])
      setMessageSearchHasMore(false)
      setMessageSearchCursor(null)
      setMessageSearchLoading(false)
      return undefined
    }

    const controller = new AbortController()
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

  const openMessageSearch = useCallback(() => {
    setMessageSearchQuery('')
    setMessageSearchResults([])
    setMessageSearchHasMore(false)
    setMessageSearchCursor(null)
    setMessageSearchOpen(true)
  }, [])

  const closeMessageSearch = useCallback(() => {
    setMessageSearchOpen(false)
  }, [])

  const focusSearchResult = useCallback((message) => {
    const person = selectedRef.current
    if (!person || !message?.id) return
    if (onFocusMessage) onFocusMessage(message)
    setMessageSearchOpen(false)
    setSearchTargetMessageId(message.id)
    window.clearTimeout(searchHighlightTimerRef.current)
    window.setTimeout(() => {
      document.getElementById(`chat-message-${message.id}`)?.scrollIntoView({
        behavior: 'smooth', block: 'center',
      })
    }, 80)
    searchHighlightTimerRef.current = window.setTimeout(() => setSearchTargetMessageId(null), 2200)
  }, [onFocusMessage])

  useEffect(() => {
    return () => {
      window.clearTimeout(searchHighlightTimerRef.current)
    }
  }, [])

  return {
    messageSearchOpen,
    messageSearchQuery,
    messageSearchResults,
    messageSearchHasMore,
    messageSearchCursor,
    messageSearchLoading,
    searchTargetMessageId,
    setMessageSearchOpen,
    setMessageSearchQuery,
    openMessageSearch,
    closeMessageSearch,
    focusSearchResult,
    searchConversationMessages,
  }
}

export default useMessageSearch
