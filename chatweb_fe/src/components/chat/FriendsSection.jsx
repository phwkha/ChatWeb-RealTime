import React, { useEffect, useRef, useState } from 'react'
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
  filters = { gender: 'ALL', city: '', minAge: '', maxAge: '' },
  hasActiveFilters = false,
  appliedFilterCount = 0,
  onApplyFilters,
  onResetFilters,
  onAcceptFriend,
  onRemoveFriendRelation,
  onUnblockUser,
  onAddFriend,
  onSelectFriend,
  onRefreshSuggestions,
}) {
  const [isPopoverOpen, setIsPopoverOpen] = useState(false)
  const [draftFilters, setDraftFilters] = useState(filters)
  const filterWrapperRef = useRef(null)

  useEffect(() => {
    setDraftFilters(filters)
  }, [filters, isPopoverOpen])

  useEffect(() => {
    if (!isPopoverOpen) return

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        setIsPopoverOpen(false)
      }
    }

    function handleClickOutside(event) {
      if (filterWrapperRef.current && !filterWrapperRef.current.contains(event.target)) {
        setIsPopoverOpen(false)
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isPopoverOpen])

  const isDiscoveryActive = Boolean(searchQuery.trim() || hasActiveFilters)

  const handleApply = () => {
    onApplyFilters?.(draftFilters)
    setIsPopoverOpen(false)
  }

  const handleReset = () => {
    const emptyFilters = { gender: 'ALL', city: '', minAge: '', maxAge: '' }
    setDraftFilters(emptyFilters)
    onResetFilters?.()
    setIsPopoverOpen(false)
  }

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

          <div className="filter-wrapper" ref={filterWrapperRef}>
            <button
              className={`filter-btn ${isPopoverOpen ? 'is-open' : ''} ${hasActiveFilters ? 'has-active' : ''}`}
              type="button"
              aria-label={t('filterUsers')}
              title={t('filterUsers')}
              onClick={() => setIsPopoverOpen((prev) => !prev)}
            >
              <ChatIcon name="filter" size={18} />
              {appliedFilterCount > 0 && <span className="filter-badge">{appliedFilterCount}</span>}
            </button>

            {isPopoverOpen && (
              <div className="filter-popover" role="dialog" aria-modal="true" aria-label={t('filterUsers')}>
                <div className="filter-group">
                  <label className="filter-label">{t('gender')}</label>
                  <div className="filter-chips">
                    {[
                      { key: 'ALL', label: t('genderAll') },
                      { key: 'MAN', label: t('genderMale') },
                      { key: 'WOMAN', label: t('genderFemale') },
                    ].map((g) => (
                      <button
                        key={g.key}
                        type="button"
                        className={`filter-chip ${draftFilters.gender === g.key ? 'is-active' : ''}`}
                        onClick={() => setDraftFilters((d) => ({ ...d, gender: g.key }))}
                      >
                        {g.label}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="filter-group">
                  <label className="filter-label">{t('location')}</label>
                  <input
                    className="filter-input"
                    value={draftFilters.city}
                    onChange={(e) => setDraftFilters((d) => ({ ...d, city: e.target.value }))}
                    placeholder={t('locationPlaceholder')}
                  />
                  <div className="filter-presets">
                    {['Hà Nội', 'TP.HCM', 'Đà Nẵng', 'Cần Thơ', 'Hải Phòng'].map((city) => (
                      <button
                        key={city}
                        type="button"
                        className={`filter-preset-btn ${draftFilters.city === city ? 'is-active' : ''}`}
                        onClick={() => setDraftFilters((d) => ({ ...d, city }))}
                      >
                        {city}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="filter-group">
                  <label className="filter-label">{t('ageRange')}</label>
                  <div className="filter-age-inputs">
                    <input
                      type="number"
                      min="1"
                      max="120"
                      className="filter-input filter-input--age"
                      placeholder={t('fromAge')}
                      value={draftFilters.minAge}
                      onChange={(e) => setDraftFilters((d) => ({ ...d, minAge: e.target.value }))}
                    />
                    <span className="filter-age-sep">-</span>
                    <input
                      type="number"
                      min="1"
                      max="120"
                      className="filter-input filter-input--age"
                      placeholder={t('toAge')}
                      value={draftFilters.maxAge}
                      onChange={(e) => setDraftFilters((d) => ({ ...d, maxAge: e.target.value }))}
                    />
                  </div>
                  <div className="filter-presets">
                    {[
                      { label: '18 - 25', min: '18', max: '25' },
                      { label: '26 - 35', min: '26', max: '35' },
                      { label: '36 - 50', min: '36', max: '50' },
                    ].map((preset) => (
                      <button
                        key={preset.label}
                        type="button"
                        className={`filter-preset-btn ${draftFilters.minAge === preset.min && draftFilters.maxAge === preset.max ? 'is-active' : ''}`}
                        onClick={() => setDraftFilters((d) => ({ ...d, minAge: preset.min, maxAge: preset.max }))}
                      >
                        {preset.label}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="filter-popover-actions">
                  <button type="button" className="filter-action-btn filter-action-btn--reset" onClick={handleReset}>
                    {t('resetFilter')}
                  </button>
                  <button type="button" className="filter-action-btn filter-action-btn--apply" onClick={handleApply}>
                    {t('applyFilter')}
                  </button>
                </div>
              </div>
            )}
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
