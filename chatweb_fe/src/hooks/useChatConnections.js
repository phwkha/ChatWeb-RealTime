import { useCallback, useEffect, useRef, useState } from 'react'
import { apiRequest, getErrorMessage } from '../services/apiClient.js'
import {
  conversationPreferenceKey,
  readBlockedMessageIntervals,
  BLOCKED_MESSAGES_STORAGE_KEY,
} from '../components/chat/chatUtils.js'

export function useChatConnections({
  currentUser,
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

  const friendSyncTimersRef = useRef([])
  const currentUsernameKey = String(currentUser?.username || '').trim().toLocaleLowerCase('en-US')

  const saveBlockedIntervals = useCallback((nextPreferences) => {
    setBlockedMessageIntervals(nextPreferences)
    localStorage.setItem(BLOCKED_MESSAGES_STORAGE_KEY, JSON.stringify(nextPreferences))
  }, [])

  const loadConnections = useCallback(async () => {
    try {
      const [friendsRes, requestsRes, sentRes, blockedRes] = await Promise.allSettled([
        apiRequest('/api/friends?size=100'),
        apiRequest('/api/friends/requests?size=100'),
        apiRequest('/api/friends/sent?size=100'),
        apiRequest('/api/friends/blocked?size=100'),
      ])
      if (friendsRes.status === 'fulfilled') setFriends(friendsRes.value?.data?.content || [])
      if (requestsRes.status === 'fulfilled') setFriendRequests(requestsRes.value?.data?.content || [])
      if (sentRes.status === 'fulfilled') setSentRequests(sentRes.value?.data?.content || [])
      if (blockedRes.status === 'fulfilled') setBlockedUsers(blockedRes.value?.data?.content || [])
    } finally {
      setConnectionsLoaded(true)
    }
  }, [])

  const scheduleConnectionSync = useCallback(() => {
    friendSyncTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    friendSyncTimersRef.current = [
      window.setTimeout(() => {
        void loadConnections()
      }, 250),
    ]
  }, [loadConnections])

  useEffect(() => {
    void loadConnections()
    return () => {
      friendSyncTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    }
  }, [loadConnections])

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
      setSentRequests((current) => [...current, person])
      showToast(response?.message || t('requested'))
      return true
    } catch (error) {
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
      showToast(response?.message || t('accept'))
      await loadConnections()
      return true
    } catch (error) {
      showToast(getErrorMessage(error, t('errorGeneric')), 'error')
      return false
    }
  }, [loadConnections, showToast, t])

  const removeFriendRelation = useCallback(async (person, successKey) => {
    try {
      const response = await apiRequest(`/api/friends/${encodeURIComponent(person.username)}`, {
        method: 'DELETE',
      })
      await loadConnections()
      showToast(response?.message || t(successKey))
      return true
    } catch (error) {
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
      return false
    }
  }, [loadConnections, showToast, t])

  const unblockServerUser = useCallback(async (person) => {
    try {
      const response = await apiRequest(`/api/friends/unblock/${encodeURIComponent(person.username)}`, {
        method: 'POST',
      })
      setBlockedUsers((current) => current.filter((b) => b.username !== person.username))
      const prefKey = conversationPreferenceKey(currentUser?.username, person.username)
      const intervals = [...(blockedMessageIntervals[prefKey] || [])]
      const lastInterval = intervals[intervals.length - 1]
      if (lastInterval?.to == null) lastInterval.to = Date.now()
      saveBlockedIntervals({ ...blockedMessageIntervals, [prefKey]: intervals })
      scheduleConnectionSync()
      showToast(response?.message || t('unblockedUserSuccess'))
      return true
    } catch (error) {
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
        if (onAfterRemoveConversation) onAfterRemoveConversation(targetUsername)
        scheduleConnectionSync()
        showToast(response?.message || t('unfriend'))
      }
    } catch (error) {
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
    } finally {
      setActionPending(false)
    }
  }, [actionPending, blockedMessageIntervals, currentUser?.username, onAfterRemoveConversation, saveBlockedIntervals, scheduleConnectionSync, showToast, t])

  const unblockSelectedConversation = useCallback(async (selectedUser) => {
    if (!selectedUser) return
    try {
      await apiRequest(`/api/friends/unblock/${encodeURIComponent(selectedUser.username)}`, { method: 'POST' })
      setBlockedUsers((current) => current.filter((p) => p.username !== selectedUser.username))
    } catch (error) {
      showToast(getErrorMessage(error, t('actionFailed')), 'error')
      return
    }
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
