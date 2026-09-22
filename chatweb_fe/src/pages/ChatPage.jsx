import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import AppRail from '../components/chat/AppRail.jsx'
import ChatSidebar from '../components/chat/ChatSidebar.jsx'
import ChatArea from '../components/chat/ChatArea.jsx'
import MessageSearchDrawer from '../components/chat/MessageSearchDrawer.jsx'
import FriendsSection from '../components/chat/FriendsSection.jsx'
import NotificationsSection from '../components/chat/NotificationsSection.jsx'
import WorldChatDrawer from '../components/chat/WorldChatDrawer.jsx'
import ConfirmActionDialog from '../components/chat/dialogs/ConfirmActionDialog.jsx'
import RevokeMessageDialog from '../components/chat/dialogs/RevokeMessageDialog.jsx'
import ReportUserDialog from '../components/chat/dialogs/ReportUserDialog.jsx'
import { displayName, isPersonOnline } from '../components/chat/Avatar.jsx'
import { useAuth } from '../context/auth-context.js'
import { useLanguage } from '../context/language-context.js'
import { useChatAudio } from '../hooks/useChatAudio.js'
import { useChatConnections } from '../hooks/useChatConnections.js'
import { useChatRealtime } from '../hooks/useChatRealtime.js'
import { useContextMenu } from '../hooks/useContextMenu.js'
import { useConversationMessages } from '../hooks/useConversationMessages.js'
import { useMessageSearch } from '../hooks/useMessageSearch.js'
import { useNotifications } from '../hooks/useNotifications.js'
import { useUserDiscovery } from '../hooks/useUserDiscovery.js'
import { isAdminAccount, isAdminUser } from '../services/authorization.js'
import {
  initialChatSection,
  isIncomingMessageBlocked,
  normalizeSearchValue,
  upsertMessage,
} from '../components/chat/chatUtils.js'
import '../styles/chat.css'

export const ACTIVE_CONVERSATION_STORAGE_KEY = 'chatweb-active-conversation'

