import React, { useState } from 'react'

export function displayName(person) {
  return [person?.firstName, person?.lastName].filter(Boolean).join(' ').trim() || person?.nickname || person?.fullName || person?.username || 'ChatWeb user'
}

export function initials(person) {
  const source = [person?.firstName, person?.lastName].filter(Boolean).join(' ') || person?.nickname || person?.fullName || person?.username || 'CW'
  return source.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

export function isPersonOnline(person) {
  return [person?.online, person?.isOnline].some((value) => (
    value === true || value === 1 || String(value).toLowerCase() === 'true'
  ))
}

export const Avatar = React.memo(function Avatar({ person, size = 'medium', showStatus = false }) {
  const [failed, setFailed] = useState(false)
  return (
    <span className={`cw-avatar cw-avatar--${size}`}>
      {person?.avatar && !failed
        ? <img src={person.avatar} alt="" onError={() => setFailed(true)} />
        : <span>{initials(person)}</span>}
      {showStatus && <i className={isPersonOnline(person) ? 'is-online' : ''} />}
    </span>
  )
})

export default Avatar
