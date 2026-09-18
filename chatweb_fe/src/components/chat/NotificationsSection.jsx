import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import PersonResult from './PersonResult.jsx'
import { formatTime } from './chatUtils.js'

export const NotificationsSection = React.memo(function NotificationsSection({
  friendRequests = [],
  worldNotifications = [],
  language,
  t,
  onAcceptFriend,
  onOpenWorld,
}) {
  return (
    <section className="app-section-page notifications-section-page" aria-label={t('notifications')}>
      <header className="app-section-page__header">
        <span><ChatIcon name="bell" size={22} /></span>
        <div>
          <small>CHATWEB</small>
          <h1>{t('notifications')}</h1>
          <p>{t('notificationsPageBody')}</p>
        </div>
      </header>

      <div className="app-section-page__content notifications-page__content">
        <div className="panel-section notifications-page__list">
          {friendRequests.map((person) => (
            <PersonResult
              key={person.username}
              person={person}
              actionLabel={t('accept')}
              onAction={() => onAcceptFriend(person)}
            />
          ))}

          {worldNotifications.map((message, index) => (
            <button
              className="notification-card"
              type="button"
              key={`${message.receivedAt}-${index}`}
              onClick={onOpenWorld}
            >
              <span><ChatIcon name="globe" size={17} /></span>
              <div>
                <strong>{message.sender || t('admin')}</strong>
                <p>{message.content}</p>
                <time>{formatTime(message.receivedAt, language)}</time>
              </div>
            </button>
          ))}

          {!friendRequests.length && !worldNotifications.length && (
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
