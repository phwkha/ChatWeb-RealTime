import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import { formatTime } from './chatUtils.js'

export const WorldChatDrawer = React.memo(function WorldChatDrawer({
  isOpen,
  worldMessages = [],
  worldHasMore = false,
  worldCursor = null,
  worldDraft = '',
  isAdmin = false,
  language,
  t,
  onClose,
  onLoadOlder,
  onChangeDraft,
  onSubmitBroadcast,
}) {
  if (!isOpen) return null

  return (
    <div
      className="panel-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <section
        className="side-panel world-panel"
        role="dialog"
        aria-modal="true"
        aria-label={t('worldHistory')}
      >
        <header>
          <div>
            <span>LIVE · CHATWEB</span>
            <h2>{t('worldHistory')}</h2>
          </div>
          <button type="button" onClick={onClose}>
            <ChatIcon name="close" />
          </button>
        </header>

        <div className="world-feed">
          {worldHasMore && (
            <button
              className="load-older"
              type="button"
              onClick={() => onLoadOlder(worldCursor, true)}
            >
              <ChatIcon name="history" size={16} />
              {t('loadOlder')}
            </button>
          )}

          {!worldMessages.length && (
            <div className="panel-empty">
              <ChatIcon name="globe" size={30} />
              <p>{t('worldEmpty')}</p>
            </div>
          )}

          {worldMessages.map((message, index) => (
            <article className="world-card" key={`${message.timestamp}-${index}`}>
              <span><ChatIcon name="globe" size={18} /></span>
              <div>
                <strong>
                  {message.sender || t('admin')}
                  <em>{t('admin')}</em>
                </strong>
                <p>{message.content}</p>
                <time>{formatTime(message.timestamp, language)}</time>
              </div>
            </article>
          ))}
        </div>

        {isAdmin && (
          <form className="world-composer" onSubmit={onSubmitBroadcast}>
            <label>{t('adminBroadcast')}</label>
            <div>
              <input
                value={worldDraft}
                onChange={(event) => onChangeDraft(event.target.value)}
                placeholder={t('broadcastPlaceholder')}
              />
              <button type="submit" disabled={!worldDraft.trim()}>
                <ChatIcon name="send" size={17} />
                {t('publish')}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  )
})

export default WorldChatDrawer
