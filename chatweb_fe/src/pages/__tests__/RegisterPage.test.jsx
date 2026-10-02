import React from 'react'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import RegisterPage from '../auth/RegisterPage.jsx'
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

describe('RegisterPage', () => {
  const mockRegister = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    authContext.useAuth.mockReturnValue({
      register: mockRegister,
    })
  })

  it('renders registration form fields', () => {
    const { container } = render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    )

    expect(screen.getByPlaceholderText('chatwithme')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('ban@email.com')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Tối thiểu 8 ký tự')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Nhập lại mật khẩu')).toBeInTheDocument()
    expect(screen.getByRole('checkbox')).toBeInTheDocument()
    expect(container.querySelector('button[type="submit"]')).toBeInTheDocument()
  })

  it('validates form errors on empty submit', async () => {
    const { container } = render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    )

    fireEvent.click(container.querySelector('button[type="submit"]'))

    expect(await screen.findByText('Vui lòng nhập tên đăng nhập.')).toBeInTheDocument()
    expect(await screen.findByText('Vui lòng nhập email.')).toBeInTheDocument()
    expect(await screen.findByText('Vui lòng nhập mật khẩu.')).toBeInTheDocument()
    expect(await screen.findByText('Vui lòng đồng ý với điều khoản để tiếp tục.')).toBeInTheDocument()
    expect(mockRegister).not.toHaveBeenCalled()
  })

  it('validates password mismatch and terms acceptance', async () => {
    const { container } = render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('chatwithme'), { target: { name: 'username', value: 'john' } })
    fireEvent.change(screen.getByPlaceholderText('ban@email.com'), { target: { name: 'email', value: 'john@example.com' } })
    fireEvent.change(screen.getByPlaceholderText('Tối thiểu 8 ký tự'), { target: { name: 'password', value: 'Password123!' } })
    fireEvent.change(screen.getByPlaceholderText('Nhập lại mật khẩu'), { target: { name: 'confirmPassword', value: 'Different123!' } })

    fireEvent.click(container.querySelector('button[type="submit"]'))

    expect(await screen.findByText('Mật khẩu xác nhận chưa khớp.')).toBeInTheDocument()
    expect(await screen.findByText('Vui lòng đồng ý với điều khoản để tiếp tục.')).toBeInTheDocument()
    expect(mockRegister).not.toHaveBeenCalled()
  })

  it('submits form successfully and navigates to /verify-account', async () => {
    mockRegister.mockResolvedValue({ message: 'Đăng ký thành công' })

    const { container } = render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('chatwithme'), { target: { name: 'username', value: 'john' } })
    fireEvent.change(screen.getByPlaceholderText('ban@email.com'), { target: { name: 'email', value: 'john@example.com' } })
    fireEvent.change(screen.getByPlaceholderText('Tối thiểu 8 ký tự'), { target: { name: 'password', value: 'Password123!' } })
    fireEvent.change(screen.getByPlaceholderText('Nhập lại mật khẩu'), { target: { name: 'confirmPassword', value: 'Password123!' } })
    fireEvent.click(screen.getByRole('checkbox'))

    fireEvent.click(container.querySelector('button[type="submit"]'))

    await waitFor(() => {
      expect(mockRegister).toHaveBeenCalledWith({
        username: 'john',
        email: 'john@example.com',
        password: 'Password123!',
      })
      expect(mockNavigate).toHaveBeenCalledWith('/verify-account', expect.objectContaining({
        replace: true,
        state: { email: 'john@example.com', message: 'Đăng ký thành công' },
      }))
    })
  })
})
