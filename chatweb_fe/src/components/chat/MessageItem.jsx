import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import { Avatar } from './Avatar.jsx'
import {
  formatDateTime,
  formatTime,
  isMessageDeleted,
  isMessageEdited,
  summarizeReactions,
  REACTION_OPTIONS,
} from './chatUtils.js'

function areMessagePropsEqual(prevProps, nextProps) {
  if (prevProps.message !== nextProps.message) {
    if (
      prevProps.message?.id !== nextProps.message?.id ||
      prevProps.message?.localId !== nextProps.message?.localId ||
      prevProps.message?.status !== nextProps.message?.status ||
      prevProps.message?.clientFailed !== nextProps.message?.clientFailed ||
      prevProps.message?.content !== nextProps.message?.content ||
      prevProps.message?.edited !== nextProps.message?.edited ||
      prevProps.message?.isEdited !== nextProps.message?.isEdited ||
      prevProps.message?.deleted !== nextProps.message?.deleted ||
      prevProps.message?.isDeleted !== nextProps.message?.isDeleted ||
      prevProps.message?.failureAttempt !== nextProps.message?.failureAttempt ||
      prevProps.message?.fileUrl !== nextProps.message?.fileUrl ||
      prevProps.message?.reactions !== nextProps.message?.reactions
    ) {
      return false
    }
  }

  return (
    prevProps.isMine === nextProps.isMine &&
    prevProps.isGrouped === nextProps.isGrouped &&
    prevProps.isSearchTarget === nextProps.isSearchTarget &&
    prevProps.showActions === nextProps.showActions &&
    prevProps.showDetails === nextProps.showDetails &&
    prevProps.isReactionPickerOpen === nextProps.isReactionPickerOpen &&
    prevProps.isEditHistoryOpen === nextProps.isEditHistoryOpen &&
    prevProps.isEditing === nextProps.isEditing &&
    prevProps.editingContent === nextProps.editingContent &&
    prevProps.reactionSubmitting === nextProps.reactionSubmitting &&
    prevProps.messageActionPending === nextProps.messageActionPending &&
    prevProps.connectionState === nextProps.connectionState &&
    prevProps.language === nextProps.language &&
    prevProps.selectedUser?.username === nextProps.selectedUser?.username &&
    prevProps.selectedUser?.avatar === nextProps.selectedUser?.avatar
  )
}

