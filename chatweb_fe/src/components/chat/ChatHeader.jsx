import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import { Avatar, displayName, isPersonOnline } from './Avatar.jsx'

export const ChatHeader = React.memo(function ChatHeader({
  selectedUser,
  connectionState,
  conversationMenuOpen,
  conversationMenuRef,
  selectedConversationBlocked,
  isTyping = false,
  t,
  onBack,
  onToggleMenu,
  onOpenSearch,
  onUnblock,
  onConfirmBlock,
  onConfirmUnfriend,
  onOpenReport,
}) {
  if (!selectedUser) return null

  return (
    <header className="chat-header">
      <button className="mobile-back" type="button" aria-label="Back" onClick={onBack}>
        <ChatIcon name="arrowLeft" />
      </button>
      <Avatar person={selectedUser} size="medium" showStatus />
      <span>
        <strong>{displayName(selectedUser)}</strong>
        <small className={isPersonOnline(selectedUser) ? 'is-online' : ''}>
          {isTyping ? t('typing') : (isPersonOnline(selectedUser) ? t('online') : t('offline'))}
        </small>
      </span>
      <div className={`connection-pill connection-pill--${connectionState}`}>
        <i />
        {t(connectionState === 'connected' ? 'connected' : connectionState === 'disconnected' ? 'disconnected' : 'reconnecting')}
      </div>
      <div className="conversation-actions" ref={conversationMenuRef}>
        <button
          className="conversation-actions__trigger"
          type="button"
          aria-label={t('conversationOptions')}
          aria-expanded={conversationMenuOpen}
          onClick={onToggleMenu}
        >
          <ChatIcon name="more" />
        </button>
        {conversationMenuOpen && (
          <div className="conversation-menu">
            <button type="button" onClick={onOpenSearch}>
              <ChatIcon name="search" size={17} />
              <span>{t('searchMessages')}</span>
            </button>
            <span className="conversation-menu__divider" />
            {selectedConversationBlocked ? (
              <button type="button" onClick={onUnblock}>
                <ChatIcon name="block" size={17} />
                <span>{t('unblockMessages')}</span>
              </button>
            ) : (
              <button type="button" onClick={onConfirmBlock}>
                <ChatIcon name="block" size={17} />
                <span>{t('blockMessages')}</span>
              </button>
            )}
            <button type="button" onClick={onConfirmUnfriend}>
              <ChatIcon name="trash" size={17} />
              <span>{t('unfriend')}</span>
            </button>
            <button className="is-danger" type="button" onClick={onOpenReport}>
              <ChatIcon name="flag" size={17} />
              <span>{t('reportUser')}</span>
            </button>
          </div>
        )}
      </div>
    </header>
  )
})

export default ChatHeader
