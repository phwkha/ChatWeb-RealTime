import React from 'react'
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react'
import { MemoryRouter, useSearchParams } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import ChatPage, { ACTIVE_CONVERSATION_STORAGE_KEY } from '../ChatPage.jsx'
import * as apiClient from '../../services/apiClient.js'
import * as authContext from '../../context/auth-context.js'
import * as languageContext from '../../context/language-context.js'

vi.mock('../../services/apiClient.js', async () => {
  const actual = await vi.importActual('../../services/apiClient.js')
  return {
    ...actual,
    apiRequest: vi.fn(),
  }
})

vi.mock('../../context/auth-context.js', () => ({
  useAuth: vi.fn(),
}))

vi.mock('../../context/language-context.js', () => ({
  useLanguage: vi.fn(),
}))

let socketOnConnected = null

vi.mock('../../hooks/useChatSocket.js', () => ({
  useChatSocket: vi.fn((props) => {
    socketOnConnected = props?.onConnected
    return {
      connectionState: 'connected',
      sendPrivateMessage: vi.fn(),
      sendWorldMessage: vi.fn(),
    }
  }),
}))

vi.mock('../../hooks/useChatAudio.js', () => ({
  useChatAudio: () => ({
    playNotificationSound: vi.fn(),
    playInboxSound: vi.fn(),
  }),
}))