function ChatPage() {
  const { user: currentUser } = useAuth()
  const { language, t } = useLanguage()
  const { playNotificationSound, playInboxSound } = useChatAudio()
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedUser, setSelectedUser] = useState(null)
  const [activeSection, setActiveSection] = useState(initialChatSection)
  const [conversationQuery, setConversationQuery] = useState('')
  const [worldOpen, setWorldOpen] = useState(() => new URLSearchParams(window.location.search).get('world') === '1')
  const [worldDraft, setWorldDraft] = useState('')
  const [toast, setToast] = useState(null)
  const [conversationMenuOpen, setConversationMenuOpen] = useState(false)
  const [confirmConversationAction, setConfirmConversationAction] = useState(null)
  const [reportDialogOpen, setReportDialogOpen] = useState(false)

  const selectedRef = useRef(null)
  const conversationMenuRef = useRef(null)
  const isAdmin = isAdminUser(currentUser)
  const currentUsernameKey = String(currentUser?.username || '').trim().toLocaleLowerCase('en-US')

  const showToast = useCallback((message, tone = 'success') => {
    setToast({ message, tone, id: Date.now() })
  }, [])

  useEffect(() => {
    if (!toast) return undefined
    const timer = window.setTimeout(() => setToast(null), 3600)
    return () => window.clearTimeout(timer)
  }, [toast])

  const { contextMenu, openContextMenu, closeContextMenu } = useContextMenu()

  const connectionsRef = useRef(null)
  const messagesStateRef = useRef(null)

  const removeConversationLocally = useCallback((username) => {
    connectionsRef.current?.setFriends((cur) => cur.filter((f) => f.username !== username))
    messagesStateRef.current?.setMessagesByUser((cur) => { const next = { ...cur }; delete next[username]; return next })
    if (selectedRef.current?.username === username) {
      selectedRef.current = null
      setSelectedUser(null)
      try {
        localStorage.removeItem(ACTIVE_CONVERSATION_STORAGE_KEY)
      } catch {}
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev)
        next.delete('user')
        return next
      }, { replace: true })
    }
    setConversationMenuOpen(false)
    setConfirmConversationAction(null)
  }, [setSearchParams])

  const connections = useChatConnections({
    currentUser, showToast, t, onAfterRemoveConversation: removeConversationLocally,
  })

  const {
    friends, setFriends, connectionsLoaded, friendRequests, sentRequests, blockedUsers,
    blockedMessageIntervals, actionPending, addFriend, acceptFriend,
    removeFriendRelation, unblockServerUser, performConversationAction,
    unblockSelectedConversation, scheduleConnectionSync,
  } = connections

  const friendNames = useMemo(() => new Set(friends.map((f) => f.username)), [friends])
  const sentNames = useMemo(() => new Set(sentRequests.map((p) => p.username)), [sentRequests])
  const blockedNames = useMemo(() => new Set(blockedUsers.map((p) => p.username)), [blockedUsers])
  const incomingRequestNames = useMemo(() => new Set(friendRequests.map((p) => p.username)), [friendRequests])
  const selectedUsername = selectedUser?.username || ''
  const selectedConversationBlocked = isIncomingMessageBlocked(blockedMessageIntervals, currentUser?.username, selectedUsername)

  const {
    searchQuery, setSearchQuery, searchType, setSearchType,
    searchResults, searching, suggestions, loadingSuggestions, loadSuggestions,
  } = useUserDiscovery({ activeSection, currentUsernameKey, language, showToast, t })

  const updatePeerPresence = useCallback((username, online) => {
    const norm = String(username || '').toLocaleLowerCase('en-US')
    if (!norm) return
    setFriends((cur) => cur.map((f) => f.username?.toLocaleLowerCase('en-US') === norm ? { ...f, online, isOnline: online } : f))
    setSelectedUser((cur) => cur?.username?.toLocaleLowerCase('en-US') === norm ? { ...cur, online, isOnline: online } : cur)
  }, [setFriends])

  const {
    notifications,
    unreadCount: unreadNotificationCount,
    loading: notificationsLoading,
    loadingMore: notificationsLoadingMore,
    hasMore: notificationsHasMore,
    loadMore: loadMoreNotifications,
    markAsRead: markNotificationRead,
    markAllAsRead: markAllNotificationsRead,
    handleRealtimeNotification,
  } = useNotifications({
    enabled: Boolean(currentUser),
    playNotificationSound,
    showToast,
  })

  const realtime = useChatRealtime({
    currentUser, selectedUser, activeSection, blockedMessageIntervals, language,
    playNotificationSound, playInboxSound, showToast, t,
    setMessagesByUser: (updater) => messagesState.setMessagesByUser(updater),
    messagesByUserRef: { get current() { return messagesState.messagesByUser } },
    recordMessageEdit: (...args) => messagesState.recordMessageEdit(...args),
    removeRateLimitedMessage: (...args) => messagesState.removeRateLimitedMessage(...args),
    loadConversation: (...args) => messagesState.loadConversation(...args),
    scheduleConnectionSync, updatePeerPresence,
    onRealtimeNotification: handleRealtimeNotification,
  })

  const {
    connectionState, sendPrivateMessage, sendWorldMessage, unreadCounts,
    typingUsers, worldMessages, worldCursor, worldHasMore, worldNotifications,
    loadWorldHistory, sendTypingStatus, sendReactionControl, markAsRead, sendRealtimeReceipt,
  } = realtime

  const selectedUserIsTyping = Boolean(typingUsers[selectedUsername])

  const messagesState = useConversationMessages({
    user: currentUser, selectedUser, activeSection, connectionState, blockedMessageIntervals,
    sendPrivateMessage, sendTypingStatus, sendReactionControl, showToast, t,
    selectedUserIsTyping,
  })

  useEffect(() => {
    connectionsRef.current = connections
    messagesStateRef.current = messagesState
  })

  const {
    messageSearchOpen, messageSearchQuery, messageSearchResults, messageSearchHasMore,
    messageSearchCursor, messageSearchLoading, openMessageSearch, closeMessageSearch,
    focusSearchResult, searchConversationMessages, setMessageSearchQuery,
  } = useMessageSearch({
    user: currentUser, selectedUser, blockedMessageIntervals, showToast, t,
    onFocusMessage: (msg) => {
      if (selectedUser?.username) {
        messagesState.setMessagesByUser((cur) => ({ ...cur, [selectedUser.username]: upsertMessage(cur[selectedUser.username] || [], msg) }))
      }
      messagesState.setDetailMessageId(msg.id)
    },
  })

  useEffect(() => { selectedRef.current = selectedUser }, [selectedUser])

  const totalUnreadMessages = useMemo(() => friends.reduce((total, friend) => {
    const unread = Number(unreadCounts[friend.username])
    return total + (Number.isFinite(unread) && unread > 0 ? unread : 0)
  }, 0), [friends, unreadCounts])

  const filteredFriends = useMemo(() => {
    const query = normalizeSearchValue(conversationQuery)
    if (!query) return friends
    return friends.filter((f) => normalizeSearchValue(`${displayName(f)} ${f.username || ''}`).includes(query))
  }, [conversationQuery, friends])

  const visibleSuggestions = useMemo(() => suggestions
    .filter((p) => String(p.username || '').trim().toLocaleLowerCase('en-US') !== currentUsernameKey
      && !isAdminAccount(p)
      && !friendNames.has(p.username) && !sentNames.has(p.username)
      && !blockedNames.has(p.username) && !incomingRequestNames.has(p.username))
    .sort((a, b) => Number(isPersonOnline(b)) - Number(isPersonOnline(a)))
    .slice(0, 8), [blockedNames, currentUsernameKey, friendNames, incomingRequestNames, sentNames, suggestions])

  const selectFriend = useCallback((friend) => {
    messagesStateRef.current?.setReactionPickerMessageId(null)
    messagesStateRef.current?.setDetailMessageId(null)
    messagesStateRef.current?.setEditHistoryMessageId(null)
    closeContextMenu()
    selectedRef.current = friend
    setSelectedUser(friend)
    setActiveSection('chat')
    setWorldOpen(false)
    if (friend?.username) {
      markAsRead(friend.username, true)
      sendRealtimeReceipt(friend.username, 'READ')
      messagesStateRef.current?.scrollToBottom(true)
      try {
        localStorage.setItem(ACTIVE_CONVERSATION_STORAGE_KEY, friend.username)
      } catch {}
      setSearchParams((prev) => {
        if (prev.get('user') === friend.username) return prev
        const next = new URLSearchParams(prev)
        next.set('user', friend.username)
        return next
      }, { replace: true })
    }
  }, [closeContextMenu, markAsRead, sendRealtimeReceipt, setSearchParams])

  const handleNotificationClick = useCallback((notification) => {
    if (!notification) return
    void markNotificationRead(notification.id)
    const targetUsername = notification.targetType === 'USER'
      ? (notification.targetId || notification.senderUsername)
      : (notification.senderUsername || notification.targetId)

    if (!targetUsername) return

    const friend = friends.find((f) => (
      String(f.username || '').toLocaleLowerCase('en-US') === String(targetUsername).toLocaleLowerCase('en-US')
    ))
    if (friend) {
      selectFriend(friend)
    } else {
      selectFriend({
        username: targetUsername,
        firstName: notification.senderFirstName || '',
        lastName: notification.senderLastName || '',
        avatar: notification.senderAvatar || null,
      })
    }
  }, [friends, markNotificationRead, selectFriend])

  const clearActiveConversation = useCallback(() => {
    selectedRef.current = null
    setSelectedUser(null)
    try {
      localStorage.removeItem(ACTIVE_CONVERSATION_STORAGE_KEY)
    } catch {}
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.delete('user')
      return next
    }, { replace: true })
  }, [setSearchParams])

  const restoredRef = useRef(false)

  useEffect(() => {
    if (!connectionsLoaded) return

    const queryUser = searchParams.get('user')?.trim()
    const storedUser = (() => {
      try {
        return localStorage.getItem(ACTIVE_CONVERSATION_STORAGE_KEY)?.trim()
      } catch {
        return null
      }
    })()

    // If no queryUser in URL on initial mount, check if localStorage has one
    if (!queryUser && !restoredRef.current && storedUser) {
      restoredRef.current = true
      const match = friends.find((f) => (
        String(f.username || '').toLocaleLowerCase('en-US') === storedUser.toLocaleLowerCase('en-US')
      ))
      if (match) {
        selectFriend(match)
        return
      } else {
        try { localStorage.removeItem(ACTIVE_CONVERSATION_STORAGE_KEY) } catch {}
      }
    }

    restoredRef.current = true

    if (queryUser) {
      if (selectedUser?.username?.toLocaleLowerCase('en-US') === queryUser.toLocaleLowerCase('en-US')) {
        return
      }
      const match = friends.find((f) => (
        String(f.username || '').toLocaleLowerCase('en-US') === queryUser.toLocaleLowerCase('en-US')
      ))
      if (match) {
        selectFriend(match)
      } else {
        try { localStorage.removeItem(ACTIVE_CONVERSATION_STORAGE_KEY) } catch {}
        setSearchParams((prev) => {
          const next = new URLSearchParams(prev)
          next.delete('user')
          return next
        }, { replace: true })
      }
    }
  }, [connectionsLoaded, friends, searchParams, selectFriend, selectedUser?.username, setSearchParams])

  const latestWorldMessage = worldMessages.length ? worldMessages[worldMessages.length - 1] : null

  return (
    <main className="chat-app">
      <AppRail
        activeSection={activeSection} online={connectionState === 'connected'}
        totalUnreadMessages={totalUnreadMessages} friendRequestCount={friendRequests.length}
        worldNotificationCount={worldNotifications.length}
        unreadNotificationCount={unreadNotificationCount}
        onSelectSection={setActiveSection}
        onOpenWorld={() => setWorldOpen(true)}
      />

      <ChatSidebar
        activeSection={activeSection} conversationQuery={conversationQuery}
        onConversationQueryChange={setConversationQuery} friends={friends}
        filteredFriends={filteredFriends} selectedUser={selectedUser}
        onSelectFriend={selectFriend} unreadCounts={unreadCounts} typingUsers={typingUsers}
        currentUserWithPresence={{ ...currentUser, isOnline: connectionState === 'connected' }}
        currentUser={currentUser} isAdmin={isAdmin} t={t}
        onOpenFriendsSection={() => setActiveSection('friends')}
      />

      <ChatArea
        activeSection={activeSection} selectedUser={selectedUser} currentUser={currentUser}
        connectionState={connectionState} selectedConversationBlocked={selectedConversationBlocked}
        selectedUserIsTyping={selectedUserIsTyping} conversationMenuOpen={conversationMenuOpen}
        conversationMenuRef={conversationMenuRef} latestWorldMessage={latestWorldMessage}
        language={language} t={t} onOpenWorld={() => setWorldOpen(true)}
        onBack={clearActiveConversation}
        onToggleMenu={() => setConversationMenuOpen((cur) => !cur)}
        onOpenSearch={() => { setConversationMenuOpen(false); openMessageSearch() }}
        onUnblockConversation={() => { void unblockSelectedConversation(selectedUser); setConversationMenuOpen(false) }}
        onConfirmBlock={() => { setConversationMenuOpen(false); setConfirmConversationAction('block') }}
        onConfirmUnfriend={() => { setConversationMenuOpen(false); setConfirmConversationAction('unfriend') }}
        onOpenReport={() => { setConversationMenuOpen(false); setReportDialogOpen(true) }}
        onOpenFriendsSection={() => setActiveSection('friends')}
        messagesState={messagesState} contextMenu={contextMenu}
        closeContextMenu={closeContextMenu} openContextMenu={openContextMenu} showToast={showToast}
      />

      {activeSection === 'friends' && (
        <FriendsSection
          searchQuery={searchQuery} onSearchQueryChange={setSearchQuery}
          searchType={searchType} onSearchTypeChange={setSearchType}
          friendRequests={friendRequests} sentRequests={sentRequests} blockedUsers={blockedUsers}
          visibleSuggestions={visibleSuggestions} searchResults={searchResults} searching={searching}
          loadingSuggestions={loadingSuggestions} friendNames={friendNames} sentNames={sentNames}
          blockedNames={blockedNames} t={t}
          onAcceptFriend={async (p) => { await acceptFriend(p); selectFriend(p) }}
          onRemoveFriendRelation={removeFriendRelation} onUnblockUser={unblockServerUser}
          onAddFriend={addFriend} onSelectFriend={selectFriend} onRefreshSuggestions={loadSuggestions}
        />
      )}

      <MessageSearchDrawer
        isOpen={messageSearchOpen && Boolean(selectedUser)} selectedUser={selectedUser}
        user={currentUser} query={messageSearchQuery} results={messageSearchResults}
        loading={messageSearchLoading} hasMore={messageSearchHasMore} cursor={messageSearchCursor}
        language={language} t={t} onClose={closeMessageSearch} onChangeQuery={setMessageSearchQuery}
        onFocusResult={focusSearchResult} onLoadMore={searchConversationMessages}
      />

      {activeSection === 'notifications' && (
        <NotificationsSection
          notifications={notifications}
          unreadCount={unreadNotificationCount}
          loading={notificationsLoading}
          loadingMore={notificationsLoadingMore}
          hasMore={notificationsHasMore}
          onLoadMore={loadMoreNotifications}
          onMarkAllAsRead={markAllNotificationsRead}
          onNotificationClick={handleNotificationClick}
          friendRequests={friendRequests}
          worldNotifications={worldNotifications}
          language={language}
          t={t}
          onAcceptFriend={async (p) => { await acceptFriend(p); selectFriend(p) }}
          onOpenWorld={() => setWorldOpen(true)}
        />
      )}

      <WorldChatDrawer
        isOpen={worldOpen} worldMessages={worldMessages} worldHasMore={worldHasMore}
        worldCursor={worldCursor} worldDraft={worldDraft} isAdmin={isAdmin}
        language={language} t={t} onClose={() => setWorldOpen(false)}
        onLoadOlder={loadWorldHistory} onChangeDraft={setWorldDraft}
        onSubmitBroadcast={(e) => {
          e.preventDefault()
          const c = worldDraft.trim()
          if (!c || !isAdmin) return
          if (sendWorldMessage({ content: c, survivalTime: null })) setWorldDraft('')
          else showToast(t('socketError'), 'error')
        }}
      />

      <ConfirmActionDialog
        action={confirmConversationAction} selectedUser={selectedUser} pending={actionPending}
        t={t} onClose={() => setConfirmConversationAction(null)}
        onConfirm={async () => {
          await performConversationAction(selectedUser, confirmConversationAction)
          setConfirmConversationAction(null)
        }}
      />

      <RevokeMessageDialog
        message={messagesState.revokeTargetMessage} pending={messagesState.messageActionPending}
        t={t} onClose={() => !messagesState.messageActionPending && messagesState.setRevokeTargetMessage(null)}
        onConfirm={messagesState.revokeChatMessage}
      />

      <ReportUserDialog
        isOpen={reportDialogOpen && Boolean(selectedUser)} selectedUser={selectedUser}
        t={t} onClose={() => setReportDialogOpen(false)}
      />

      {toast && (
        <div className={`chat-toast chat-toast--${toast.tone}`} role="status">
          <span>{toast.tone === 'error' ? '!' : '✓'}</span>{toast.message}
        </div>
      )}
    </main>
  )
}

export default ChatPage
