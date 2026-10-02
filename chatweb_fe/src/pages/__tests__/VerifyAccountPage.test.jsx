import React from 'react'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import VerifyAccountPage from '../auth/VerifyAccountPage.jsx'
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
    useLocation: () => ({ state: { email: 'verify@example.com', message: 'Test message' } }),
  }
})

describe('VerifyAccountPage', () => {
  const mockVerifyAccount = vi.fn()
  const mockResendOtp = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    authContext.useAuth.mockReturnValue({
      verifyAccount: mockVerifyAccount,
      resendOtp: mockResendOtp,
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllTimers()
  })

  it('renders verify account page with email and OTP input', () => {
    const { container } = render(
      <MemoryRouter>
        <VerifyAccountPage />
      </MemoryRouter>
    )

    expect(screen.getByPlaceholderText('000000')).toBeInTheDocument()
    expect(container.querySelector('button[type="submit"]')).toBeInTheDocument()
  })

  it('submits OTP and navigates to /login on success', async () => {
    mockVerifyAccount.mockResolvedValue({ message: 'Xác minh thành công.' })

    const { container } = render(
      <MemoryRouter>
        <VerifyAccountPage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('000000'), {
      target: { value: '654321' },
    })
    fireEvent.click(container.querySelector('button[type="submit"]'))

    await waitFor(() => {
      expect(mockVerifyAccount).toHaveBeenCalledWith({
        email: 'verify@example.com',
        otp: '654321',
      })
      expect(mockNavigate).toHaveBeenCalledWith('/login', expect.objectContaining({
        replace: true,
      }))
    })
  })

  it('displays error when verification fails', async () => {
    mockVerifyAccount.mockRejectedValue(new Error('Mã OTP sai'))

    const { container } = render(
      <MemoryRouter>
        <VerifyAccountPage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('000000'), {
      target: { value: '000000' },
    })
    fireEvent.click(container.querySelector('button[type="submit"]'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Mã OTP sai')
  })

  it('progresses countdown from 60 to 0 and enables resend button', () => {
    vi.useFakeTimers()
    render(
      <MemoryRouter>
        <VerifyAccountPage />
      </MemoryRouter>
    )

    expect(screen.getByRole('button', { name: 'Gửi lại sau 60s' })).toBeDisabled()

    act(() => {
      vi.advanceTimersByTime(10000)
    })
    expect(screen.getByRole('button', { name: 'Gửi lại sau 50s' })).toBeDisabled()

    act(() => {
      vi.advanceTimersByTime(50000)
    })
    const resendBtn = screen.getByRole('button', { name: 'Gửi lại mã' })
    expect(resendBtn).toBeEnabled()

    vi.useRealTimers()
  })

  it('handles resend OTP action and restarts countdown', async () => {
    vi.useFakeTimers()
    mockResendOtp.mockResolvedValue({ message: 'Đã gửi lại mã OTP mới.' })

    render(
      <MemoryRouter>
        <VerifyAccountPage />
      </MemoryRouter>
    )

    act(() => {
      vi.advanceTimersByTime(60000)
    })

    const resendBtn = screen.getByRole('button', { name: 'Gửi lại mã' })
    expect(resendBtn).toBeEnabled()

    await act(async () => {
      fireEvent.click(resendBtn)
    })

    expect(mockResendOtp).toHaveBeenCalledWith('verify@example.com')
    expect(screen.getByText('Đã gửi lại mã OTP mới.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Gửi lại sau 60s' })).toBeDisabled()

    vi.useRealTimers()
  })

  it('cleans up countdown interval on unmount', () => {
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval')
    const { unmount } = render(
      <MemoryRouter>
        <VerifyAccountPage />
      </MemoryRouter>
    )

    unmount()
    expect(clearIntervalSpy).toHaveBeenCalled()
  })

  it('guards against unmounted state updates during submission', async () => {
    let resolveVerify
    mockVerifyAccount.mockImplementation(() => new Promise((resolve) => {
      resolveVerify = resolve
    }))

    const { container, unmount } = render(
      <MemoryRouter>
        <VerifyAccountPage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('000000'), {
      target: { value: '123456' },
    })
    fireEvent.click(container.querySelector('button[type="submit"]'))

    // Unmount while request is pending
    unmount()

    // Resolve after unmount - should not cause unmounted state update warning
    await act(async () => {
      resolveVerify({ message: 'Xác minh thành công.' })
      await Promise.resolve()
    })
  })
})
