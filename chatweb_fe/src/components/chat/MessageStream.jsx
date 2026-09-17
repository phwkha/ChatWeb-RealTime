import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import { Avatar, displayName } from './Avatar.jsx'
import MessageItem from './MessageItem.jsx'
import { messageEditHistoryKey } from './chatUtils.js'

export const MessageStream = React.memo(function MessageStream({
  messageStreamRef,
  onScroll,
  loadingConversation,
  hasMore,
  loadingOlderMessages,
  onLoadOlder,
  activeMessages = [],
  selectedUser,
  user,
  isTyping,
  messagesEndRef,
  detailMessageId,
  setDetailMessageId,
  reactionPickerMessageId,
  setReactionPickerMessageId,
  reactionSubmittingId,
  editHistoryMessageId,
  setEditHistoryMessageId,
  messageEditHistory = {},
  searchTargetMessageId,
  editingMessageId,
  editingMessageContent,
  setEditingMessageId,
  setEditingMessageContent,
  messageActionPending,
  connectionState,
  language,
  t,
  onContextMenu,
  onToggleReaction,
  onRetry,
  onBeginEdit,
  onSaveEdit,
  onRevokeMessage,
  children,
}) {
  return (
    <div className="message-stream" ref={messageStreamRef} onScroll={onScroll}>
      {loadingConversation && (
        <div className="message-loading">
          <i /><i /><i />
        </div>
      )}

      {!loadingConversation && hasMore && (
        <button
          className="load-older"
          type="button"
          disabled={loadingOlderMessages}
          onClick={onLoadOlder}
        >
          <ChatIcon name="history" size={16} />
          {loadingOlderMessages ? t('loadingOlder') : t('loadOlder')}
        </button>
      )}

      {!loadingConversation && activeMessages.length === 0 && (
        <div className="conversation-start">
          <Avatar person={selectedUser} size="large" />
          <h2>{displayName(selectedUser)}</h2>
          <p>@{selectedUser?.username}</p>
        </div>
      )}

      {activeMessages.map((message, index) => {
        const isMine = message.sender === user?.username
        const previous = activeMessages[index - 1]
        const isGrouped = previous?.sender === message.sender
        const messageKey = message.id || message.localId || `${message.timestamp}-${index}`
        const showActions = detailMessageId === messageKey
        const showDetails = showActions || index === activeMessages.length - 1
        const editHistory = message.id
          ? (messageEditHistory[messageEditHistoryKey(user?.username, message.id)] || [])
          : []

        return (
          <MessageItem
            key={`${messageKey}-${message.failureAttempt || 0}`}
            message={message}
            messageKey={messageKey}
            selectedUser={selectedUser}
            user={user}
            isMine={isMine}
            isGrouped={isGrouped}
            isSearchTarget={searchTargetMessageId === message.id}
            showActions={showActions}
            showDetails={showDetails}
            isReactionPickerOpen={reactionPickerMessageId === messageKey}
            isEditHistoryOpen={editHistoryMessageId === messageKey}
            isEditing={editingMessageId === message.id}
            editingContent={editingMessageContent}
            reactionSubmitting={reactionSubmittingId === message.id}
            messageActionPending={messageActionPending}
            connectionState={connectionState}
            language={language}
            editHistory={editHistory}
            t={t}
            onContextMenu={onContextMenu}
            onBubbleClick={(key) => {
              setReactionPickerMessageId((current) => current === key ? null : key)
              setDetailMessageId((current) => current === key ? null : key)
            }}
            onBubbleKeyDown={(event, key) => {
              if (!['Enter', ' '].includes(event.key)) return
              event.preventDefault()
              setReactionPickerMessageId((current) => current === key ? null : key)
              setDetailMessageId((current) => current === key ? null : key)
            }}
            onToggleReactionPicker={(key) => {
              setReactionPickerMessageId(key)
              setDetailMessageId(key)
            }}
            onToggleEditHistory={(event, key) => {
              if (event) {
                event.stopPropagation()
                setReactionPickerMessageId(null)
                if (editHistoryMessageId !== key) {
                  event.currentTarget.closest('.message-row')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
                }
              }
              setEditHistoryMessageId((current) => current === key ? null : key)
            }}
            onSelectReaction={onToggleReaction}
            onRetry={onRetry}
            onBeginEdit={onBeginEdit}
            onSaveEdit={onSaveEdit}
            onCancelEdit={() => setEditingMessageId(null)}
            onChangeEditContent={setEditingMessageContent}
            onRevokeClick={onRevokeMessage}
          />
        )
      })}

      {isTyping && (
        <div className="typing-indicator">
          <Avatar person={selectedUser} size="tiny" />
          <span className="typing-indicator__bubble">
            <i /><i /><i />
            <small>{t('typing')}</small>
          </span>
        </div>
      )}

      <div ref={messagesEndRef} />
      {children}
    </div>
  )
})

export default MessageStream
