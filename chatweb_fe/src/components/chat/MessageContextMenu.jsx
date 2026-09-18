import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import { REACTION_OPTIONS } from './chatUtils.js'

export const MessageContextMenu = React.memo(function MessageContextMenu({
  menu,
  user,
  t,
  reactionOptions = REACTION_OPTIONS,
  reactionSubmittingId,
  messageActionPending,
  onClose,
  onToggleReaction,
  onCopy,
  onBeginEdit,
  onRevoke,
}) {
  if (!menu) return null

  const { message, x, y } = menu
  const isMine = message.sender === user?.username
  const isText = String(message.contentType || 'TEXT').toUpperCase() === 'TEXT'

  return (
    <div
      className="message-context-menu"
      role="menu"
      aria-label={t('messageMenu')}
      style={{ left: x, top: y }}
      onContextMenu={(event) => event.preventDefault()}
    >
      <span className="message-context-menu__label">{t('reactToMessage')}</span>
      <div className="message-context-menu__reactions">
        {reactionOptions.map((reaction) => (
          <button
            key={reaction.type}
            className={message.reactions?.[user?.username] === reaction.type ? 'is-active' : ''}
            type="button"
            role="menuitem"
            aria-label={reaction.type}
            disabled={reactionSubmittingId === message.id}
            onClick={() => {
              onClose()
              void onToggleReaction(message, reaction.type)
            }}
          >
            {reaction.emoji}
          </button>
        ))}
      </div>
      <span className="message-context-menu__divider" />
      {Boolean(message.content) && (
        <button type="button" role="menuitem" onClick={() => void onCopy(message)}>
          <ChatIcon name="copy" size={16} />
          <span>{t('copyMessage')}</span>
        </button>
      )}
      {isMine && isText && (
        <button type="button" role="menuitem" disabled={messageActionPending} onClick={() => onBeginEdit(message)}>
          <ChatIcon name="edit" size={16} />
          <span>{t('editMessage')}</span>
        </button>
      )}
      {isMine && (
        <button
          className="is-danger"
          type="button"
          role="menuitem"
          disabled={messageActionPending}
          onClick={() => {
            onClose()
            onRevoke(message)
          }}
        >
          <ChatIcon name="trash" size={16} />
          <span>{t('revokeMessage')}</span>
        </button>
      )}
    </div>
  )
})

export default MessageContextMenu
