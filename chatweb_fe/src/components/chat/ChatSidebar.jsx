import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import { Avatar, displayName } from './Avatar.jsx'

export const ChatSidebar = React.memo(function ChatSidebar({
  activeSection,
  conversationQuery,
  onConversationQueryChange,
  friends = [],
  filteredFriends = [],
  selectedUser,
  onSelectFriend,
  unreadCounts = {},
  typingUsers = {},
  currentUserWithPresence,
  currentUser,
  isAdmin,
  t,
  onOpenFriendsSection,
}) {
  return (
    <aside className={`conversation-sidebar${activeSection !== 'chat' ? ' is-section-hidden' : ''}`}>
      <div className="conversation-sidebar__heading">
        <div>
          <span>{t('appName')}</span>
          <h1>{t('conversations')}</h1>
        </div>
      </div>
      <div className="conversation-search">
        <ChatIcon name="search" size={18} />
        <input
          type="search"
          value={conversationQuery}
          onChange={(event) => onConversationQueryChange(event.target.value)}
          placeholder={t('searchConversations')}
          aria-label={t('searchConversations')}
          autoComplete="off"
        />
      </div>
      <div className="conversation-filter">
        <button className="is-active" type="button">
          {t('all')}
        </button>
        <span>{filteredFriends.length}</span>
      </div>
      <div className="friend-list">
        {friends.length === 0 && (
          <div className="empty-friends">
            <ChatIcon name="users" size={26} />
            <p>{t('noFriends')}</p>
            <button type="button" onClick={onOpenFriendsSection}>
              {t('search')}
            </button>
          </div>
        )}
        {friends.length > 0 && filteredFriends.length === 0 && (
          <div className="empty-friends">
            <ChatIcon name="search" size={26} />
            <p>{t('noConversationResults')}</p>
          </div>
        )}
        {filteredFriends.map((friend) => (
          <button
            key={friend.username}
            className={`friend-row${selectedUser?.username === friend.username ? ' is-active' : ''}`}
            type="button"
            onClick={() => onSelectFriend(friend)}
          >
            <Avatar person={friend} showStatus />
            <span className="friend-row__body">
              <span>
                <strong>{displayName(friend)}</strong>
                <time>{unreadCounts[friend.username] ? t('newMessage') : ''}</time>
              </span>
              <small>{typingUsers[friend.username] ? t('typing') : `@${friend.username}`}</small>
            </span>
            {Boolean(unreadCounts[friend.username]) && (
              <b>{unreadCounts[friend.username] > 99 ? '99+' : unreadCounts[friend.username]}</b>
            )}
          </button>
        ))}
      </div>
      <div className="sidebar-profile">
        <Avatar person={currentUserWithPresence} size="small" showStatus />
        <span>
          <strong>{displayName(currentUser)}</strong>
          <small>{isAdmin ? t('admin') : t('member')}</small>
        </span>
      </div>
    </aside>
  )
})

export default ChatSidebar
