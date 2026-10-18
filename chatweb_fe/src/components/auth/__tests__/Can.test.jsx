import React from 'react'
import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import Can, { Can as NamedCan } from '../Can.jsx'

// Mock usePermission hook
const mockCan = vi.fn()
const mockCanAny = vi.fn()
const mockCanAll = vi.fn()

vi.mock('../../../hooks/usePermission.js', () => ({
  usePermission: () => ({
    can: mockCan,
    canAny: mockCanAny,
    canAll: mockCanAll,
  }),
  default: () => ({
    can: mockCan,
    canAny: mockCanAny,
    canAll: mockCanAll,
  }),
}))

describe('Can Component', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCan.mockReturnValue(false)
    mockCanAny.mockReturnValue(false)
    mockCanAll.mockReturnValue(false)
  })

  it('exports both named Can and default Can identically', () => {
    expect(NamedCan).toBeDefined()
    expect(Can).toBeDefined()
    expect(NamedCan).toBe(Can)
  })

  describe('Single permission check', () => {
    it('renders children when single permission is granted', () => {
      mockCan.mockImplementation((perm) => perm === 'USER_VIEW')

      render(
        <Can permission="USER_VIEW">
          <div data-testid="allowed-content">Visible to User</div>
        </Can>
      )

      expect(mockCan).toHaveBeenCalledWith('USER_VIEW')
      expect(screen.getByTestId('allowed-content')).toBeInTheDocument()
      expect(screen.getByText('Visible to User')).toBeInTheDocument()
    })

    it('renders null fallback when single permission is denied and no fallback provided', () => {
      mockCan.mockReturnValue(false)

      const { container } = render(
        <Can permission="ADMIN_DELETE">
          <div data-testid="secret-content">Secret Action</div>
        </Can>
      )

      expect(mockCan).toHaveBeenCalledWith('ADMIN_DELETE')
      expect(screen.queryByTestId('secret-content')).not.toBeInTheDocument()
      expect(container).toBeEmptyDOMElement()
    })

    it('renders custom fallback element when permission is denied', () => {
      mockCan.mockReturnValue(false)

      render(
        <Can
          permission="ADMIN_LOCK"
          fallback={<span data-testid="fallback-msg">No access allowed</span>}
        >
          <div data-testid="secret-content">Secret Action</div>
        </Can>
      )

      expect(screen.queryByTestId('secret-content')).not.toBeInTheDocument()
      expect(screen.getByTestId('fallback-msg')).toBeInTheDocument()
      expect(screen.getByText('No access allowed')).toBeInTheDocument()
    })
  })

  describe('anyOf permissions check', () => {
    it('renders children when canAny matches at least one permission in anyOf', () => {
      mockCanAny.mockImplementation((perms) => perms.includes('ROLE_VIEW_ALL'))

      render(
        <Can anyOf={['ROLE_VIEW_ALL', 'ROLE_MANAGE']}>
          <div data-testid="role-content">Role Management</div>
        </Can>
      )

      expect(mockCanAny).toHaveBeenCalledWith(['ROLE_VIEW_ALL', 'ROLE_MANAGE'])
      expect(screen.getByTestId('role-content')).toBeInTheDocument()
    })

    it('renders fallback when canAny returns false for anyOf array', () => {
      mockCanAny.mockReturnValue(false)

      render(
        <Can
          anyOf={['ADMIN_LOCK', 'ADMIN_UNLOCK']}
          fallback={<div>Access Denied</div>}
        >
          <div data-testid="restricted">Restricted</div>
        </Can>
      )

      expect(screen.queryByTestId('restricted')).not.toBeInTheDocument()
      expect(screen.getByText('Access Denied')).toBeInTheDocument()
    })

    it('does not evaluate anyOf when given an empty array', () => {
      render(
        <Can anyOf={[]}>
          <div data-testid="default-content">Default Render</div>
        </Can>
      )

      expect(mockCanAny).not.toHaveBeenCalled()
      expect(screen.getByTestId('default-content')).toBeInTheDocument()
    })
  })

  describe('allOf permissions check', () => {
    it('renders children when canAll returns true for all permissions in allOf', () => {
      mockCanAll.mockImplementation((perms) => perms.includes('USER_EDIT') && perms.includes('USER_DELETE'))

      render(
        <Can allOf={['USER_EDIT', 'USER_DELETE']}>
          <div data-testid="all-content">Full User Operations</div>
        </Can>
      )

      expect(mockCanAll).toHaveBeenCalledWith(['USER_EDIT', 'USER_DELETE'])
      expect(screen.getByTestId('all-content')).toBeInTheDocument()
    })

    it('renders fallback when canAll returns false for allOf array', () => {
      mockCanAll.mockReturnValue(false)

      render(
        <Can
          allOf={['USER_EDIT', 'SUPER_PRIVILEGE']}
          fallback={<p>Missing Permissions</p>}
        >
          <div data-testid="all-content">Full User Operations</div>
        </Can>
      )

      expect(screen.queryByTestId('all-content')).not.toBeInTheDocument()
      expect(screen.getByText('Missing Permissions')).toBeInTheDocument()
    })

    it('does not evaluate allOf when given an empty array', () => {
      render(
        <Can allOf={[]}>
          <div data-testid="default-content">Default Render</div>
        </Can>
      )

      expect(mockCanAll).not.toHaveBeenCalled()
      expect(screen.getByTestId('default-content')).toBeInTheDocument()
    })
  })

  describe('Combination checks (permission + anyOf + allOf)', () => {
    it('requires all active checks to pass when combined', () => {
      mockCan.mockReturnValue(true)
      mockCanAny.mockReturnValue(true)
      mockCanAll.mockReturnValue(true)

      render(
        <Can
          permission="READ"
          anyOf={['WRITE', 'ADMIN']}
          allOf={['AUDIT_LOG', 'SETTINGS_VIEW']}
        >
          <div data-testid="super-restricted">All Matched</div>
        </Can>
      )

      expect(mockCan).toHaveBeenCalledWith('READ')
      expect(mockCanAny).toHaveBeenCalledWith(['WRITE', 'ADMIN'])
      expect(mockCanAll).toHaveBeenCalledWith(['AUDIT_LOG', 'SETTINGS_VIEW'])
      expect(screen.getByTestId('super-restricted')).toBeInTheDocument()
    })

    it('denies access if any combined check fails', () => {
      mockCan.mockReturnValue(true)
      mockCanAny.mockReturnValue(false)
      mockCanAll.mockReturnValue(true)

      render(
        <Can
          permission="READ"
          anyOf={['WRITE', 'ADMIN']}
          allOf={['AUDIT_LOG', 'SETTINGS_VIEW']}
          fallback={<span>Denied</span>}
        >
          <div data-testid="super-restricted">All Matched</div>
        </Can>
      )

      expect(screen.queryByTestId('super-restricted')).not.toBeInTheDocument()
      expect(screen.getByText('Denied')).toBeInTheDocument()
    })
  })

  describe('not={true} inversion check', () => {
    it('inverts check: renders children when permission is NOT granted', () => {
      mockCan.mockReturnValue(false)

      render(
        <Can permission="ADMIN_ACCESS" not={true}>
          <div data-testid="guest-banner">Welcome Regular User</div>
        </Can>
      )

      expect(mockCan).toHaveBeenCalledWith('ADMIN_ACCESS')
      expect(screen.getByTestId('guest-banner')).toBeInTheDocument()
    })

    it('inverts check: renders fallback when permission IS granted but not={true}', () => {
      mockCan.mockReturnValue(true)

      render(
        <Can
          permission="ADMIN_ACCESS"
          not={true}
          fallback={<span data-testid="admin-notice">Admins do not see this</span>}
        >
          <div data-testid="guest-banner">Welcome Regular User</div>
        </Can>
      )

      expect(screen.queryByTestId('guest-banner')).not.toBeInTheDocument()
      expect(screen.getByTestId('admin-notice')).toBeInTheDocument()
    })
  })

  describe('Render-prop pattern', () => {
    it('passes { allowed: true } to children function when permission is granted', () => {
      mockCan.mockReturnValue(true)

      render(
        <Can permission="MESSAGE_SEND">
          {({ allowed }) => (
            <button data-testid="send-btn" disabled={!allowed}>
              {allowed ? 'CAN SEND' : 'CANNOT SEND'}
            </button>
          )}
        </Can>
      )

      expect(screen.getByTestId('send-btn')).toBeInTheDocument()
      expect(screen.getByText('CAN SEND')).toBeInTheDocument()
      expect(screen.getByTestId('send-btn')).not.toBeDisabled()
    })

    it('renders fallback when permission is not granted even if children is a function', () => {
      mockCan.mockReturnValue(false)

      render(
        <Can
          permission="MESSAGE_SEND"
          fallback={<span data-testid="fn-fallback">Fallback for fn</span>}
        >
          {({ allowed }) => <span>{allowed ? 'YES' : 'NO'}</span>}
        </Can>
      )

      expect(screen.queryByText('YES')).not.toBeInTheDocument()
      expect(screen.queryByText('NO')).not.toBeInTheDocument()
      expect(screen.getByTestId('fn-fallback')).toBeInTheDocument()
    })

    it('supports render-prop with not={true}', () => {
      mockCan.mockReturnValue(false)

      render(
        <Can permission="RESTRICTED_FEATURE" not={true}>
          {({ allowed }) => (
            <span data-testid="inverted-prop">{allowed ? 'ALLOWED_VIA_NOT' : 'DENIED'}</span>
          )}
        </Can>
      )

      expect(screen.getByTestId('inverted-prop')).toBeInTheDocument()
      expect(screen.getByText('ALLOWED_VIA_NOT')).toBeInTheDocument()
    })
  })

  describe('Default props behavior', () => {
    it('renders children by default when no permission criteria are specified', () => {
      render(
        <Can>
          <div data-testid="unconditional">Always visible</div>
        </Can>
      )

      expect(screen.getByTestId('unconditional')).toBeInTheDocument()
      expect(mockCan).not.toHaveBeenCalled()
      expect(mockCanAny).not.toHaveBeenCalled()
      expect(mockCanAll).not.toHaveBeenCalled()
    })
  })
})
