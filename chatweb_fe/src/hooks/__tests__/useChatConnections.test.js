import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useChatConnections } from '../useChatConnections.js'
import * as apiClient from '../../services/apiClient.js'

describe('useChatConnections', () => {
  const showToast = vi.fn()
  const t = (k) => k

  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('initializes with default empty lists and loads friends on chat section', async () => {
    const mockFriends = [{ id: '1', username: 'bob', friendshipStatus: 'ACCEPTED' }]
    vi.spyOn(apiClient, 'apiRequest').mockImplementation((url) => {
      if (url.includes('/api/friends?size=100')) {
        return Promise.resolve({ data: { content: mockFriends } })
      }
      return Promise.resolve({ data: { content: [] } })
    })

    const { result } = renderHook(() =>
      useChatConnections({
        currentUser: { username: 'alice' },
        activeSection: 'chat',
        showToast,
        t,
      })
    )

    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.friends).toEqual(mockFriends)
    expect(result.current.connectionsLoaded).toBe(true)
  })

  it('loads all relationships when activeSection is friends', async () => {
    const mockFriends = [{ id: '1', username: 'bob' }]
    const mockRequests = [{ id: '2', username: 'charlie' }]
    const mockSent = [{ id: '3', username: 'dave' }]
    const mockBlocked = [{ id: '4', username: 'eve' }]

    vi.spyOn(apiClient, 'apiRequest').mockImplementation((url) => {
      if (url.includes('/api/friends?size=100')) return Promise.resolve({ data: { content: mockFriends } })
      if (url.includes('/api/friends/requests?size=100')) return Promise.resolve({ data: { content: mockRequests } })
      if (url.includes('/api/friends/sent?size=100')) return Promise.resolve({ data: { content: mockSent } })
      if (url.includes('/api/friends/blocked?size=100')) return Promise.resolve({ data: { content: mockBlocked } })
      return Promise.resolve({ data: { content: [] } })
    })

    const { result } = renderHook(() =>
      useChatConnections({
        currentUser: { username: 'alice' },
        activeSection: 'friends',
        showToast,
        t,
      })
    )

    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.friends).toEqual(mockFriends)
    expect(result.current.friendRequests).toEqual(mockRequests)
    expect(result.current.sentRequests).toEqual(mockSent)
    expect(result.current.blockedUsers).toEqual(mockBlocked)
  })
})
