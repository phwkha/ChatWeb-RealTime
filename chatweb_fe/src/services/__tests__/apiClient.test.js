import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  getErrorMessage,
  setSessionExpiredHandler,
  notifySessionExpired,
  setAccessToken,
  getAccessToken,
  apiRequest,
  refreshAccessToken,
} from '../apiClient.js'

describe('apiClient', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setAccessToken(null)
    setSessionExpiredHandler(null)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('getErrorMessage', () => {
    it('filters out raw JWT expired message and returns friendly expired message', () => {
      const rawError = {
        message: 'Lỗi xác thực: JWT expired 309633 milliseconds ago at 2026-10-01T09:23:33.000Z. Current time: 2026-10-01T09:28:42.633Z. Allowed clock skew: 0 milliseconds.',
        errorCode: 'STOMP_ERROR',
      }
      const msg = getErrorMessage(rawError)
      expect(msg).toBe('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
    })

    it('returns friendly message for 4011 and TOKEN_EXPIRED codes', () => {
      expect(getErrorMessage({ errorCode: 'TOKEN_EXPIRED' })).toBe('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
      expect(getErrorMessage({ code: 4011 })).toBe('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
    })

    it('returns custom safe messages if not matching technical pattern', () => {
      const msg = getErrorMessage({ message: 'Tên người dùng đã tồn tại.', fromServer: true })
      expect(msg).toBe('Tên người dùng đã tồn tại.')
    })

    it('filters technical database or network errors and returns fallback', () => {
      const msg = getErrorMessage({ message: 'Failed to fetch', fromServer: true })
      expect(msg).toBe('Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối và thử lại.')
    })
  })

  describe('notifySessionExpired', () => {
    it('clears access token and calls registered sessionExpiredHandler', () => {
      setAccessToken('some-old-token')
      const handler = vi.fn()
      setSessionExpiredHandler(handler)

      notifySessionExpired('Phiên hết hạn test')

      expect(getAccessToken()).toBeNull()
      expect(handler).toHaveBeenCalledWith('Phiên hết hạn test')
    })

    it('dispatches chatweb:session-expired window event', () => {
      const eventHandler = vi.fn()
      window.addEventListener('chatweb:session-expired', eventHandler)

      notifySessionExpired()

      expect(eventHandler).toHaveBeenCalled()
      window.removeEventListener('chatweb:session-expired', eventHandler)
    })
  })

  describe('apiRequest with silent token refresh', () => {
    it('retries request after successful token refresh on 401', async () => {
      const originalFetch = global.fetch
      global.fetch = vi.fn()
        // 1st call: request returns 401
        .mockResolvedValueOnce({
          status: 401,
          ok: false,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ code: 401, message: 'Unauthorized' }),
        })
        // 2nd call: refresh-token succeeds and returns new access token
        .mockResolvedValueOnce({
          status: 200,
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ code: 200, message: 'Refreshed', data: 'new-refreshed-token-abc' }),
        })
        // 3rd call: retried request succeeds
        .mockResolvedValueOnce({
          status: 200,
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ code: 200, data: { success: true } }),
        })

      const res = await apiRequest('/api/messages/unread-counts')
      expect(res.data.success).toBe(true)
      expect(getAccessToken()).toBe('new-refreshed-token-abc')

      global.fetch = originalFetch
    })

    it('triggers notifySessionExpired when refresh token also fails with 401', async () => {
      const originalFetch = global.fetch
      const expiredHandler = vi.fn()
      setSessionExpiredHandler(expiredHandler)

      global.fetch = vi.fn()
        // 1st call: request returns 401
        .mockResolvedValueOnce({
          status: 401,
          ok: false,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ code: 401, message: 'Unauthorized' }),
        })
        // 2nd call: refresh-token also returns 401 (refresh token expired)
        .mockResolvedValueOnce({
          status: 401,
          ok: false,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ code: 401, message: 'Refresh token expired' }),
        })

      await expect(apiRequest('/api/messages/unread-counts')).rejects.toThrow()
      expect(expiredHandler).toHaveBeenCalled()

      global.fetch = originalFetch
    })

    it('deduplicates concurrent refreshAccessToken calls into a single network request', async () => {
      const originalFetch = global.fetch
      global.fetch = vi.fn().mockImplementation(async (url) => {
        if (url.includes('/api/auth/refresh-token')) {
          return {
            status: 200,
            ok: true,
            headers: new Headers({ 'content-type': 'application/json' }),
            json: async () => ({ code: 200, message: 'Refreshed', data: 'new-concurrent-token-123' }),
          }
        }
        return { status: 200, ok: true, headers: new Headers({ 'content-type': 'application/json' }), json: async () => ({}) }
      })

      // Simulate simultaneous refresh calls from REST and WebSocket
      const [res1, res2] = await Promise.all([
        refreshAccessToken(),
        refreshAccessToken(),
      ])

      expect(res1.data).toBe('new-concurrent-token-123')
      expect(res2.data).toBe('new-concurrent-token-123')
      // Only 1 fetch call should have been made to /api/auth/refresh-token
      expect(global.fetch).toHaveBeenCalledTimes(1)

      global.fetch = originalFetch
    })
  })
})
