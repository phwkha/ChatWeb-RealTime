import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import { MESSAGE_EMOJI_OPTIONS } from './chatUtils.js'

export const MessageComposer = React.memo(function MessageComposer({
  messageDraft,
  rateLimitRemaining = 0,
  uploadingMedia = false,
  connectionState,
  emojiPickerOpen = false,
  mediaInputRef,
  messageInputRef,
  t,
  onSubmit,
  onDraftChange,
  onMediaSelect,
  onToggleEmojiPicker,
  onInsertEmoji,
}) {
  const isConnected = connectionState === 'connected'
  const isLocked = rateLimitRemaining > 0

  return (
    <form className="message-composer-bar" onSubmit={onSubmit}>
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
  )
})

export default MessageComposer
