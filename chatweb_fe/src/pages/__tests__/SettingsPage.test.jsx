import React from 'react'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import SettingsPage from '../SettingsPage.jsx'
import { reportApi } from '../../services/reportApi.js'
import * as apiClient from '../../services/apiClient.js'
import * as authContext from '../../context/auth-context.js'
import * as languageContext from '../../context/language-context.js'

vi.mock('../../services/apiClient.js', async () => {
  const actual = await vi.importActual('../../services/apiClient.js')
  return {
    ...actual,
    apiRequest: vi.fn(),
  }
})

vi.mock('../../context/auth-context.js', () => ({
  useAuth: vi.fn(),
}))

vi.mock('../../context/language-context.js', () => ({
  useLanguage: vi.fn(),
}))

describe('SettingsPage Reports Tab', () => {
  const mockUser = { username: 'testuser', firstName: 'Test', lastName: 'User', role: 'USER' }
  const mockT = (key) => key

  const mockReportsData = {
    content: [
      {
        id: 101,
        reportedUser: { username: 'badactor', firstName: 'Bad', lastName: 'Actor', avatar: '' },
        reason: 'SPAM',
        details: 'Sent unsolicited spam messages repeatedly.',
        status: 'PENDING',
        resolutionNote: null,
        createdAt: '2026-09-30T10:00:00Z',
      },
      {
        id: 102,
        reportedUser: { username: 'harasser', firstName: 'Mean', lastName: 'Person', avatar: '' },
        reason: 'HARASSMENT',
        details: 'Offensive language in chat.',
        status: 'RESOLVED',
        resolutionNote: 'User account has been issued a warning.',
        createdAt: '2026-09-29T15:30:00Z',
      },
      {
        id: 103,
        reportedUser: { username: 'innocent', firstName: '', lastName: '', avatar: '' },
        reason: 'OTHER',
        details: 'Not sure about this user.',
        status: 'DISMISSED',
        resolutionNote: 'Insufficient evidence of violation.',
        createdAt: '2026-09-28T08:00:00Z',
      },
    ],
    pageNo: 0,
    pageSize: 10,
    totalElements: 3,
    totalPages: 1,
    last: true,
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: mockUser,
      refreshUser: vi.fn(),
      logoutEverywhere: vi.fn(),
      deleteAccount: vi.fn(),
    })
    vi.mocked(languageContext.useLanguage).mockReturnValue({
      language: 'vi',
      setLanguage: vi.fn(),
      t: mockT,
    })
    vi.mocked(apiClient.apiRequest).mockImplementation((url) => {
      if (typeof url === 'string' && (url.includes('/api/users/profile') || url.includes('/api/account/profile'))) return Promise.resolve({ data: mockUser })
      if (typeof url === 'string' && (url.includes('/api/users/addresses') || url.includes('/api/account/addresses'))) return Promise.resolve({ data: [] })
      if (typeof url === 'string' && url.includes('/api/reports/me')) return Promise.resolve({ data: mockReportsData })
      return Promise.resolve({ data: {} })
    })
  })

  it('renders reports tab and fetches submitted reports when navigating to /settings?tab=reports', async () => {
    const getMyReportsSpy = vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: mockReportsData })

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(getMyReportsSpy).toHaveBeenCalledWith(0, 10, 'desc')
    })

    await waitFor(() => {
      expect(screen.getByText('Bad Actor')).toBeInTheDocument()
      expect(screen.getByText('@badactor')).toBeInTheDocument()
      expect(screen.getByText('Sent unsolicited spam messages repeatedly.')).toBeInTheDocument()
      expect(screen.getByText('reportStatusPending')).toBeInTheDocument()
    })
  })

  it('displays report details including statuses, dates, and admin resolution notes', async () => {
    vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: mockReportsData })

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      // Resolved report
      expect(screen.getByText('Mean Person')).toBeInTheDocument()
      expect(screen.getByText('reportStatusResolved')).toBeInTheDocument()
      expect(screen.getByText('User account has been issued a warning.')).toBeInTheDocument()

      // Dismissed report
      expect(screen.getByText('@innocent')).toBeInTheDocument()
      expect(screen.getByText('reportStatusDismissed')).toBeInTheDocument()
      expect(screen.getByText('Insufficient evidence of violation.')).toBeInTheDocument()
    })
  })

  it('shows informative empty state when user has no submitted reports', async () => {
    vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({
      data: { content: [], pageNo: 0, pageSize: 10, totalElements: 0, totalPages: 0, last: true },
    })

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('noReports')).toBeInTheDocument()
    })
  })

  it('cancels pending report after confirmation and re-fetches list', async () => {
    const getMyReportsSpy = vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: mockReportsData })
    const cancelReportSpy = vi.spyOn(reportApi, 'cancelReport').mockResolvedValue({
      code: 200,
      message: 'reportCancelledSuccess',
    })

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Bad Actor')).toBeInTheDocument()
    })

    const cancelButtons = screen.getAllByRole('button', { name: 'cancelReport' })
    expect(cancelButtons).toHaveLength(1)

    fireEvent.click(cancelButtons[0])

    expect(confirmSpy).toHaveBeenCalled()
    await waitFor(() => {
      expect(cancelReportSpy).toHaveBeenCalledWith(101)
    })

    await waitFor(() => {
      expect(getMyReportsSpy).toHaveBeenCalledTimes(2)
    })

    confirmSpy.mockRestore()
  })

  it('does not cancel if user declines confirmation', async () => {
    vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: mockReportsData })
    const cancelReportSpy = vi.spyOn(reportApi, 'cancelReport')
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Bad Actor')).toBeInTheDocument()
    })

    const cancelButtons = screen.getAllByRole('button', { name: 'cancelReport' })
    fireEvent.click(cancelButtons[0])

    expect(confirmSpy).toHaveBeenCalled()
    expect(cancelReportSpy).not.toHaveBeenCalled()

    confirmSpy.mockRestore()
  })

  it('does not allow cancel action for RESOLVED or DISMISSED reports', async () => {
    const nonPendingReports = {
      content: [mockReportsData.content[1], mockReportsData.content[2]],
      pageNo: 0,
      pageSize: 10,
      totalElements: 2,
      totalPages: 1,
      last: true,
    }
    vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: nonPendingReports })

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Mean Person')).toBeInTheDocument()
      expect(screen.getByText('@innocent')).toBeInTheDocument()
    })

    expect(screen.queryByRole('button', { name: 'cancelReport' })).toBeNull()
  })

  it('handles cancellation error gracefully by displaying notification', async () => {
    vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: mockReportsData })
    vi.spyOn(reportApi, 'cancelReport').mockRejectedValue(new Error('Network error'))
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Bad Actor')).toBeInTheDocument()
    })

    const cancelButtons = screen.getAllByRole('button', { name: 'cancelReport' })
    fireEvent.click(cancelButtons[0])

    await waitFor(() => {
      expect(screen.getByRole('status')).toBeInTheDocument()
      expect(cancelButtons[0]).not.toBeDisabled()
    })

    confirmSpy.mockRestore()
  })

  it('renders pagination controls and navigates between pages', async () => {
    const multiPageReports = {
      content: [mockReportsData.content[0]],
      pageNo: 0,
      pageSize: 1,
      totalElements: 2,
      totalPages: 2,
      last: false,
    }
    const getMyReportsSpy = vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: multiPageReports })

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Bad Actor')).toBeInTheDocument()
    })

    // Next page button should be enabled
    const nextBtn = screen.getByRole('button', { name: /Sau|nextPage/i })
    expect(nextBtn).not.toBeDisabled()

    fireEvent.click(nextBtn)

    await waitFor(() => {
      expect(getMyReportsSpy).toHaveBeenCalledWith(1, 10, 'desc')
    })
  })

  it('renders fallback gracefully when reportedUser is null or missing details', async () => {
    const edgeCaseReports = {
      content: [
        {
          id: 201,
          reportedUser: null,
          reason: 'SPAM',
          details: '',
          status: 'PENDING',
          resolutionNote: null,
          createdAt: null,
        },
      ],
      pageNo: 0,
      pageSize: 10,
      totalElements: 1,
      totalPages: 1,
      last: true,
    }
    vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: edgeCaseReports })

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('noReportDetails')).toBeInTheDocument()
      expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('adjusts page index when cancelling the last item on page > 0', async () => {
    const page1Data = {
      content: [
        {
          id: 301,
          reportedUser: { username: 'lastitem', firstName: 'Last', lastName: 'Item' },
          reason: 'OTHER',
          details: 'Only item on page 1',
          status: 'PENDING',
          createdAt: '2026-09-25T12:00:00Z',
        },
      ],
      pageNo: 1,
      pageSize: 10,
      totalElements: 11,
      totalPages: 2,
      last: true,
    }
    const getMyReportsSpy = vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: page1Data })
    vi.spyOn(reportApi, 'cancelReport').mockResolvedValue({ code: 200, message: 'Cancelled' })
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Last Item')).toBeInTheDocument()
    })

    const cancelBtn = screen.getByRole('button', { name: 'cancelReport' })
    fireEvent.click(cancelBtn)

    // Should request page 0 (pageNo - 1) because the remaining items on page 1 is 0
    await waitFor(() => {
      expect(getMyReportsSpy).toHaveBeenLastCalledWith(0, 10, 'desc')
    })

    confirmSpy.mockRestore()
  })

  it('displays error state with retry button when getMyReports fails, and recovers on retry', async () => {
    const getMyReportsSpy = vi.spyOn(reportApi, 'getMyReports')
      .mockRejectedValueOnce(new Error('Internal Server Error 500'))

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    // Should display notice banner and error state inside the reports tab
    await waitFor(() => {
      expect(screen.getByRole('status')).toBeInTheDocument()
      expect(screen.getByTestId('reports-error-state')).toBeInTheDocument()
    })

    // Must NOT display "noReports" ("Chưa có báo cáo nào") when fetch actually failed
    expect(screen.queryByText('noReports')).toBeNull()

    // Retry button should be rendered
    const retryBtn = screen.getByRole('button', { name: 'retry' })
    expect(retryBtn).toBeInTheDocument()

    // Configure success on retry
    getMyReportsSpy.mockResolvedValueOnce({ data: mockReportsData })
    fireEvent.click(retryBtn)

    await waitFor(() => {
      expect(screen.getByText('Bad Actor')).toBeInTheDocument()
      expect(screen.queryByTestId('reports-error-state')).toBeNull()
    })
  })

  it('handles empty or malformed backend response gracefully without crashing', async () => {
    vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: null })

    render(
      <MemoryRouter initialEntries={['/settings?tab=reports']}>
        <SettingsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('noReports')).toBeInTheDocument()
    })
  })

  it('switches between settings tabs and syncs with URL', async () => {
    const getMyReportsSpy = vi.spyOn(reportApi, 'getMyReports').mockResolvedValue({ data: mockReportsData })

    render(
      <MemoryRouter initialEntries={['/settings?tab=profile']}>
        <SettingsPage />
      </MemoryRouter>
    )

    // On profile tab, reports should not be fetched
    expect(getMyReportsSpy).not.toHaveBeenCalled()

    // Click reports tab in sidebar
    const reportsTabBtn = screen.getByRole('button', { name: 'myReports' })
    fireEvent.click(reportsTabBtn)

    await waitFor(() => {
      expect(getMyReportsSpy).toHaveBeenCalledWith(0, 10, 'desc')
      expect(screen.getByText('Bad Actor')).toBeInTheDocument()
    })
  })
})
