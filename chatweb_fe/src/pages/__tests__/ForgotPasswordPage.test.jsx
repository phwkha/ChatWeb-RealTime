import React from 'react'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import ForgotPasswordPage from '../auth/ForgotPasswordPage.jsx'
import * as apiClient from '../../services/apiClient.js'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders initial request step with email input', () => {
    const { container } = render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>
    )

    expect(screen.getByPlaceholderText('ban@email.com')).toBeInTheDocument()
    expect(container.querySelector('button[type="submit"]')).toBeInTheDocument()
  })

  it('progresses to reset step after requesting OTP', async () => {
    vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ message: 'Mã xác nhận đã gửi.' })

    const { container } = render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('ban@email.com'), {
      target: { name: 'email', value: 'alice@example.com' },
    })
    fireEvent.click(container.querySelector('button[type="submit"]'))

    expect(await screen.findByPlaceholderText('000000')).toBeInTheDocument()
    expect(container.querySelector('input[name="newPassword"]')).toBeInTheDocument()
    expect(container.querySelector('input[name="confirmPassword"]')).toBeInTheDocument()
  })

  it('submits reset password and navigates to /login', async () => {
    const apiSpy = vi.spyOn(apiClient, 'apiRequest')
      .mockResolvedValueOnce({ message: 'OTP sent' })
      .mockResolvedValueOnce({ message: 'Đổi mật khẩu thành công.' })

    const { container } = render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>
    )

    // Step 1: Request OTP
    fireEvent.change(screen.getByPlaceholderText('ban@email.com'), {
      target: { name: 'email', value: 'alice@example.com' },
    })
    fireEvent.click(container.querySelector('button[type="submit"]'))

    // Step 2: Reset Password
    const otpInput = await screen.findByPlaceholderText('000000')
    fireEvent.change(otpInput, { target: { name: 'otp', value: '123456' } })
    fireEvent.change(container.querySelector('input[name="newPassword"]'), {
      target: { name: 'newPassword', value: 'NewPassword123!' },
    })
    fireEvent.change(container.querySelector('input[name="confirmPassword"]'), {
      target: { name: 'confirmPassword', value: 'NewPassword123!' },
    })

    fireEvent.click(container.querySelector('button[type="submit"]'))

    await waitFor(() => {
      expect(apiSpy).toHaveBeenLastCalledWith('/api/auth/reset-password', expect.objectContaining({
        method: 'POST',
        body: { email: 'alice@example.com', otp: '123456', newPassword: 'NewPassword123!' },
      }))
      expect(mockNavigate).toHaveBeenCalledWith('/login', expect.objectContaining({ replace: true }))
    })
  })
})
