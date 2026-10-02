import React from 'react'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import LoginPage from '../auth/LoginPage.jsx'
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

describe('LoginPage', () => {
  const mockLogin = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    authContext.useAuth.mockReturnValue({
      login: mockLogin,
    })
  })

  it('renders login form properly', () => {
    const { container } = render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    )

    expect(screen.getByPlaceholderText('Nhập tên đăng nhập')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Nhập mật khẩu')).toBeInTheDocument()
    expect(container.querySelector('button[type="submit"]')).toBeInTheDocument()
  })

  it('shows validation errors when submitting empty form', async () => {
    const { container } = render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    )

    fireEvent.click(container.querySelector('button[type="submit"]'))

    expect(await screen.findByText('Vui lòng nhập tên đăng nhập.')).toBeInTheDocument()
    expect(await screen.findByText('Vui lòng nhập mật khẩu.')).toBeInTheDocument()
    expect(mockLogin).not.toHaveBeenCalled()
  })

  it('toggles password visibility', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    )

    const passwordInput = screen.getByPlaceholderText('Nhập mật khẩu')
    const toggleButton = screen.getByRole('button', { name: /hiện mật khẩu/i })

    expect(passwordInput).toHaveAttribute('type', 'password')
    fireEvent.click(toggleButton)
    expect(passwordInput).toHaveAttribute('type', 'text')
    fireEvent.click(screen.getByRole('button', { name: /ẩn mật khẩu/i }))
    expect(passwordInput).toHaveAttribute('type', 'password')
  })

  it('logs in successfully and navigates to /chat for normal user', async () => {
    mockLogin.mockResolvedValue({
      data: { username: 'testuser', role: { name: 'ROLE_USER' } },
    })

    const { container } = render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('Nhập tên đăng nhập'), {
      target: { name: 'username', value: 'testuser' },
    })
    fireEvent.change(screen.getByPlaceholderText('Nhập mật khẩu'), {
      target: { name: 'password', value: 'secret123' },
    })
    fireEvent.click(container.querySelector('button[type="submit"]'))

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith({ username: 'testuser', password: 'secret123' })
      expect(mockNavigate).toHaveBeenCalledWith('/chat', { replace: true })
    })
  })

  it('displays error alert on login failure', async () => {
    mockLogin.mockRejectedValue(new Error('Sai mật khẩu'))

    const { container } = render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('Nhập tên đăng nhập'), {
      target: { name: 'username', value: 'testuser' },
    })
    fireEvent.change(screen.getByPlaceholderText('Nhập mật khẩu'), {
      target: { name: 'password', value: 'wrongpassword' },
    })
    fireEvent.click(container.querySelector('button[type="submit"]'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Sai mật khẩu')
  })
})
