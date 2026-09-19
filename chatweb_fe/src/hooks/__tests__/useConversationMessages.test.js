import { renderHook, waitFor, act } from '@testing-library/react'
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

  it('provides scrollToBottom function and does not trigger loadOlderConversation during initial load', async () => {
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

    expect(typeof result.current.scrollToBottom).toBe('function')

    const mockStream = {
      scrollHeight: 1000,
      scrollTop: 0,
      clientHeight: 400,
      scrollTo: vi.fn(),
    }
    result.current.messageStreamRef.current = mockStream

    result.current.scrollToBottom(true)
    expect(mockStream.scrollTop).toBe(1000)

    // Simulate scroll event near top while initial load is still flagged
    result.current.initialLoadScrollRef.current = true
    result.current.handleMessageStreamScroll({ currentTarget: { scrollTop: 10 } })

    // Initial load fetch was called once
    expect(apiClient.apiRequest).toHaveBeenCalledTimes(1)
    expect(apiClient.apiRequest).toHaveBeenCalledWith(
      expect.stringContaining('/api/messages/private?user2=bob')
    )

    // Should NOT call loadOlderConversation API (no cursor query param)
    expect(apiClient.apiRequest).not.toHaveBeenCalledWith(
      expect.stringContaining('cursor=')
    )
  })

  it('manages reply state and sends replyToId on submit', async () => {
    const user = { username: 'alice' }
    const selectedUser = { username: 'bob' }
    const sendPrivateMessage = vi.fn().mockReturnValue(true)

    const { result } = renderHook(() =>
      useConversationMessages({
        user,
        selectedUser,
        activeSection: 'chat',
        connectionState: 'connected',
        blockedMessageIntervals: {},
        sendPrivateMessage,
        sendTypingStatus: vi.fn(),
        sendReactionControl: vi.fn(),
        showToast: vi.fn(),
        t: (k) => k,
        selectedUserIsTyping: false,
      })
    )

    const parentMessage = { id: 'msg-parent', sender: 'bob', content: 'What time?' }
    act(() => {
      result.current.beginReply(parentMessage)
    })
    expect(result.current.replyingToMessage).toEqual(parentMessage)

    // Draft a message
    act(() => {
      result.current.setMessageDraft('At 3 PM')
    })

    // Submit the message
    await act(async () => {
      await result.current.submitMessage({ preventDefault: () => {} })
    })

    expect(sendPrivateMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        content: 'At 3 PM',
        recipient: 'bob',
        replyToId: 'msg-parent',
      })
    )
    expect(result.current.replyingToMessage).toBeNull()
  })

  it('cancels reply via cancelReply', () => {
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

    act(() => {
      result.current.beginReply({ id: 'msg-1', content: 'Hello' })
    })
    expect(result.current.replyingToMessage).not.toBeNull()

    act(() => {
      result.current.cancelReply()
    })
    expect(result.current.replyingToMessage).toBeNull()
  })

  it('fetches missing reply message from GET /api/messages/{id} and caches it', async () => {
    vi.mocked(apiClient.apiRequest).mockImplementation((url) => {
      if (url.includes('/api/messages/private')) {
        return Promise.resolve({
          data: {
            content: [
              { id: 'msg-2', sender: 'bob', recipient: 'alice', content: 'Reply content', replyToId: 'msg-old', timestamp: '2026-09-19T10:00:00Z' },
            ],
            nextCursor: null,
            hasMore: false,
          },
        })
      }
      if (url === '/api/messages/msg-old') {
        return Promise.resolve({
          data: { id: 'msg-old', sender: 'alice', recipient: 'bob', content: 'Original older message' },
        })
      }
      return Promise.resolve({ data: null })
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
      expect(apiClient.apiRequest).toHaveBeenCalledWith('/api/messages/msg-old')
    })

    await waitFor(() => {
      expect(result.current.replyMessageCache['msg-old']).toBeDefined()
      expect(result.current.replyMessageCache['msg-old'].content).toBe('Original older message')
    })

    const callCountBefore = vi.mocked(apiClient.apiRequest).mock.calls.filter((c) => c[0] === '/api/messages/msg-old').length
    await act(async () => {
      await result.current.fetchReplyMessage('msg-old')
    })
    const callCountAfter = vi.mocked(apiClient.apiRequest).mock.calls.filter((c) => c[0] === '/api/messages/msg-old').length
    expect(callCountAfter).toBe(callCountBefore)
  })

  it('scrollToQuotedMessage scrolls and highlights if element exists, or shows toast if not found', () => {
    const user = { username: 'alice' }
    const selectedUser = { username: 'bob' }
    const showToast = vi.fn()

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
        showToast,
        t: (k) => k,
        selectedUserIsTyping: false,
      })
    )

    act(() => {
      result.current.scrollToQuotedMessage('msg-nonexistent')
    })
    expect(showToast).toHaveBeenCalledWith('originalMessageNotFound')

    const mockEl = document.createElement('div')
    mockEl.id = 'chat-message-msg-existing'
    mockEl.scrollIntoView = vi.fn()
    document.body.appendChild(mockEl)

    act(() => {
      result.current.scrollToQuotedMessage('msg-existing')
    })
    expect(mockEl.scrollIntoView).toHaveBeenCalled()
    expect(result.current.highlightedMessageId).toBe('msg-existing')

    document.body.removeChild(mockEl)
  })
})
