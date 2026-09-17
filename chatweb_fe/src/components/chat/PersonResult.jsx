import React from 'react'
import ChatIcon from './ChatIcon.jsx'
import { Avatar, displayName } from './Avatar.jsx'

export const PersonResult = React.memo(function PersonResult({
  person,
  actionLabel,
  onAction,
  onSelect,
  disabled = false,
  secondaryActionLabel = '',
  onSecondaryAction,
}) {
  return (
    <article className="person-result">
      <button className="person-result__identity" type="button" onClick={onSelect} disabled={!onSelect}>
        <Avatar person={person} showStatus />
        <span>
          <strong>{displayName(person)}</strong>
          <small>@{person.username}</small>
        </span>
      </button>
      <span className="person-result__actions">
        {secondaryActionLabel && (
          <button className="person-result__secondary" type="button" onClick={onSecondaryAction}>
            {secondaryActionLabel}
          </button>
        )}
        <button className="person-result__action" type="button" disabled={disabled} onClick={onAction}>
          {disabled && <ChatIcon name="check" size={15} />}
          {actionLabel}
        </button>
      </span>
    </article>
  )
})

export default PersonResult
