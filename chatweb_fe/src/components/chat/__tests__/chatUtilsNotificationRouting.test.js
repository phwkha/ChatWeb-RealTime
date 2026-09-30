import { describe, it, expect } from 'vitest'
import { getNotificationNavigationTarget } from '../chatUtils.js'

describe('getNotificationNavigationTarget', () => {
  it('routes FRIEND_REQUEST notifications to friends section without chat payload', () => {
    const notif = { id: 1, type: 'FRIEND_REQUEST', senderUsername: 'alice' }
    const result = getNotificationNavigationTarget(notif)
    expect(result).toEqual({ section: 'friends' })
  })

  it('routes FRIEND_REQUEST from nested socket payload correctly', () => {
    const notif = {
      id: 11,
      data: {
        type: 'FRIEND_REQUEST',
        senderUsername: 'alice_socket',
      },
    }
    const result = getNotificationNavigationTarget(notif)
    expect(result).toEqual({ section: 'friends' })
  })

  it('routes FRIEND_ACCEPTED to chat and extracts targetUsername and profile fallback', () => {
    const notif = {
      id: 2,
      type: 'FRIEND_ACCEPTED',
      senderUsername: 'bob',
      senderFirstName: 'Bob',
      senderLastName: 'Smith',
      senderAvatar: 'https://example.com/bob.png',
    }
    const result = getNotificationNavigationTarget(notif)
    expect(result).toEqual({
      section: 'chat',
      targetUsername: 'bob',
      userFallback: {
        username: 'bob',
        firstName: 'Bob',
        lastName: 'Smith',
        avatar: 'https://example.com/bob.png',
      },
    })
  })

  it('routes FRIEND_ACCEPTED with targetId when targetType is USER or omitted', () => {
    const notif = { id: 3, type: 'FRIEND_ACCEPTED', targetId: 'charlie' }
    const result = getNotificationNavigationTarget(notif)
    expect(result?.section).toBe('chat')
    expect(result?.targetUsername).toBe('charlie')
  })

  it('ignores non-persisted types like YOU_ACCEPTED or USER_ONLINE', () => {
    expect(getNotificationNavigationTarget({ id: 4, type: 'YOU_ACCEPTED', senderUsername: 'dave' })).toBeNull()
    expect(getNotificationNavigationTarget({ id: 5, type: 'USER_ONLINE', senderUsername: 'dave' })).toBeNull()
  })

  it('routes REACT_MESSAGE to sender conversation, strictly ignoring message targetId', () => {
    const notif = {
      id: 5,
      type: 'REACT_MESSAGE',
      targetType: 'MESSAGE',
      targetId: 'msg-uuid-1234',
      senderUsername: 'eve',
    }
    const result = getNotificationNavigationTarget(notif)
    expect(result?.section).toBe('chat')
    expect(result?.targetUsername).toBe('eve')
  })

  it('correctly falls back to relatedUser object properties in userFallback', () => {
    const notif = {
      id: 6,
      type: 'FRIEND_ACCEPTED',
      relatedUser: {
        username: 'frank',
        firstName: 'Frank',
        lastName: 'Sinatra',
        avatar: 'https://example.com/frank.png',
      },
    }
    const result = getNotificationNavigationTarget(notif)
    expect(result?.targetUsername).toBe('frank')
    expect(result?.userFallback).toEqual({
      username: 'frank',
      firstName: 'Frank',
      lastName: 'Sinatra',
      avatar: 'https://example.com/frank.png',
    })
  })

  it('returns null for missing target in REACT_MESSAGE or FRIEND_ACCEPTED without throwing', () => {
    expect(getNotificationNavigationTarget({ type: 'REACT_MESSAGE' })).toBeNull()
    expect(getNotificationNavigationTarget({ type: 'FRIEND_ACCEPTED' })).toBeNull()
  })

  it('returns null for unhandled or invalid notification types', () => {
    expect(getNotificationNavigationTarget({ type: 'SYSTEM' })).toBeNull()
    expect(getNotificationNavigationTarget({ type: 'UNKNOWN' })).toBeNull()
    expect(getNotificationNavigationTarget(null)).toBeNull()
    expect(getNotificationNavigationTarget({})).toBeNull()
  })
})
