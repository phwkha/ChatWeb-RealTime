import React, { useState } from 'react'
import ChatIcon from './ChatIcon.jsx'
import { formatRelativeTime } from './chatUtils.js'

function getSenderName(notification) {
  const parts = [notification.senderFirstName, notification.senderLastName].filter(Boolean)
  if (parts.length) return parts.join(' ').trim()
  return notification.senderUsername || ''
}

function getSenderInitials(notification) {
  const name = getSenderName(notification)
  if (!name) return 'CW'
  return name.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

function NotificationAvatar({ notification }) {
  const [avatarFailed, setAvatarFailed] = useState(false)
  if (notification.senderAvatar && !avatarFailed) {
    return (
      <span className="cw-avatar cw-avatar--small notification-avatar">
        <img
          src={notification.senderAvatar}
          alt=""
          onError={() => setAvatarFailed(true)}
        />
      </span>
    )
  }

  if (notification.senderUsername) {
    return (
      <span className="cw-avatar cw-avatar--small notification-avatar">
        <span>{getSenderInitials(notification)}</span>
      </span>
    )
  }

  return (
    <span>
      <ChatIcon name="bell" size={17} />
    </span>
  )
}

export const NotificationsSection = React.memo(function NotificationsSection({
  notifications = [],
  unreadCount = 0,
  loading = false,
  loadingMore = false,
  hasMore = false,
  onLoadMore,
  onMarkAllAsRead,
  onNotificationClick,
  language,
  t,
}) {
  const hasItems = notifications.length > 0

  return (
    <section className="app-section-page notifications-section-page" aria-label={t('notifications')}>
      <header className="app-section-page__header">
        <span><ChatIcon name="bell" size={22} /></span>
        <div className="notifications-header-main">
          <small>CHATWEB</small>
          <div className="notifications-header-title-row">
            <h1>{t('notifications')}</h1>
            {onMarkAllAsRead && (
              <button
                type="button"
                className="mark-all-read-btn"
                disabled={unreadCount === 0 || loading}
                onClick={onMarkAllAsRead}
                title={t('markAllAsRead')}
              >
                {t('markAllAsRead')}
              </button>
            )}
          </div>
          <p>{t('notificationsPageBody')}</p>
        </div>
      </header>

      <div className="app-section-page__content notifications-page__content">
        <div className="panel-section notifications-page__list">
          {notifications.map((item) => (
            <button
              className={`notification-card ${!item.isRead ? 'is-unread' : ''}`}
              type="button"
              key={item.id}
              onClick={() => onNotificationClick?.(item)}
            >
              <NotificationAvatar notification={item} />
              <div className="notification-card__body">
                <div className="notification-card__header">
                  <strong>{getSenderName(item) || t('admin')}</strong>
                  {!item.isRead && (
                    <span className="unread-dot" title={t('unreadNotification')} />
                  )}
                </div>
                <p>{item.content}</p>
                <time>{formatRelativeTime(item.createdAt, language)}</time>
              </div>
            </button>
          ))}

          {hasMore && onLoadMore && (
            <div className="notification-load-more-container">
              <button
                type="button"
                className="notification-load-more"
                disabled={loadingMore}
                onClick={onLoadMore}
              >
                {loadingMore ? t('loadingNotifications') : t('loadMore')}
              </button>
            </div>
          )}

          {!hasItems && !loading && (
            <div className="panel-empty">
              <ChatIcon name="bell" size={30} />
              <p>{t('noNotifications')}</p>
            </div>
          )}
        </div>
      </div>
    </section>
  )
})

export default NotificationsSection
