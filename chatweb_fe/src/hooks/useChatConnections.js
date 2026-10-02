import { useCallback, useEffect, useRef, useState } from 'react'
import { apiRequest, getErrorMessage } from '../services/apiClient.js'
import {
  conversationPreferenceKey,
  readBlockedMessageIntervals,
  BLOCKED_MESSAGES_STORAGE_KEY,
} from '../components/chat/chatUtils.js'

export function useChatConnections({
  currentUser,
  activeSection = 'chat',
  showToast,
  t,
  onAfterRemoveConversation,
}) {
  const [friends, setFriends] = useState([])
  const [friendRequests, setFriendRequests] = useState([])
  const [sentRequests, setSentRequests] = useState([])
  const [blockedUsers, setBlockedUsers] = useState([])
  const [blockedMessageIntervals, setBlockedMessageIntervals] = useState(readBlockedMessageIntervals)
  const [actionPending, setActionPending] = useState(false)
  const [connectionsLoaded, setConnectionsLoaded] = useState(false)

  const isMountedRef = useRef(true)
  const abortControllerRef = useRef(null)
  const friendSyncTimersRef = useRef([])
  const currentUsernameKey = String(currentUser?.username || '').trim().toLocaleLowerCase('en-US')
  const requestsLoadedRef = useRef(false)

  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
        abortControllerRef.current = null
      }
      friendSyncTimersRef.current.forEach((timer) => window.clearTimeout(timer))
      friendSyncTimersRef.current = []
    }
  }, [])

  useEffect(() => {
    requestsLoadedRef.current = false
    if (isMountedRef.current) {
      setBlockedMessageIntervals(readBlockedMessageIntervals())
    }
  }, [currentUser?.username])

  const saveBlockedIntervals = useCallback((nextPreferences) => {
    if (isMountedRef.current) {
      setBlockedMessageIntervals(nextPreferences)
    }
    try {
      localStorage.setItem(BLOCKED_MESSAGES_STORAGE_KEY, JSON.stringify(nextPreferences))
    } catch (error) {
      console.warn('Failed to save blocked message intervals to localStorage:', error)
    }
  }, [])

  const loadFriendsOnly = useCallback(async () => {
    try {
      const friendsRes = await apiRequest('/api/friends?size=100')
      if (!isMountedRef.current) return
      setFriends(friendsRes?.data?.content || [])
    } catch (error) {
      if (error?.name === 'AbortError') return
    } finally {
      if (isMountedRef.current) {
        setConnectionsLoaded(true)
      }
    }
  }, [])

  const loadFriendsAndRelationships = useCallback(async () => {
    try {
      const [friendsRes, requestsRes, sentRes, blockedRes] = await Promise.allSettled([
        apiRequest('/api/friends?size=100'),
        apiRequest('/api/friends/requests?size=100'),
        apiRequest('/api/friends/sent?size=100'),
        apiRequest('/api/friends/blocked?size=100'),
      ])
      if (!isMountedRef.current) return
      if (friendsRes.status === 'fulfilled') setFriends(friendsRes.value?.data?.content || [])
      if (requestsRes.status === 'fulfilled') setFriendRequests(requestsRes.value?.data?.content || [])
      if (sentRes.status === 'fulfilled') setSentRequests(sentRes.value?.data?.content || [])
      if (blockedRes.status === 'fulfilled') setBlockedUsers(blockedRes.value?.data?.content || [])
      requestsLoadedRef.current = true
    } finally {
      if (isMountedRef.current) {
        setConnectionsLoaded(true)
      }
    }
  }, [])

  const loadConnections = useCallback(async () => {
    if (activeSection === 'friends') {
      await loadFriendsAndRelationships()
    } else {
      await loadFriendsOnly()
    }
  }, [activeSection, loadFriendsAndRelationships, loadFriendsOnly])

  const scheduleConnectionSync = useCallback(() => {
    friendSyncTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    friendSyncTimersRef.current = [
      window.setTimeout(() => {
        if (!isMountedRef.current) return
        void loadConnections()
      }, 250),
    ]
  }, [loadConnections])

  useEffect(() => {
    void loadConnections()
    return () => {
      friendSyncTimersRef.current.forEach((timer) => window.clearTimeout(timer))
      friendSyncTimersRef.current = []
    }
  }, [loadConnections, currentUser?.username])

  useEffect(() => {
    if (activeSection === 'friends' && !requestsLoadedRef.current) {
      void loadFriendsAndRelationships()
    }
  }, [activeSection, loadFriendsAndRelationships, currentUser?.username])

  const addFriend = useCallback(async (person) => {
    if (String(person?.username || '').trim().toLocaleLowerCase('en-US') === currentUsernameKey) {
      showToast(t('cannotFriendSelf'), 'error')
      return false
    }
    try {
      const response = await apiRequest('/api/friends/request', {
        method: 'POST',
        body: { targetUsername: person.username },
      })
      if (!isMountedRef.current) return false
      setSentRequests((current) => [...current, person])
      showToast(response?.message || t('requested'))
      return true
    } catch (error) {
      if (!isMountedRef.current) return false
      showToast(getErrorMessage(error, t('errorGeneric')), 'error')
      return false
    }
  }, [currentUsernameKey, showToast, t])

  const acceptFriend = useCallback(async (person) => {
    try {
      const response = await apiRequest('/api/friends/accept', {
        method: 'POST',
        body: { targetUsername: person.username },
      })
      if (!isMountedRef.current) return false
      showToast(response?.message || t('accept'))
      await loadConnections()
      return true
    } catch (error) {
      if (!isMountedRef.current) return false
      showToast(getErrorMessage(error, t('errorGeneric')), 'error')
      return false
    }
  }, [loadConnections, showToast, t])

  const removeFriendRelation = useCallback(async (person, successKey) => {
    try {
      const response = await apiRequest(`/api/friends/${encodeURIComponent(person.username)}`, {
        method: 'DELETE',
      })
      if (!isMountedRef.current) return false
      await loadConnections()
      showToast(response?.message || t(successKey))
      return true
    } catch (error) {
      if (!isMountedRef.current) return false
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
      return false
    }
  }, [loadConnections, showToast, t])

  const unblockServerUser = useCallback(async (person) => {
    try {
      const response = await apiRequest(`/api/friends/unblock/${encodeURIComponent(person.username)}`, {
        method: 'POST',
      })
      if (!isMountedRef.current) return false
      setBlockedUsers((current) => current.filter((b) => b.username !== person.username))
      const prefKey = conversationPreferenceKey(currentUser?.username, person.username)
      const intervals = [...(blockedMessageIntervals[prefKey] || [])]
      if (intervals.length > 0) {
        const lastIndex = intervals.length - 1
        const lastInterval = { ...intervals[lastIndex] }
        if (lastInterval.to == null) {
          lastInterval.to = Date.now()
          intervals[lastIndex] = lastInterval
        }
      }
      saveBlockedIntervals({ ...blockedMessageIntervals, [prefKey]: intervals })
      scheduleConnectionSync()
      showToast(response?.message || t('unblockedUserSuccess'))
      return true
    } catch (error) {
      if (!isMountedRef.current) return false
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
      return false
    }
  }, [blockedMessageIntervals, currentUser?.username, saveBlockedIntervals, scheduleConnectionSync, showToast, t])

  const performConversationAction = useCallback(async (selectedUser, action) => {
    if (!selectedUser || !action || actionPending) return
    const targetUsername = selectedUser.username
    setActionPending(true)
    try {
      if (action === 'block') {
        const response = await apiRequest(`/api/friends/block/${encodeURIComponent(targetUsername)}`, {
          method: 'POST',
        })
        if (!isMountedRef.current) return
        const prefKey = conversationPreferenceKey(currentUser?.username, targetUsername)
        const intervals = [...(blockedMessageIntervals[prefKey] || []), { from: Date.now(), to: null }]
        saveBlockedIntervals({ ...blockedMessageIntervals, [prefKey]: intervals })
        setBlockedUsers((current) => current.some((p) => p.username === targetUsername) ? current : [...current, selectedUser])
        if (onAfterRemoveConversation) onAfterRemoveConversation(targetUsername)
        scheduleConnectionSync()
        showToast(response?.message || t('blockedSuccess'))
      } else {
        const response = await apiRequest(`/api/friends/${encodeURIComponent(targetUsername)}`, {
          method: 'DELETE',
        })
        if (!isMountedRef.current) return
        if (onAfterRemoveConversation) onAfterRemoveConversation(targetUsername)
        scheduleConnectionSync()
        showToast(response?.message || t('unfriend'))
      }
    } catch (error) {
      if (!isMountedRef.current) return
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
    } finally {
      if (isMountedRef.current) {
        setActionPending(false)
      }
    }
  }, [actionPending, blockedMessageIntervals, currentUser?.username, onAfterRemoveConversation, saveBlockedIntervals, scheduleConnectionSync, showToast, t])

  const unblockSelectedConversation = useCallback(async (selectedUser) => {
    if (!selectedUser) return
    try {
      await apiRequest(`/api/friends/unblock/${encodeURIComponent(selectedUser.username)}`, { method: 'POST' })
      if (!isMountedRef.current) return
      setBlockedUsers((current) => current.filter((p) => p.username !== selectedUser.username))
    } catch (error) {
      if (!isMountedRef.current) return
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
      return
    }
    if (!isMountedRef.current) return
    const prefKey = conversationPreferenceKey(currentUser?.username, selectedUser.username)
    const intervals = [...(blockedMessageIntervals[prefKey] || [])]
    const lastInterval = intervals[intervals.length - 1]
    if (lastInterval?.to == null) lastInterval.to = Date.now()
    saveBlockedIntervals({ ...blockedMessageIntervals, [prefKey]: intervals })
    showToast(t('unblockedMessagesSuccess'))
  }, [blockedMessageIntervals, currentUser?.username, saveBlockedIntervals, showToast, t])

  return {
    friends,
    setFriends,
    connectionsLoaded,
    friendRequests,
    setFriendRequests,
    sentRequests,
    setSentRequests,
    blockedUsers,
    setBlockedUsers,
    blockedMessageIntervals,
    setBlockedMessageIntervals,
    actionPending,
    saveBlockedIntervals,
    loadConnections,
    scheduleConnectionSync,
    addFriend,
    acceptFriend,
    removeFriendRelation,
    unblockServerUser,
    performConversationAction,
    unblockSelectedConversation,
  }
}

export default useChatConnections
