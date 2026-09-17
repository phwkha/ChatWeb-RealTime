import { renderHook } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useChatSocket } from '../useChatSocket.js'

let mockActivate = vi.fn()
let mockDeactivate = vi.fn()
let mockPublish = vi.fn()
let mockSubscribe = vi.fn()
let capturedOptions = null

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    constructor(options) {
      capturedOptions = options
      this.activate = mockActivate
      this.deactivate = mockDeactivate
      this.publish = mockPublish
      this.subscribe = mockSubscribe
    }
  },
}))

vi.mock('sockjs-client', () => ({
  default: vi.fn(),
}))

vi.mock('../../services/apiClient.js', () => ({
  API_BASE_URL: 'http://localhost:8080',
  getAccessToken: vi.fn(() => 'mock-jwt-token'),
}))

describe('useChatSocket', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    capturedOptions = null
  })

  it('does not initialize client when disabled', () => {
    const { result } = renderHook(() =>
      useChatSocket({ enabled: false, language: 'vi' })
    )
    expect(mockActivate).not.toHaveBeenCalled()
    expect(result.current.connectionState).toBe('connecting')
  })

  it('initializes and activates client with Authorization header when enabled', () => {
    const onConnected = vi.fn()
    renderHook(() =>
      useChatSocket({ enabled: true, language: 'vi', onConnected })
    )

    expect(mockActivate).toHaveBeenCalledTimes(1)
    expect(capturedOptions).toBeDefined()
    expect(capturedOptions.connectHeaders).toEqual({
      'Accept-Language': 'vi',
      Authorization: 'Bearer mock-jwt-token',
    })
  })

  it('deactivates client on unmount', () => {
    const { unmount } = renderHook(() =>
      useChatSocket({ enabled: true, language: 'vi' })
    )

    unmount()
    expect(mockDeactivate).toHaveBeenCalledTimes(1)
  })
})
