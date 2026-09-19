import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import { MESSAGE_EMOJI_OPTIONS, getQuotedMessagePreview, getQuotedSenderName } from './chatUtils.js'

export const MessageComposer = React.memo(function MessageComposer({
  messageDraft,
  rateLimitRemaining = 0,
  uploadingMedia = false,
  connectionState,
  emojiPickerOpen = false,
  mediaInputRef,
  messageInputRef,
  replyingToMessage = null,
  currentUser,
  selectedUser,
  t,
  onSubmit,
  onDraftChange,
  onMediaSelect,
  onToggleEmojiPicker,
  onInsertEmoji,
  onCancelReply,
}) {
  const isConnected = connectionState === 'connected'
  const isLocked = rateLimitRemaining > 0

  return (
    <div className="message-composer-wrap">
      {replyingToMessage && (
        <div className="composer-reply-banner" role="region" aria-label={t('replyingTo')}>
          <div className="composer-reply-banner__indicator" />
          <div className="composer-reply-banner__icon">
            <ChatIcon name="reply" size={16} />
          </div>
          <div className="composer-reply-banner__content">
            <div className="composer-reply-banner__header">
              <span className="composer-reply-banner__label">{t('replyingTo')}</span>
              <strong className="composer-reply-banner__sender">
                {getQuotedSenderName(replyingToMessage, currentUser, selectedUser, t)}
              </strong>
            </div>
            <div className="composer-reply-banner__preview">
              {['IMAGE', 'VIDEO'].includes(String(replyingToMessage.contentType || '').toUpperCase()) && (
                <ChatIcon name="image" size={13} />
              )}
              <span>{getQuotedMessagePreview(replyingToMessage, t)}</span>
            </div>
          </div>
          <button
            type="button"
            className="composer-reply-banner__close"
            aria-label={t('cancelReply')}
            title={t('cancelReply')}
            onClick={onCancelReply}
          >
            <ChatIcon name="close" size={14} />
          </button>
        </div>
      )}
      <form className={`message-composer-bar${replyingToMessage ? ' has-reply' : ''}`} onSubmit={onSubmit}>
        {isLocked && (
          <div className="message-composer-lock" role="status">
            <strong>{t('rateLimitActive')}</strong>
            <span>{rateLimitRemaining}s</span>
          </div>
        )}
        <input
          ref={mediaInputRef}
          className="media-input"
          type="file"
          accept="image/*,video/*"
          onChange={onMediaSelect}
        />
        <button
          className={`attachment-button${uploadingMedia ? ' is-uploading' : ''}`}
          type="button"
          aria-label={uploadingMedia ? t('uploadingMedia') : t('uploadMedia')}
          title={uploadingMedia ? t('uploadingMedia') : t('uploadMedia')}
          disabled={uploadingMedia || !isConnected || isLocked}
          onClick={() => mediaInputRef.current?.click()}
        >
          <ChatIcon name="image" />
        </button>
        <input
          ref={messageInputRef}
          value={messageDraft}
          onChange={onDraftChange}
          onKeyDown={(event) => {
            if (event.key === 'Escape' && replyingToMessage) {
              event.preventDefault()
              onCancelReply?.()
            }
          }}
          placeholder={isLocked ? t('rateLimitActive') : t('messagePlaceholder')}
          aria-label={t('messagePlaceholder')}
          disabled={isLocked}
        />
      <span className="composer-emoji-anchor">
        <button
          type="button"
          aria-label="Chọn emoji"
          aria-expanded={emojiPickerOpen}
          disabled={isLocked}
          onClick={onToggleEmojiPicker}
        >
          <ChatIcon name="smile" />
        </button>
        {emojiPickerOpen && (
          <div className="composer-emoji-picker" role="menu" aria-label="Chọn emoji">
            {MESSAGE_EMOJI_OPTIONS.map((emoji) => (
              <button
                key={emoji}
                type="button"
                role="menuitem"
                aria-label={`Chèn ${emoji}`}
                onClick={() => onInsertEmoji(emoji)}
              >
                {emoji}
              </button>
            ))}
          </div>
        )}
      </span>
      <button
        className="composer-send"
        type="submit"
        aria-label={t('send')}
        disabled={!messageDraft.trim() || !isConnected || isLocked}
      >
        <ChatIcon name="send" size={18} />
      </button>
      </form>
    </div>
  )
})

export default MessageComposer
