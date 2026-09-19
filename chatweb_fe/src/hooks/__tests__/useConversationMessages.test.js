import { renderHook, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useConversationMessages } from '../useConversationMessages.js'
import * as apiClient from '../../services/apiClient.js'

vi.mock('../../services/apiClient.js', async () => {
  const actual = await vi.importActual('../../services/apiClient.js')
  return {
    ...actual,
    apiRequest: vi.fn(),
  }
})

describe('useConversationMessages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls loadConversation API when selectedUser is set and activeSection is chat', async () => {
    vi.mocked(apiClient.apiRequest).mockResolvedValueOnce({
      data: {
        content: [
          { id: 'msg-1', sender: 'bob', recipient: 'alice', content: 'Hi Alice', timestamp: '2026-09-19T10:00:00Z' },
        ],
        nextCursor: null,
        hasMore: false,
      },
    })

    const user = { username: 'alice' }
    const selectedUser = { username: 'bob' }

    const { result } = renderHook(() =>
      useConversationMessages({
        user,
        selectedUser,
        activeSection: 'chat',
        connectionState: 'connected',
        blockedMessageIntervals: {},
        sendPrivateMessage: vi.fn(),
        sendTypingStatus: vi.fn(),
        sendReactionControl: vi.fn(),
        showToast: vi.fn(),
        t: (k) => k,
        selectedUserIsTyping: false,
      })
    )

    await waitFor(() => {
      expect(apiClient.apiRequest).toHaveBeenCalledWith(
        expect.stringContaining('/api/messages/private?user2=bob')
      )
    })

    await waitFor(() => {
      expect(result.current.messagesByUser.bob).toHaveLength(1)
      expect(result.current.messagesByUser.bob[0].content).toBe('Hi Alice')
    })
  })

  it('does not call loadConversation API if activeSection is not chat', async () => {
    const user = { username: 'alice' }
    const selectedUser = { username: 'bob' }

    renderHook(() =>
      useConversationMessages({
        user,
        selectedUser,
        activeSection: 'friends',
        connectionState: 'connected',
        blockedMessageIntervals: {},
        sendPrivateMessage: vi.fn(),
        sendTypingStatus: vi.fn(),
        sendReactionControl: vi.fn(),
        showToast: vi.fn(),
        t: (k) => k,
        selectedUserIsTyping: false,
      })
    )

    expect(apiClient.apiRequest).not.toHaveBeenCalled()
  })

  it('calls loadConversation API exactly once and does not trigger duplicate load', async () => {
    vi.mocked(apiClient.apiRequest).mockResolvedValueOnce({
      data: {
        content: [
          { id: 'msg-1', sender: 'bob', recipient: 'alice', content: 'Hi Alice', timestamp: '2026-09-19T10:00:00Z' },
        ],
        nextCursor: 'cursor-123',
        hasMore: true,
      },
    })

    const user = { username: 'alice' }
    const selectedUser = { username: 'bob' }

    const { rerender } = renderHook(
      (props) => useConversationMessages(props),
      {
        initialProps: {
          user,
          selectedUser,
          activeSection: 'chat',
          connectionState: 'connected',
          blockedMessageIntervals: {},
          sendPrivateMessage: vi.fn(),
          sendTypingStatus: vi.fn(),
          sendReactionControl: vi.fn(),
          showToast: vi.fn(),
          t: (k) => k,
          selectedUserIsTyping: false,
        },
      }
    )

    await waitFor(() => {
      expect(apiClient.apiRequest).toHaveBeenCalledTimes(1)
    })

    // Re-render with same user should not trigger second fetch
    rerender({
      user,
      selectedUser: { ...selectedUser },
      activeSection: 'chat',
      connectionState: 'connected',
      blockedMessageIntervals: {},
      sendPrivateMessage: vi.fn(),
      sendTypingStatus: vi.fn(),
      sendReactionControl: vi.fn(),
      showToast: vi.fn(),
      t: (k) => k,
      selectedUserIsTyping: false,
    })

    expect(apiClient.apiRequest).toHaveBeenCalledTimes(1)
  })
})