describe('ChatPage Conversation Persistence', () => {
  const mockUser = { username: 'testuser', firstName: 'Test', lastName: 'User' }
  const mockFriends = [
    { username: 'alice', firstName: 'Alice', lastName: 'Wonderland' },
    { username: 'bob', firstName: 'Bob', lastName: 'Builder' },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()

    vi.mocked(authContext.useAuth).mockReturnValue({
      user: mockUser,
      isInitializing: false,
    })

    vi.mocked(languageContext.useLanguage).mockReturnValue({
      language: 'vi',
      t: (key) => key,
    })

    vi.mocked(apiClient.apiRequest).mockImplementation((url) => {
      if (url.includes('/api/friends?')) {
        return Promise.resolve({ data: { content: mockFriends } })
      }
      if (url.includes('/api/friends/requests?')) {
        return Promise.resolve({ data: { content: [] } })
      }
      if (url.includes('/api/friends/sent?')) {
        return Promise.resolve({ data: { content: [] } })
      }
      if (url.includes('/api/friends/blocked?')) {
        return Promise.resolve({ data: { content: [] } })
      }
      if (url.includes('/api/messages/unread-counts')) {
        return Promise.resolve({ data: { unreadCounts: {} } })
      }
      if (url.includes('/api/systems/message')) {
        return Promise.resolve({ data: { content: [], nextCursor: null, hasMore: false } })
      }
      if (url.includes('/api/messages/private?')) {
        return Promise.resolve({ data: { content: [], nextCursor: null, hasMore: false } })
      }
      if (url.includes('/api/messages/mark-as-read')) {
        return Promise.resolve({ data: {} })
      }
      return Promise.resolve({ data: {} })
    })
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('exports the ACTIVE_CONVERSATION_STORAGE_KEY constant', () => {
    expect(ACTIVE_CONVERSATION_STORAGE_KEY).toBe('chatweb-active-conversation')
  })

  it('restores conversation from URL query parameter ?user=alice on page load', async () => {
    render(
      <MemoryRouter initialEntries={['/chat?user=alice']}>
        <ChatPage />
      </MemoryRouter>
    )

    // Wait for friends to load and conversation to be restored
    await waitFor(() => {
      // Both sidebar and chat header/start display Alice
      expect(screen.getAllByText('Alice Wonderland').length).toBeGreaterThanOrEqual(2)
      // LocalStorage should also be populated
      expect(localStorage.getItem(ACTIVE_CONVERSATION_STORAGE_KEY)).toBe('alice')
    })
  })

  it('restores conversation from localStorage when URL has no query param', async () => {
    localStorage.setItem(ACTIVE_CONVERSATION_STORAGE_KEY, 'bob')

    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getAllByText('Bob Builder').length).toBeGreaterThanOrEqual(2)
      expect(screen.getAllByText('@bob').length).toBeGreaterThanOrEqual(2)
    })
  })

  it('cleans up storage and shows welcome screen if user parameter does not exist in friends list', async () => {
    localStorage.setItem(ACTIVE_CONVERSATION_STORAGE_KEY, 'nonexistent_user')

    render(
      <MemoryRouter initialEntries={['/chat?user=nonexistent_user']}>
        <ChatPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      // Welcome screen should be displayed
      expect(screen.getByText('welcomeTitle, Test!')).toBeInTheDocument()
      // Invalid user should be cleaned up from localStorage
      expect(localStorage.getItem(ACTIVE_CONVERSATION_STORAGE_KEY)).toBeNull()
    })
  })

  it('calls messages API only once when page loads with conversation and socket connects', async () => {
    render(
      <MemoryRouter initialEntries={['/chat?user=alice']}>
        <ChatPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getAllByText('Alice Wonderland').length).toBeGreaterThanOrEqual(2)
    })

    // Simulate socket onConnected firing on initial connection
    act(() => {
      socketOnConnected?.({ isReconnect: false })
    })

    // Check how many times /api/messages/private? was called
    const messageCalls = vi.mocked(apiClient.apiRequest).mock.calls.filter((c) =>
      typeof c[0] === 'string' && c[0].includes('/api/messages/private?')
    )
    expect(messageCalls.length).toBe(1)
  })

  it('preserves friends section on reload even if localStorage has active conversation', async () => {
    localStorage.setItem(ACTIVE_CONVERSATION_STORAGE_KEY, 'bob')

    render(
      <MemoryRouter initialEntries={['/chat?section=friends']}>
        <ChatPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByRole('region', { name: 'friends' })).toBeInTheDocument()
    })

    // Must NOT restore conversation with bob in ChatHeader
    expect(screen.queryByText('searchMessages')).not.toBeInTheDocument()
    // Bob should only appear once in sidebar, not in ChatHeader
    expect(screen.getAllByText('Bob Builder')).toHaveLength(1)
  })

  it('preserves notifications section on reload even if localStorage has active conversation', async () => {
    localStorage.setItem(ACTIVE_CONVERSATION_STORAGE_KEY, 'bob')

    render(
      <MemoryRouter initialEntries={['/chat?section=notifications']}>
        <ChatPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByRole('region', { name: 'notifications' })).toBeInTheDocument()
    })

    // Must NOT restore conversation with bob in ChatHeader
    expect(screen.queryByText('searchMessages')).not.toBeInTheDocument()
    // Bob should only appear once in sidebar, not in ChatHeader
    expect(screen.getAllByText('Bob Builder')).toHaveLength(1)
  })

  it('does not eagerly fetch friend requests or notifications list on initial chat load', async () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      const calls = vi.mocked(apiClient.apiRequest).mock.calls.map(([url]) => String(url))
      expect(calls.some((u) => u.includes('/api/friends?'))).toBe(true)
    })

    const allCalls = vi.mocked(apiClient.apiRequest).mock.calls.map(([url]) => String(url))
    expect(allCalls.some((u) => u.includes('/api/friends/requests?'))).toBe(false)
    expect(allCalls.some((u) => u.includes('/api/friends/sent?'))).toBe(false)
    expect(allCalls.some((u) => u.includes('/api/friends/blocked?'))).toBe(false)
    expect(allCalls.some((u) => u.includes('/api/notifications?'))).toBe(false)
  })

  function LocationWatcher() {
    const [searchParams] = useSearchParams()
    return (
      <div style={{ display: 'none' }}>
        <span data-testid="current-user-param">{searchParams.get('user') || ''}</span>
        <span data-testid="current-section-param">{searchParams.get('section') || ''}</span>
      </div>
    )
  }

  it('restores ?user=<username> in URL when switching back to chat section while a conversation is open', async () => {
    render(
      <MemoryRouter initialEntries={['/chat?user=bob']}>
        <LocationWatcher />
        <ChatPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getAllByText('Bob Builder').length).toBeGreaterThanOrEqual(2)
      expect(screen.getByTestId('current-user-param').textContent).toBe('bob')
    })

    // Click Friends in rail
    fireEvent.click(screen.getByTitle('friends'))
    await waitFor(() => {
      expect(screen.getByTestId('current-section-param').textContent).toBe('friends')
      expect(screen.getByTestId('current-user-param').textContent).toBe('')
    })

    // Click Conversations in rail to return to chat
    fireEvent.click(screen.getByTitle('conversations'))
    await waitFor(() => {
      expect(screen.getByTestId('current-section-param').textContent).toBe('')
      expect(screen.getByTestId('current-user-param').textContent).toBe('bob')
    })
  })

  it('does NOT call mark-as-read API when selecting a friend who has zero unread messages', async () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Bob Builder')).toBeInTheDocument()
    })

    // Clear calls made during initial load
    vi.mocked(apiClient.apiRequest).mockClear()

    // Click Bob Builder (has 0 unread messages)
    fireEvent.click(screen.getByText('Bob Builder'))

    // Wait past the 250ms debounce time
    await new Promise((r) => setTimeout(r, 300))

    const markReadCalls = vi.mocked(apiClient.apiRequest).mock.calls.filter(([url]) =>
      typeof url === 'string' && url.includes('/api/messages/mark-as-read')
    )
    expect(markReadCalls).toHaveLength(0)
  })

  it('calls mark-as-read API when selecting a friend who has unread messages', async () => {
    vi.mocked(apiClient.apiRequest).mockImplementation((url) => {
      if (url.includes('/api/friends?')) {
        return Promise.resolve({ data: { content: [{ username: 'bob', firstName: 'Bob', lastName: 'Builder' }] } })
      }
      if (url.includes('/api/messages/unread-counts')) {
        return Promise.resolve({ data: { unreadCounts: { bob: 3 } } })
      }
      return Promise.resolve({ data: {} })
    })

    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Bob Builder')).toBeInTheDocument()
    })

    // Click Bob Builder (has 3 unread messages)
    fireEvent.click(screen.getByText('Bob Builder'))

    await waitFor(() => {
      const markReadCalls = vi.mocked(apiClient.apiRequest).mock.calls.filter(([url, opts]) =>
        typeof url === 'string' && url.includes('/api/messages/mark-as-read')
      )
      expect(markReadCalls.length).toBeGreaterThanOrEqual(1)
    })
  })
})



