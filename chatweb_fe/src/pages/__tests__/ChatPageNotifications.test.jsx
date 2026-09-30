import React from 'react'
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react'
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

let socketOnNotification = null

vi.mock('../../hooks/useChatSocket.js', () => ({
  useChatSocket: vi.fn((props) => {
    socketOnNotification = props?.onNotification
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

describe('ChatPage Notification Click Navigation Integration', () => {
  const mockUser = { username: 'testuser', firstName: 'Test', lastName: 'User' }
  const mockFriends = [
    { username: 'bob', firstName: 'Bob', lastName: 'Builder' },
    { username: 'charlie', firstName: 'Charlie', lastName: 'Chaplin' },
  ]

  let currentNotifications = []

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()

    vi.mocked(authContext.useAuth).mockReturnValue({
      user: mockUser,
      isInitializing: false,
    })

    vi.mocked(languageContext.useLanguage).mockReturnValue({
      language: 'en',
      t: (key) => key,
    })

    currentNotifications = [
      {
        id: 201,
        type: 'FRIEND_REQUEST',
        content: 'Alice sent you a friend request',
        senderUsername: 'alice',
        senderFirstName: 'Alice',
        senderLastName: 'Wonderland',
        isRead: false,
        createdAt: new Date().toISOString(),
      },
      {
        id: 202,
        type: 'FRIEND_ACCEPTED',
        content: 'Bob accepted your friend request',
        senderUsername: 'bob',
        senderFirstName: 'Bob',
        senderLastName: 'Builder',
        isRead: false,
        createdAt: new Date().toISOString(),
      },
      {
        id: 203,
        type: 'REACT_MESSAGE',
        content: 'Bob reacted to your message',
        senderUsername: 'bob',
        targetType: 'MESSAGE',
        targetId: 'msg-999',
        isRead: false,
        createdAt: new Date().toISOString(),
      },
      {
        id: 204,
        type: 'SYSTEM_ANNOUNCEMENT',
        content: 'System maintenance scheduled tonight',
        isRead: false,
        createdAt: new Date().toISOString(),
      },
      {
        id: 205,
        type: 'FRIEND_ACCEPTED',
        content: 'Corrupt notification without target metadata',
        senderUsername: null,
        targetId: null,
        isRead: false,
        createdAt: new Date().toISOString(),
      },
    ]

    vi.mocked(apiClient.apiRequest).mockImplementation((url, options) => {
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
      if (url.includes('/api/notifications/unread-counts')) {
        return Promise.resolve({ data: currentNotifications.filter((n) => !n.isRead).length })
      }
      if (url.includes('/api/notifications?')) {
        return Promise.resolve({
          data: {
            content: currentNotifications,
            nextCursor: null,
            hasMore: false,
          },
        })
      }
      if (url.includes('/read')) {
        return Promise.resolve({ data: { success: true } })
      }
      return Promise.resolve({ data: {} })
    })
  })

  afterEach(() => {
    localStorage.clear()
  })

  const openNotificationsSection = async () => {
    const notifBtn = screen.getByTitle('notifications')
    fireEvent.click(notifBtn)
    await waitFor(() => {
      expect(screen.getByRole('region', { name: 'notifications' })).toBeInTheDocument()
    })
  }

  it('clicking FRIEND_REQUEST notification switches section to friends and does NOT open chat with requester', async () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatPage />
      </MemoryRouter>
    )

    await openNotificationsSection()

    const requestCard = await screen.findByText('Alice sent you a friend request')
    fireEvent.click(requestCard)

    // Should mark notification as read
    await waitFor(() => {
      const readCalls = vi.mocked(apiClient.apiRequest).mock.calls.filter(
        ([url, opts]) => typeof url === 'string' && url.includes('/api/notifications/201/read')
      )
      expect(readCalls.length).toBeGreaterThanOrEqual(1)
    })

    // Active section should now be 'friends'
    await waitFor(() => {
      expect(screen.getByRole('region', { name: 'friends' })).toBeInTheDocument()
    })

    // Must NOT start conversation with requester alice
    expect(localStorage.getItem(ACTIVE_CONVERSATION_STORAGE_KEY)).not.toBe('alice')
  })

  it('clicking FRIEND_ACCEPTED notification switches section to chat and selects friend conversation', async () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatPage />
      </MemoryRouter>
    )

    await openNotificationsSection()

    const acceptedCard = await screen.findByText('Bob accepted your friend request')
    fireEvent.click(acceptedCard)

    // Should mark notification 202 as read
    await waitFor(() => {
      const readCalls = vi.mocked(apiClient.apiRequest).mock.calls.filter(
        ([url]) => typeof url === 'string' && url.includes('/api/notifications/202/read')
      )
      expect(readCalls.length).toBeGreaterThanOrEqual(1)
    })

    // Active conversation should now be Bob Builder
    await waitFor(() => {
      expect(localStorage.getItem(ACTIVE_CONVERSATION_STORAGE_KEY)).toBe('bob')
      expect(screen.getAllByText('Bob Builder').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('clicking REACT_MESSAGE notification switches section to chat and selects sender conversation', async () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatPage />
      </MemoryRouter>
    )

    await openNotificationsSection()

    const reactCard = await screen.findByText('Bob reacted to your message')
    fireEvent.click(reactCard)

    // Should mark notification 203 as read
    await waitFor(() => {
      const readCalls = vi.mocked(apiClient.apiRequest).mock.calls.filter(
        ([url]) => typeof url === 'string' && url.includes('/api/notifications/203/read')
      )
      expect(readCalls.length).toBeGreaterThanOrEqual(1)
    })

    // Active conversation should now be Bob
    await waitFor(() => {
      expect(localStorage.getItem(ACTIVE_CONVERSATION_STORAGE_KEY)).toBe('bob')
      expect(screen.getAllByText('Bob Builder').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('allows switching from one friend to another friend in the chat sidebar', async () => {
    render(
      <MemoryRouter initialEntries={['/chat?user=bob']}>
        <ChatPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(localStorage.getItem(ACTIVE_CONVERSATION_STORAGE_KEY)).toBe('bob')
    })

    const charlieButton = screen.getAllByRole('button').find((btn) => btn.textContent.includes('Charlie Chaplin'))
    expect(charlieButton).toBeTruthy()
    fireEvent.click(charlieButton)

    await waitFor(() => {
      expect(localStorage.getItem(ACTIVE_CONVERSATION_STORAGE_KEY)).toBe('charlie')
    })
  })

  it('filters out non-persisted notification types so only types saved by BE are shown', async () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatPage />
      </MemoryRouter>
    )

    await openNotificationsSection()

    // Notification 204 (SYSTEM_ANNOUNCEMENT) is not saved by BE and should not be displayed
    expect(screen.queryByText('System maintenance scheduled tonight')).toBeNull()

    // Valid persisted notifications saved by BE ARE displayed
    expect(screen.getByText('Alice sent you a friend request')).toBeInTheDocument()
    expect(screen.getByText('Bob accepted your friend request')).toBeInTheDocument()
    expect(screen.getByText('Bob reacted to your message')).toBeInTheDocument()
  })

  it('gracefully handles notification with missing metadata without throwing runtime errors', async () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatPage />
      </MemoryRouter>
    )

    await openNotificationsSection()

    const corruptCard = await screen.findByText('Corrupt notification without target metadata')
    expect(() => fireEvent.click(corruptCard)).not.toThrow()

    // Still marks read
    await waitFor(() => {
      const readCalls = vi.mocked(apiClient.apiRequest).mock.calls.filter(
        ([url]) => typeof url === 'string' && url.includes('/api/notifications/205/read')
      )
      expect(readCalls.length).toBeGreaterThanOrEqual(1)
    })
  })

  it('handles realtime notifications received via WebSocket with type-aware navigation', async () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ChatPage />
      </MemoryRouter>
    )

    await openNotificationsSection()

    // Simulate WebSocket event delivering a realtime FRIEND_REQUEST
    act(() => {
      socketOnNotification?.({
        id: 301,
        type: 'FRIEND_REQUEST',
        content: 'Dave sent you a realtime friend request',
        senderUsername: 'dave',
      })
    })

    const [realtimeCard] = await screen.findAllByText('Dave sent you a realtime friend request')
    fireEvent.click(realtimeCard)

    // Should route to friends section
    await waitFor(() => {
      expect(screen.getByRole('region', { name: 'friends' })).toBeInTheDocument()
    })
    expect(localStorage.getItem(ACTIVE_CONVERSATION_STORAGE_KEY)).not.toBe('dave')
  })
})
