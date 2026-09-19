import React from 'react'
import { render, screen, waitFor, act } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
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

vi.mock('../../hooks/useChatSocket.js', () => ({
  useChatSocket: vi.fn(() => ({
    connectionState: 'connected',
    sendPrivateMessage: vi.fn(),
    sendWorldMessage: vi.fn(),
  })),
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
})
