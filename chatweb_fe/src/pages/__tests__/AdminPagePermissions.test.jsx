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
    createRole: vi.fn().mockResolvedValue({ data: {} }),
    updateRole: vi.fn().mockResolvedValue({ data: {} }),
    deleteRole: vi.fn().mockResolvedValue({ data: {} }),
    lockUser: vi.fn().mockResolvedValue({ message: 'User locked' }),
    unlockUser: vi.fn().mockResolvedValue({ message: 'User unlocked' }),
    resolveReport: vi.fn().mockResolvedValue({ message: 'Report resolved' }),
    deleteReport: vi.fn().mockResolvedValue({ message: 'Report deleted' }),
    deleteUser: vi.fn().mockResolvedValue({ message: 'User deleted' }),
    deleteAvatar: vi.fn().mockResolvedValue({ message: 'Avatar deleted' }),
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

  it('renders "Không có quyền truy cập" when user only has user-level permissions like USER_SEND_WORLD_CHAT', () => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: { username: 'vip_user', role: 'VIP', permissions: ['USER_SEND_WORLD_CHAT'] },
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

  it('disables ADMIN role option when creating a new user in UserDrawer', async () => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: {
        username: 'admin',
        role: 'ADMIN',
        permissions: ['ADMIN_VIEW_USERS', 'ADMIN_CREATE', 'ROLE_VIEW_ALL'],
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
      expect(within(nav).getByText('Người dùng')).toBeInTheDocument()
    })
    fireEvent.click(within(nav).getByText('Người dùng'))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Tạo người dùng/i })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /Tạo người dùng/i }))

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })

    const dialog = screen.getByRole('dialog')
    const roleSelect = within(dialog).getByRole('combobox')
    const adminOption = within(roleSelect).getByText(/ADMIN/i)
    expect(adminOption).toBeDisabled()
    expect(adminOption.textContent).toContain('(Dành riêng cho root admin)')
  })

  it('protects root admin account in UserDrawer (disabled role and hidden lock/delete buttons)', async () => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: {
        username: 'subadmin',
        role: 'SUBADMIN',
        permissions: [
          'ADMIN_VIEW_USERS',
          'ADMIN_VIEW_USER_DETAIL',
          'ADMIN_UPDATE_USER',
          'ADMIN_LOCK',
          'ADMIN_DELETE_USER',
          'ROLE_VIEW_ALL',
        ],
      },
      logout: vi.fn(),
    })

    vi.mocked(adminApi.getUsers).mockResolvedValue({
      data: {
        content: [
          {
            id: 1,
            username: 'admin',
            firstName: 'Root',
            lastName: 'Admin',
            email: 'admin@cw.com',
            role: 'ADMIN',
            roleId: 1,
            userStatus: 'ACTIVE',
          },
        ],
        totalElements: 1,
        totalPages: 1,
      },
    })

    vi.mocked(adminApi.getUser).mockResolvedValue({
      data: {
        id: 1,
        username: 'admin',
        firstName: 'Root',
        lastName: 'Admin',
        email: 'admin@cw.com',
        role: 'ADMIN',
        roleId: 1,
        userStatus: 'ACTIVE',
      },
    })

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AdminPage />
      </MemoryRouter>
    )

    const nav = screen.getByRole('navigation')
    await waitFor(() => {
      expect(within(nav).getByText('Người dùng')).toBeInTheDocument()
    })
    fireEvent.click(within(nav).getByText('Người dùng'))

    await waitFor(() => {
      expect(screen.getByText('@admin')).toBeInTheDocument()
    })

    const detailBtn = screen.getByRole('button', { name: /Chi tiết/i })
    fireEvent.click(detailBtn)

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('Chỉ tài khoản quản trị viên gốc mới có quyền tự cập nhật thông tin của mình.')).toBeInTheDocument()
    expect(within(dialog).getByText('Không thể thay đổi vai trò của tài khoản quản trị viên gốc')).toBeInTheDocument()
    const roleSelect = within(dialog).getByRole('combobox')
    expect(roleSelect).toBeDisabled()

    // Form inputs must be disabled and "Lưu thay đổi" button hidden
    expect(within(dialog).getByLabelText(/Email/i)).toBeDisabled()
    expect(within(dialog).getByLabelText(/Họ/i)).toBeDisabled()
    expect(within(dialog).getByLabelText(/Tên/i)).toBeDisabled()
    expect(within(dialog).queryByRole('button', { name: 'Lưu thay đổi' })).toBeNull()

    // Neither lock nor delete button should be visible for root admin
    expect(within(dialog).queryByRole('button', { name: 'Khóa tài khoản' })).toBeNull()
    expect(within(dialog).queryByRole('button', { name: 'Xóa người dùng' })).toBeNull()
  })

  it('prevents creating role with system name (ADMIN or USER) in RolesSection', async () => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: {
        username: 'role_admin',
        role: 'ADMIN',
        permissions: ['ROLE_VIEW_ALL', 'ROLE_ADD'],
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
    fireEvent.click(within(nav).getByText('Role & quyền'))

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 2, name: 'Tạo role mới' })).toBeInTheDocument()
    })

    const roleNameInput = screen.getByLabelText(/Tên role/i)
    fireEvent.change(roleNameInput, { target: { value: 'admin' } })

    const submitBtn = screen.getByRole('button', { name: 'Tạo role' })
    fireEvent.click(submitBtn)

    // adminApi.createRole should NOT be called
    expect(adminApi.createRole).not.toHaveBeenCalled()
  })

  it('prevents updating role with system name (ADMIN or USER) in RolesSection', async () => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: {
        username: 'role_admin',
        role: 'ADMIN',
        permissions: ['ROLE_VIEW_ALL', 'ROLE_UPDATE'],
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
    fireEvent.click(within(nav).getByText('Role & quyền'))

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 3, name: 'MODERATOR' })).toBeInTheDocument()
    })

    const editBtn = screen.getByRole('button', { name: /Sửa/i })
    fireEvent.click(editBtn)

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 2, name: 'Cập nhật role' })).toBeInTheDocument()
    })

    const roleNameInput = screen.getByLabelText(/Tên role/i)
    fireEvent.change(roleNameInput, { target: { value: 'USER' } })

    const updateSubmitBtn = screen.getByRole('button', { name: 'Lưu thay đổi' })
    fireEvent.click(updateSubmitBtn)

    // adminApi.updateRole should NOT be called
    expect(adminApi.updateRole).not.toHaveBeenCalled()
  })

  it('allows root admin to edit self in UserDrawer (inputs enabled and "Lưu thay đổi" button present)', async () => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: {
        username: 'admin',
        role: 'ADMIN',
        permissions: ['ADMIN_VIEW_USERS', 'ADMIN_VIEW_USER_DETAIL', 'ADMIN_UPDATE_USER'],
      },
      logout: vi.fn(),
    })

    vi.mocked(adminApi.getUsers).mockResolvedValue({
      data: {
        content: [
          {
            id: 1,
            username: 'admin',
            firstName: 'Root',
            lastName: 'Admin',
            email: 'admin@cw.com',
            role: 'ADMIN',
            roleId: 1,
            userStatus: 'ACTIVE',
          },
        ],
        totalElements: 1,
        totalPages: 1,
      },
    })

    vi.mocked(adminApi.getUser).mockResolvedValue({
      data: {
        id: 1,
        username: 'admin',
        firstName: 'Root',
        lastName: 'Admin',
        email: 'admin@cw.com',
        role: 'ADMIN',
        roleId: 1,
        userStatus: 'ACTIVE',
      },
    })

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AdminPage />
      </MemoryRouter>
    )

    const nav = screen.getByRole('navigation')
    await waitFor(() => {
      expect(within(nav).getByText('Người dùng')).toBeInTheDocument()
    })
    fireEvent.click(within(nav).getByText('Người dùng'))

    await waitFor(() => {
      expect(screen.getAllByText('@admin').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByRole('button', { name: /Chi tiết/i })).toBeInTheDocument()
    })

    const detailBtn = screen.getByRole('button', { name: /Chi tiết/i })
    fireEvent.click(detailBtn)

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })

    const dialog = screen.getByRole('dialog')

    // Warning banner should NOT be present
    expect(within(dialog).queryByText('Chỉ tài khoản quản trị viên gốc mới có quyền tự cập nhật thông tin của mình.')).toBeNull()

    // Form inputs must be enabled for root admin self-editing
    expect(within(dialog).getByLabelText(/Email/i)).toBeEnabled()
    expect(within(dialog).getByLabelText(/Họ/i)).toBeEnabled()
    expect(within(dialog).getByLabelText(/Tên/i)).toBeEnabled()

    // "Lưu thay đổi" button MUST be present
    expect(within(dialog).getByRole('button', { name: 'Lưu thay đổi' })).toBeInTheDocument()
  })

  describe('Standalone action-permission unblocking', () => {
    it('allows standalone ADMIN_LOCK to access Users tab, lookup username, and lock account', async () => {
      vi.mocked(authContext.useAuth).mockReturnValue({
        user: {
          id: 10,
          username: 'operator',
          role: 'OPERATOR',
          permissions: ['ADMIN_LOCK'],
        },
      })

      render(
        <MemoryRouter initialEntries={['/admin']}>
          <AdminPage />
        </MemoryRouter>
      )

      const nav = screen.getByRole('navigation')
      await waitFor(() => {
        expect(within(nav).getByText('Người dùng')).toBeInTheDocument()
      })
      fireEvent.click(within(nav).getByText('Người dùng'))

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/Nhập @username để mở bảng thao tác/i)).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText(/Nhập @username để mở bảng thao tác/i)
      fireEvent.change(input, { target: { value: '@john_doe' } })
      fireEvent.click(screen.getByRole('button', { name: 'Mở thao tác' }))

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument()
      })

      const dialog = screen.getByRole('dialog')
      const lockBtn = within(dialog).getByRole('button', { name: 'Khóa tài khoản' })
      expect(lockBtn).toBeInTheDocument()

      fireEvent.click(lockBtn)
      await waitFor(() => {
        expect(adminApi.lockUser).toHaveBeenCalledWith('john_doe')
      })
    })

    it('allows ADMIN_VIEW_USERS + ADMIN_LOCK to click Chi tiết on a row and lock user without calling getUser', async () => {
      vi.mocked(authContext.useAuth).mockReturnValue({
        user: {
          id: 11,
          username: 'auditor',
          role: 'AUDITOR',
          permissions: ['ADMIN_VIEW_USERS', 'ADMIN_LOCK'],
        },
      })

      vi.mocked(adminApi.getUsers).mockResolvedValue({
        data: {
          content: [
            { id: 20, username: 'target_user', firstName: 'Target', lastName: 'User', userStatus: 'ACTIVE', role: 'USER' },
          ],
          totalElements: 1,
          totalPages: 1,
        },
      })

      render(
        <MemoryRouter initialEntries={['/admin']}>
          <AdminPage />
        </MemoryRouter>
      )

      const nav = screen.getByRole('navigation')
      await waitFor(() => {
        expect(within(nav).getByText('Người dùng')).toBeInTheDocument()
      })
      fireEvent.click(within(nav).getByText('Người dùng'))

      await waitFor(() => {
        expect(screen.getByText('@target_user')).toBeInTheDocument()
      })

      const detailBtn = screen.getByRole('button', { name: /Chi tiết/i })
      expect(detailBtn).not.toBeDisabled()
      fireEvent.click(detailBtn)

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument()
      })

      // Must not call adminApi.getUser because user lacks ADMIN_VIEW_USER_DETAIL
      expect(adminApi.getUser).not.toHaveBeenCalled()

      const dialog = screen.getByRole('dialog')
      const lockBtn = within(dialog).getByRole('button', { name: 'Khóa tài khoản' })
      expect(lockBtn).toBeInTheDocument()
      fireEvent.click(lockBtn)

      await waitFor(() => {
        expect(adminApi.lockUser).toHaveBeenCalledWith('target_user')
      })
    })

    it('allows standalone ADMIN_ROLE_ADD to access Roles tab and submit Create Role form', async () => {
      vi.mocked(authContext.useAuth).mockReturnValue({
        user: {
          id: 12,
          username: 'role_creator',
          role: 'CREATOR',
          permissions: ['ADMIN_ROLE_ADD'],
        },
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
      fireEvent.click(within(nav).getByText('Role & quyền'))

      await waitFor(() => {
        expect(screen.getByText('Tạo role mới')).toBeInTheDocument()
      })

      const nameInput = screen.getByPlaceholderText('VD: MODERATOR')
      fireEvent.change(nameInput, { target: { value: 'CONTENT_MODERATOR' } })

      const submitBtn = screen.getByRole('button', { name: 'Tạo role' })
      fireEvent.click(submitBtn)

      await waitFor(() => {
        expect(adminApi.createRole).toHaveBeenCalledWith(
          expect.objectContaining({ name: 'CONTENT_MODERATOR' })
        )
      })
    })

    it('allows standalone ADMIN_RESOLVE_REPORTS to access Reports tab and resolve report by ID', async () => {
      vi.mocked(authContext.useAuth).mockReturnValue({
        user: {
          id: 13,
          username: 'moderator_user',
          role: 'MODERATOR',
          permissions: ['ADMIN_RESOLVE_REPORTS'],
        },
      })

      render(
        <MemoryRouter initialEntries={['/admin']}>
          <AdminPage />
        </MemoryRouter>
      )

      const nav = screen.getByRole('navigation')
      await waitFor(() => {
        expect(within(nav).getByText('Báo cáo vi phạm')).toBeInTheDocument()
      })
      fireEvent.click(within(nav).getByText('Báo cáo vi phạm'))

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/Nhập Mã báo cáo \(#ID\)/i)).toBeInTheDocument()
      })

      // Unconditional loadReports should NOT have been called without ADMIN_VIEW_REPORTS
      expect(adminApi.getReports).not.toHaveBeenCalled()

      const input = screen.getByPlaceholderText(/Nhập Mã báo cáo \(#ID\)/i)
      fireEvent.change(input, { target: { value: '42' } })
      fireEvent.click(screen.getByRole('button', { name: 'Mở báo cáo' }))

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument()
      })

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByText('Báo cáo #42')).toBeInTheDocument()

      const saveBtn = within(dialog).getByRole('button', { name: 'Lưu kết quả' })
      fireEvent.click(saveBtn)

      await waitFor(() => {
        expect(adminApi.resolveReport).toHaveBeenCalledWith(42, expect.objectContaining({ status: 'RESOLVED' }))
      })
    })

    it('auto-selects prerequisite permissions when selecting an action permission in RolesSection', async () => {
      vi.mocked(authContext.useAuth).mockReturnValue({
        user: {
          id: 1,
          username: 'admin',
          role: 'ADMIN',
          permissions: ['ADMIN_ROLE_VIEW_ALL', 'ADMIN_ROLE_VIEW_ALL_PERMISSION', 'ADMIN_ROLE_ADD'],
        },
      })

      vi.mocked(adminApi.getPermissions).mockResolvedValue({
        data: [
          { id: 101, name: 'ADMIN_VIEW_USERS', description: 'Xem danh sách user' },
          { id: 102, name: 'ADMIN_VIEW_USER_DETAIL', description: 'Xem chi tiết user' },
          { id: 103, name: 'ADMIN_LOCK', description: 'Khóa tài khoản' },
        ],
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
      fireEvent.click(within(nav).getByText('Role & quyền'))

      await waitFor(() => {
        expect(screen.getByText('ADMIN_LOCK')).toBeInTheDocument()
      })

      const lockCheckbox = screen.getByLabelText(/ADMIN_LOCK/i)
      const viewUsersCheckbox = screen.getByLabelText(/ADMIN_VIEW_USERS/i)
      const viewDetailCheckbox = screen.getByLabelText(/ADMIN_VIEW_USER_DETAIL/i)

      expect(lockCheckbox).not.toBeChecked()
      expect(viewUsersCheckbox).not.toBeChecked()
      expect(viewDetailCheckbox).not.toBeChecked()

      fireEvent.click(lockCheckbox)

      await waitFor(() => {
        expect(lockCheckbox).toBeChecked()
        expect(viewUsersCheckbox).toBeChecked()
        expect(viewDetailCheckbox).toBeChecked()
      })
    })
  })
})

