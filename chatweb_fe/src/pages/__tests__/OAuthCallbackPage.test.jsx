import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import OAuthCallbackPage from '../auth/OAuthCallbackPage.jsx'
import * as authContext from '../../context/auth-context.js'

vi.mock('../../context/auth-context.js', () => ({
  useAuth: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

describe('OAuthCallbackPage', () => {
  const mockRefreshUser = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    authContext.useAuth.mockReturnValue({
      refreshUser: mockRefreshUser,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('navigates to /chat when normal user is authenticated successfully', async () => {
    mockRefreshUser.mockResolvedValue({
      id: '1',
      username: 'normal_user',
      role: { name: 'ROLE_USER' },
    })

    render(
      <MemoryRouter initialEntries={['/oauth2/redirect']}>
        <OAuthCallbackPage />
      </MemoryRouter>
    )

    expect(screen.getByText('Đang hoàn tất đăng nhập')).toBeInTheDocument()
    expect(screen.getByText('ChatWeb đang kết nối tài khoản của bạn...')).toBeInTheDocument()

    await waitFor(() => {
      expect(mockRefreshUser).toHaveBeenCalled()
      expect(mockNavigate).toHaveBeenCalledWith('/chat', { replace: true })
    })
  })

  it('navigates to /admin when admin user is authenticated successfully', async () => {
    mockRefreshUser.mockResolvedValue({
      id: '2',
      username: 'admin_user',
      role: 'ADMIN',
    })

    render(
      <MemoryRouter initialEntries={['/oauth2/redirect']}>
        <OAuthCallbackPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(mockRefreshUser).toHaveBeenCalled()
      expect(mockNavigate).toHaveBeenCalledWith('/admin', { replace: true })
    })
  })

  it('shows error and redirects to /login when OAuth provider returns an error parameter', () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout')

    render(
      <MemoryRouter initialEntries={['/oauth2/redirect?error=access_denied']}>
        <OAuthCallbackPage />
      </MemoryRouter>
    )

    expect(screen.getByText('Chưa thể đăng nhập')).toBeInTheDocument()
    expect(mockRefreshUser).not.toHaveBeenCalled()
    expect(setTimeoutSpy).toHaveBeenCalledWith(expect.any(Function), 1500)

    const callback = setTimeoutSpy.mock.calls.find((call) => call[1] === 1500)[0]
    callback()
    expect(mockNavigate).toHaveBeenCalledWith('/login', {
      replace: true,
      state: expect.objectContaining({ error: expect.any(String) }),
    })
  })

  it('shows error and redirects to /login when refreshUser returns null', async () => {
    mockRefreshUser.mockResolvedValue(null)
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout')

    render(
      <MemoryRouter initialEntries={['/oauth2/redirect']}>
        <OAuthCallbackPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(mockRefreshUser).toHaveBeenCalled()
      expect(screen.getByText('Chưa thể đăng nhập')).toBeInTheDocument()
      expect(screen.getByText('Xác thực thành công nhưng ChatWeb chưa nhận được phiên đăng nhập.')).toBeInTheDocument()
    })

    expect(setTimeoutSpy).toHaveBeenCalledWith(expect.any(Function), 1800)
    const callback = setTimeoutSpy.mock.calls.find((call) => call[1] === 1800)[0]
    callback()
    expect(mockNavigate).toHaveBeenCalledWith('/login', {
      replace: true,
      state: { error: 'Xác thực thành công nhưng ChatWeb chưa nhận được phiên đăng nhập.' },
    })
  })
})