export const MessageItem = React.memo(function MessageItem({
  message,
  messageKey,
  selectedUser,
  user,
  isMine,
  isGrouped,
  isSearchTarget,
  showActions,
  showDetails,
  isReactionPickerOpen,
  isEditHistoryOpen,
  isEditing,
  editingContent,
  reactionSubmitting,
  messageActionPending,
  connectionState,
  language,
  editHistory = [],
  t,
  onContextMenu,
  onBubbleClick,
  onBubbleKeyDown,
  onToggleReactionPicker,
  onToggleEditHistory,
  onSelectReaction,
  onRetry,
  onBeginEdit,
  onSaveEdit,
  onCancelEdit,
  onChangeEditContent,
  onRevokeClick,
}) {
  const reactionSummary = summarizeReactions(message.reactions)
  const currentUserReaction = message.reactions?.[user?.username]
  const deleted = isMessageDeleted(message)
  const edited = isMessageEdited(message)

  return (
    <div
      id={message.id ? `chat-message-${message.id}` : undefined}
      className={`message-row${isMine ? ' is-mine' : ''}${isGrouped ? ' is-grouped' : ''}${isSearchTarget ? ' is-search-target' : ''}`}
      onContextMenu={(event) => onContextMenu(event, message, messageKey)}
    >
      {!isMine && !isGrouped && <Avatar person={selectedUser} size="tiny" />}
      <div className="message-content message-reaction-anchor">
        {isEditHistoryOpen && (
          <aside className="message-edit-history message-edit-history-anchor" aria-label={t('editHistoryTitle')}>
            <header>
              <strong>{t('editHistoryTitle')}</strong>
              <button
                type="button"
                aria-label={t('cancel')}
                onClick={() => onToggleEditHistory(null)}
              >
                <ChatIcon name="close" size={13} />
              </button>
            </header>
            {editHistory.length > 0 ? (
              <div>
                {[...editHistory].reverse().map((entry, historyIndex) => (
                  <article key={`${entry.editedAt}-${historyIndex}`}>
                    <span>{t('previousVersion')}</span>
                    <p>{entry.content}</p>
                    <time>{formatDateTime(entry.editedAt, language)}</time>
                  </article>
                ))}
              </div>
            ) : (
              <p className="message-edit-history__empty">{t('editHistoryUnavailable')}</p>
            )}
          </aside>
        )}

        <div
          className={`message-bubble${message.clientFailed ? ' is-failed' : ''}${deleted ? ' is-deleted' : ''}${!message.id || deleted ? ' is-static' : ''}`}
          role={message.id && !deleted ? 'button' : undefined}
          tabIndex={message.id && !deleted ? 0 : undefined}
          aria-label={message.id && !deleted ? t('reactToMessage') : undefined}
          onClick={message.id && !deleted ? () => onBubbleClick(messageKey) : undefined}
          onKeyDown={message.id && !deleted ? (event) => onBubbleKeyDown(event, messageKey) : undefined}
        >
          {deleted ? (
            t('deletedMessage')
          ) : (
            <>
              {String(message.contentType || '').toUpperCase() === 'IMAGE' && message.fileUrl && (
                <img
                  className="message-media message-media--image"
                  src={message.fileUrl}
                  alt={message.fileName || t('sharedImage')}
                  loading="lazy"
                />
              )}
              {String(message.contentType || '').toUpperCase() === 'VIDEO' && message.fileUrl && (
                <video
                  className="message-media message-media--video"
                  src={message.fileUrl}
                  controls
                  preload="metadata"
                  onClick={(event) => event.stopPropagation()}
                >
                  {t('videoUnsupported')}
                </video>
              )}
              {message.content && <span className="message-text">{message.content}</span>}
              {message.fileName && <span className="message-file-name">{message.fileName}</span>}
            </>
          )}
        </div>

        {(edited || editHistory.length > 0) && !deleted && (
          <button
            className="message-edited-label message-edit-history-anchor"
            type="button"
            aria-expanded={isEditHistoryOpen}
            onClick={(event) => onToggleEditHistory(event, messageKey)}
          >
            {t('editedLabel')}
          </button>
        )}

        {isReactionPickerOpen && (
          <div className="reaction-picker" role="menu" aria-label={t('reactToMessage')}>
            {REACTION_OPTIONS.map((reaction) => (
              <button
                key={reaction.type}
                className={currentUserReaction === reaction.type ? 'is-active' : ''}
                type="button"
                role="menuitem"
                disabled={reactionSubmitting}
                aria-label={reaction.type}
                onClick={() => onSelectReaction(message, reaction.type)}
              >
                {reaction.emoji}
              </button>
            ))}
          </div>
        )}

        {reactionSummary.length > 0 && (
          <button
            className="message-reactions"
            type="button"
            onClick={() => onToggleReactionPicker(messageKey)}
            aria-label={t('reactToMessage')}
          >
            {reactionSummary.map((reaction) => (
              <span key={reaction.type}>
                {reaction.emoji}
                {reaction.count > 1 && <b>{reaction.count}</b>}
              </span>
            ))}
          </button>
        )}

        {message.clientFailed && (
          <div className="message-send-error" role="alert">
            <span>{t('messageFailed')}</span>
            <button
              type="button"
              disabled={connectionState !== 'connected'}
              onClick={() => onRetry(message)}
            >
              {t('retry')}
            </button>
          </div>
        )}

        {showActions && !deleted && (
          isEditing ? (
            <form className="message-edit-form" onSubmit={(event) => onSaveEdit(event, message)}>
              <input
                autoFocus
                maxLength="10000"
                value={editingContent}
                onChange={(event) => onChangeEditContent(event.target.value)}
              />
              <button
                type="submit"
                disabled={messageActionPending || !editingContent.trim()}
              >
                {t('save')}
              </button>
              <button type="button" onClick={onCancelEdit}>
                {t('cancel')}
              </button>
            </form>
          ) : (
            <div className="message-actions">
              {isMine && String(message.contentType || 'TEXT').toUpperCase() === 'TEXT' && (
                <button
                  type="button"
                  disabled={messageActionPending}
                  onClick={() => onBeginEdit(message)}
                >
                  {t('editMessage')}
                </button>
              )}
              {isMine && (
                <button
                  className="is-danger"
                  type="button"
                  disabled={messageActionPending}
                  onClick={() => onRevokeClick(message)}
                >
                  {t('revokeMessage')}
                </button>
              )}
            </div>
          )
        )}

        {showDetails && (
          <small className="message-details">
            {formatTime(message.timestamp, language)}
            {isMine && (
              <> · {message.clientFailed ? t('messageFailed') : t(message.status === 'READ' ? 'read' : message.status === 'DELIVERED' ? 'delivered' : message.status === 'SENDING' ? 'sending' : 'sent')}</>
            )}
          </small>
        )}
      </div>
    </div>
  )
}, areMessagePropsEqual)

export default MessageItem
