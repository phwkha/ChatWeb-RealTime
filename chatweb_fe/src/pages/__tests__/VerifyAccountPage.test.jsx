import React from 'react'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
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
})
