import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import PersonResult from './PersonResult.jsx'

export const FriendsSection = React.memo(function FriendsSection({
  searchQuery,
  onSearchQueryChange,
  searchType,
  onSearchTypeChange,
  friendRequests = [],
  sentRequests = [],
  blockedUsers = [],
  visibleSuggestions = [],
  searchResults = [],
  searching = false,
  loadingSuggestions = false,
  friendNames = new Set(),
  sentNames = new Set(),
  blockedNames = new Set(),
  t,
  onAcceptFriend,
  onRemoveFriendRelation,
  onUnblockUser,
  onAddFriend,
  onSelectFriend,
  onRefreshSuggestions,
}) {
  const isDiscoveryActive = Boolean(searchQuery.trim())

  return (
    <section className="app-section-page friends-section-page" aria-label={t('friends')}>
      <header className="app-section-page__header">
        <span><ChatIcon name="users" size={22} /></span>
        <div>
          <small>CHATWEB</small>
          <h1>{t('friends')}</h1>
          <p>{t('friendsPageBody')}</p>
        </div>
      </header>

      <div className="app-section-page__content">
        <div className="friends-search-row">
          <div className="search-input">
            <ChatIcon name="search" />
            <input
              autoFocus
              value={searchQuery}
              onChange={(event) => onSearchQueryChange(event.target.value)}
              placeholder={t('searchHint')}
            />
          </div>
        </div>

        <div className="search-types">
          <button
            className={searchType === 'username' ? 'is-active' : ''}
            type="button"
            onClick={() => onSearchTypeChange('username')}
          >
            @ {t('username')}
          </button>
          <button
            className={searchType === 'displayName' ? 'is-active' : ''}
            type="button"
            onClick={() => onSearchTypeChange('displayName')}
          >
            {t('displayName')}
          </button>
        </div>

        {!isDiscoveryActive && (
          <div className="relationship-lists">
            <div className="panel-section">
              <h3>{t('pendingRequests')} <span>{friendRequests.length}</span></h3>
              {friendRequests.map((person) => (
                <PersonResult
                  key={person.username}
                  person={person}
                  actionLabel={t('accept')}
                  onAction={() => onAcceptFriend(person)}
                  secondaryActionLabel={t('rejectRequest')}
                  onSecondaryAction={() => onRemoveFriendRelation(person, 'requestRejected')}
                />
              ))}
              {!friendRequests.length && (
                <p className="relationship-empty">{t('noPendingRequests')}</p>
              )}
            </div>

            <div className="panel-section">
              <h3>{t('sentRequests')} <span>{sentRequests.length}</span></h3>
              {sentRequests.map((person) => (
                <PersonResult
                  key={person.username}
                  person={person}
                  actionLabel={t('requested')}
                  disabled
                  secondaryActionLabel={t('cancelRequest')}
                  onSecondaryAction={() => onRemoveFriendRelation(person, 'requestCancelled')}
                />
              ))}
              {!sentRequests.length && (
                <p className="relationship-empty">{t('noSentRequests')}</p>
              )}
            </div>

            <div className="panel-section blocked-users-section">
              <h3>{t('blockedUsers')} <span>{blockedUsers.length}</span></h3>
              {blockedUsers.map((person) => (
                <PersonResult
                  key={person.username}
                  person={person}
                  actionLabel={t('unblockUser')}
                  onAction={() => onUnblockUser(person)}
                />
              ))}
              {!blockedUsers.length && (
                <p className="relationship-empty">{t('noBlockedUsers')}</p>
              )}
            </div>
          </div>
        )}

        {!isDiscoveryActive && (
          <div className="panel-section suggestion-section">
            <div className="panel-section__heading">
              <h3>{t('suggestedForYou')} <span>{visibleSuggestions.length}</span></h3>
              <button
                type="button"
                onClick={onRefreshSuggestions}
                disabled={loadingSuggestions}
              >
                {t('refreshSuggestions')}
              </button>
            </div>
            {loadingSuggestions && (
              <div className="panel-loading">
                <i /><i /><i />
              </div>
            )}
            {!loadingSuggestions && visibleSuggestions.map((person) => (
              <PersonResult
                key={person.username}
                person={person}
                actionLabel={t('addFriend')}
                onAction={() => onAddFriend(person)}
              />
            ))}
            {!loadingSuggestions && visibleSuggestions.length === 0 && (
              <div className="suggestion-empty">
                <ChatIcon name="users" size={24} />
                <p>{t('noSuggestions')}</p>
              </div>
            )}
          </div>
        )}

        {isDiscoveryActive && (
          <div className="panel-section search-results">
            {searching && (
              <div className="panel-loading">
                <i /><i /><i />
              </div>
            )}
            {!searching && searchResults.length === 0 && (
              <div className="panel-empty">
                <ChatIcon name="search" size={30} />
                <p>{t('noResults')}</p>
              </div>
            )}
            {!searching && searchResults.map((person) => {
              const isBlocked = blockedNames.has(person.username)
              const isFriend = friendNames.has(person.username)
              const isSent = sentNames.has(person.username)

              let actionLabel = t('addFriend')
              let disabled = false
              let onAction = () => onAddFriend(person)
              let secondaryActionLabel = ''
              let onSecondaryAction = undefined

              if (isBlocked) {
                actionLabel = t('unblockUser')
                onAction = () => onUnblockUser(person)
              } else if (isFriend) {
                actionLabel = t('messageUser')
                onAction = () => onSelectFriend(person)
              } else if (isSent) {
                actionLabel = t('requested')
                disabled = true
                secondaryActionLabel = t('cancelRequest')
                onSecondaryAction = () => onRemoveFriendRelation(person, 'requestCancelled')
              }

              return (
                <PersonResult
                  key={person.username}
                  person={person}
                  actionLabel={actionLabel}
                  disabled={disabled}
                  onAction={onAction}
                  secondaryActionLabel={secondaryActionLabel}
                  onSecondaryAction={onSecondaryAction}
                  onSelect={isFriend ? () => onSelectFriend(person) : undefined}
                />
              )
            })}
          </div>
        )}
      </div>
    </section>
  )
})

export default FriendsSection
