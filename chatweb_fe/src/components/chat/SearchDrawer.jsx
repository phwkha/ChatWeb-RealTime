import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import { displayName } from './Avatar.jsx'

export const SearchDrawer = React.memo(function SearchDrawer({
  isOpen,
  selectedUser,
  user,
  t,
  language,
  formatDateTime,
  query,
  loading,
  results,
  hasMore,
  onClose,
  onChangeQuery,
  onSelectResult,
  onLoadMore,
}) {
  if (!isOpen || !selectedUser) return null

  return (
    <div
      className="panel-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <section className="side-panel message-search-panel" role="dialog" aria-modal="true" aria-label={t('searchMessages')}>
        <header>
          <div>
            <span>@{selectedUser.username}</span>
            <h2>{t('searchMessages')}</h2>
          </div>
          <button type="button" onClick={onClose}>
            <ChatIcon name="close" />
          </button>
        </header>
        <div className="search-input">
          <ChatIcon name="search" />
          <input
            autoFocus
            type="search"
            value={query}
            onChange={(event) => onChangeQuery(event.target.value)}
            placeholder={t('searchMessagesHint')}
          />
        </div>
        <div className="message-search-results">
          {!query.trim() && (
            <div className="panel-empty">
              <ChatIcon name="search" size={30} />
              <p>{t('typeToSearchMessages')}</p>
            </div>
          )}
          {loading && results.length === 0 && (
            <div className="panel-loading">
              <i /><i /><i />
            </div>
          )}
          {!loading && query.trim() && results.length === 0 && (
            <div className="panel-empty">
              <ChatIcon name="search" size={30} />
              <p>{t('noMessageResults')}</p>
            </div>
          )}
          {results.map((message) => (
            <button
              className="message-search-result"
              type="button"
              key={message.id}
              onClick={() => onSelectResult(message)}
            >
              <span>
                <strong>{message.sender === user?.username ? t('you') : displayName(selectedUser)}</strong>
                <time>{formatDateTime(message.timestamp, language)}</time>
              </span>
              <p>{message.content}</p>
            </button>
          ))}
          {hasMore && (
            <button className="load-older" type="button" disabled={loading} onClick={onLoadMore}>
              <ChatIcon name="history" size={16} />
              {t('loadMoreResults')}
            </button>
          )}
        </div>
      </section>
    </div>
  )
})

export default SearchDrawer
