import React from 'react'
import { render, screen, waitFor, within, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import AdminPage from '../AdminPage.jsx'
import { adminApi } from '../../services/adminApi.js'
import * as authContext from '../../context/auth-context.js'
import * as languageContext from '../../context/language-context.js'

vi.mock('../../context/auth-context.js', () => ({
  useAuth: vi.fn(),
}))

vi.mock('../../context/language-context.js', () => ({
  useLanguage: vi.fn(),
}))

vi.mock('../../context/ConfirmDialogContext.jsx', () => ({
  useConfirm: () => vi.fn().mockResolvedValue(true),
  ConfirmDialogProvider: ({ children }) => children,
}))

vi.mock('../../hooks/useChatSocket.js', () => ({
  useChatSocket: () => ({
    connectionState: 'connected',
    sendWorldMessage: vi.fn(),
  }),
}))

vi.mock('../../services/adminApi.js', () => ({
  adminApi: {
    getUsers: vi.fn().mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } }),
    getOnlineUsers: vi.fn().mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } }),
    getRoles: vi.fn().mockResolvedValue({
      data: [
        { id: 1, name: 'ADMIN', description: 'System Admin Role', permissions: [] },
        { id: 2, name: 'USER', description: 'Default User Role', permissions: [] },
        { id: 3, name: 'MODERATOR', description: 'Custom Moderator', permissions: [{ id: 10, name: 'ADMIN_VIEW_REPORTS' }] },
      ],
    }),
    getPermissions: vi.fn().mockResolvedValue({ data: [] }),
    getReports: vi.fn().mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } }),
    getReport: vi.fn().mockResolvedValue({ data: null }),
    getReportStatistics: vi.fn().mockResolvedValue({ data: { pendingReports: 5, totalReports: 20 } }),
    getSystemMessages: vi.fn().mockResolvedValue({ data: { content: [] } }),
    getUser: vi.fn().mockResolvedValue({ data: null }),
    getAddresses: vi.fn().mockResolvedValue({ data: [] }),
  },
}))

describe('AdminPage Permission-Based UI Rendering', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.mocked(languageContext.useLanguage).mockReturnValue({
      language: 'vi',
      setLanguage: vi.fn(),
      t: (key) => key,
    })
  })

  it('renders "Không có quyền truy cập" when user has zero admin permissions', () => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: { username: 'regular_user', role: 'USER', permissions: [] },
      logout: vi.fn(),
    })

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AdminPage />
      </MemoryRouter>
    )

    expect(screen.getByText('Không có quyền truy cập')).toBeInTheDocument()
    expect(screen.queryByText('CONTROL CENTER')).toBeNull()
  })

  it('renders navigation tabs strictly corresponding to granted permissions for a Moderator', async () => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: {
        username: 'mod_user',
        role: 'MODERATOR',
        permissions: ['ADMIN_VIEW_REPORTS'],
      },
      logout: vi.fn(),
    })

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AdminPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('CONTROL CENTER')).toBeInTheDocument()
    })

    const nav = screen.getByRole('navigation')

    // Should see Dashboard (Tổng quan) and Reports (Báo cáo vi phạm) in sidebar nav
    expect(within(nav).getByText('Tổng quan')).toBeInTheDocument()
    expect(within(nav).getByText('Báo cáo vi phạm')).toBeInTheDocument()

    // Should NOT see other tabs in sidebar nav
    expect(within(nav).queryByText('Người dùng')).toBeNull()
    expect(within(nav).queryByText('Role & quyền')).toBeNull()
    expect(within(nav).queryByText('Email hệ thống')).toBeNull()
    expect(within(nav).queryByText('Thông báo hệ thống')).toBeNull()
  })

  it('renders system role protection tag and hides Sửa/Xóa for ADMIN and USER roles in RolesSection', async () => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: {
        username: 'role_admin',
        role: 'ADMIN',
        permissions: [
          'ROLE_VIEW_ALL',
          'ROLE_UPDATE',
          'ROLE_DELETE',
          'ROLE_ADD',
        ],
      },
      logout: vi.fn(),
    })

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AdminPage />
      </MemoryRouter>
    )

    const nav = screen.getByRole('navigation')
    await waitFor(() => {
      expect(within(nav).getByText('Role & quyền')).toBeInTheDocument()
    })

    // Click on Role & quyền tab in navigation
    fireEvent.click(within(nav).getByText('Role & quyền'))

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 3, name: 'ADMIN' })).toBeInTheDocument()
      expect(screen.getByRole('heading', { level: 3, name: 'USER' })).toBeInTheDocument()
      expect(screen.getByRole('heading', { level: 3, name: 'MODERATOR' })).toBeInTheDocument()
    })

    // Protected tags for system roles
    const protectedTags = screen.getAllByText('Role mặc định hệ thống')
    expect(protectedTags.length).toBe(2) // One for ADMIN, one for USER

    // Custom role MODERATOR should have Sửa and Xóa buttons
    expect(screen.getByRole('button', { name: /Sửa/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Xóa/i })).toBeInTheDocument()
  })
})
