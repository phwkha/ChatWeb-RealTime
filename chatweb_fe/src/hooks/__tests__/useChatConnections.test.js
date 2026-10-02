import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useChatConnections } from '../useChatConnections.js'
import * as apiClient from '../../services/apiClient.js'
import { BLOCKED_MESSAGES_STORAGE_KEY } from '../../components/chat/chatUtils.js'

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

  it('cancels pending timers and ignores in-flight requests on unmount', async () => {
    const clearTimeoutSpy = vi.spyOn(window, 'clearTimeout')
    let resolvePending
    const pendingPromise = new Promise((resolve) => {
      resolvePending = resolve
    })

    vi.spyOn(apiClient, 'apiRequest').mockImplementation((url) => {
      if (url.includes('/api/friends?size=100')) {
        return pendingPromise
      }
      return Promise.resolve({ data: { content: [] } })
    })

    const { result, unmount } = renderHook(() =>
      useChatConnections({
        currentUser: { username: 'alice' },
        activeSection: 'chat',
        showToast,
        t,
      })
    )

    act(() => {
      result.current.scheduleConnectionSync()
    })

    unmount()

    expect(clearTimeoutSpy).toHaveBeenCalled()

    await act(async () => {
      resolvePending({ data: { content: [{ id: '99', username: 'ghost' }] } })
      await Promise.resolve()
    })

    expect(result.current.friends).toEqual([])
  })

  it('safely handles localStorage.setItem quota or security errors without throwing', async () => {
    const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError: storage is full')
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
      result.current.saveBlockedIntervals({ 'alice:bob': [{ from: 1000, to: 2000 }] })
      await Promise.resolve()
    })

    expect(result.current.blockedMessageIntervals).toEqual({
      'alice:bob': [{ from: 1000, to: 2000 }],
    })
    expect(consoleWarnSpy).toHaveBeenCalledWith(
      expect.stringContaining('Failed to save blocked message intervals'),
      expect.any(Error)
    )
  })

  it('allows unblockServerUser to succeed even if localStorage.setItem fails', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError')
    })
    vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({})

    const { result } = renderHook(() =>
      useChatConnections({
        currentUser: { username: 'alice' },
        activeSection: 'chat',
        showToast,
        t,
      })
    )

    let success
    await act(async () => {
      success = await result.current.unblockServerUser({ username: 'bob' })
    })

    expect(success).toBe(true)
    expect(showToast).toHaveBeenCalledWith('unblockedUserSuccess')
  })

  it('cleanly resets requestsLoaded and re-reads blocked intervals when currentUser changes', async () => {
    localStorage.setItem(
      BLOCKED_MESSAGES_STORAGE_KEY,
      JSON.stringify({ 'bob:charlie': [{ from: 500, to: null }] })
    )

    const mockAliceFriends = [{ id: '1', username: 'alice_friend' }]
    const mockBobFriends = [{ id: '2', username: 'bob_friend' }]
    const mockBobRequests = [{ id: '3', username: 'charlie' }]

    vi.spyOn(apiClient, 'apiRequest').mockImplementation((url) => {
      if (url.includes('/api/friends/requests')) {
        return Promise.resolve({ data: { content: mockBobRequests } })
      }
      if (url.includes('/api/friends?size=100')) {
        return Promise.resolve({ data: { content: mockBobFriends } })
      }
      return Promise.resolve({ data: { content: [] } })
    })

    const { result, rerender } = renderHook(
      ({ user, section }) =>
        useChatConnections({
          currentUser: user,
          activeSection: section,
          showToast,
          t,
        }),
      {
        initialProps: {
          user: { username: 'alice' },
          section: 'friends',
        },
      }
    )

    await act(async () => {
      await Promise.resolve()
    })

    localStorage.setItem(
      BLOCKED_MESSAGES_STORAGE_KEY,
      JSON.stringify({ 'bob:eve': [{ from: 999, to: null }] })
    )

    rerender({
      user: { username: 'bob' },
      section: 'friends',
    })

    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.blockedMessageIntervals).toEqual({
      'bob:eve': [{ from: 999, to: null }],
    })
    expect(result.current.friendRequests).toEqual(mockBobRequests)
  })
})
