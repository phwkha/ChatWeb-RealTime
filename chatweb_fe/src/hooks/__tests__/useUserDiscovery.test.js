import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useUserDiscovery } from '../useUserDiscovery.js'
import * as apiClient from '../../services/apiClient.js'

describe('useUserDiscovery', () => {
  const showToast = vi.fn()
  const t = (k) => k

  beforeEach(() => {
    vi.restoreAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('initializes with default state', () => {
    const { result } = renderHook(() =>
      useUserDiscovery({
        activeSection: 'chat',
        currentUsernameKey: 'me',
        language: 'vi',
        showToast,
        t,
      })
    )

    expect(result.current.searchQuery).toBe('')
    expect(result.current.searchType).toBe('username')
    expect(result.current.searchResults).toEqual([])
    expect(result.current.suggestions).toEqual([])
    expect(result.current.searching).toBe(false)
  })

  it('loads suggestions when activeSection is friends and suggestions empty', async () => {
    const mockUsers = [
      { id: '1', username: 'bob', firstName: 'Bob', lastName: 'Smith' },
      { id: '2', username: 'charlie', firstName: 'Charlie', lastName: 'Brown' },
    ]
    const apiSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({
      data: { content: mockUsers },
    })

    const { result } = renderHook(() =>
      useUserDiscovery({
        activeSection: 'friends',
        currentUsernameKey: 'me',
        language: 'vi',
        showToast,
        t,
      })
    )

    await act(async () => {
      await Promise.resolve()
    })

    expect(apiSpy).toHaveBeenCalledWith('/api/users/search?size=24&sortDir=asc')
    expect(result.current.suggestions).toEqual(mockUsers)
  })

  it('searches users with debounce and filters out current user', async () => {
    const mockUsers = [
      { id: '1', username: 'me', firstName: 'Myself' },
      { id: '2', username: 'alex', firstName: 'Alex' },
    ]
    const apiSpy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({
      data: { content: mockUsers },
    })

    const { result } = renderHook(() =>
      useUserDiscovery({
        activeSection: 'friends',
        currentUsernameKey: 'me',
        language: 'en',
        showToast,
        t,
      })
    )

    act(() => {
      result.current.setSearchQuery('alex')
    })

    await act(async () => {
      vi.advanceTimersByTime(350)
      await Promise.resolve()
    })

    expect(apiSpy).toHaveBeenCalledWith(
      expect.stringContaining('/api/users/search?keyword=alex'),
      expect.any(Object)
    )
    expect(result.current.searchResults).toEqual([
      { id: '2', username: 'alex', firstName: 'Alex' },
    ])
  })
})
