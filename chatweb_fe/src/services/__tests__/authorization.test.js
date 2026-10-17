import { describe, it, expect } from 'vitest'
import {
  PERMISSIONS,
  SYSTEM_ROLES,
  ADMIN_CONSOLE_PERMISSIONS,
  normalizeRoleName,
  isSystemRole,
  isSuperAdmin,
  isAdminUser,
  isAdminAccount,
  getUserPermissionSet,
  hasPermission,
  hasAnyPermission,
  hasAllPermissions,
  canAssignRole,
} from '../authorization.js'

describe('authorization service', () => {
  // Pre-existing tests
  it('identifies admin user by role string', () => {
    expect(isAdminUser({ role: 'ROLE_ADMIN' })).toBe(true)
    expect(isAdminUser({ role: 'ADMIN' })).toBe(true)
    expect(isAdminUser({ role: 'USER' })).toBe(false)
    expect(isAdminUser(null)).toBe(false)
  })

  it('identifies admin user by permissions', () => {
    expect(isAdminUser({ permissions: ['ADMIN_VIEW_USERS'] })).toBe(true)
    expect(isAdminUser({ permissions: ['ROLE_MODERATOR'] })).toBe(true)
    expect(isAdminUser({ permissions: ['SEND_EMAIL'] })).toBe(true)
    expect(isAdminUser({ permissions: ['READ_MESSAGE'] })).toBe(false)
  })

  it('identifies admin account properly', () => {
    expect(isAdminAccount({ role: 'ADMIN' })).toBe(true)
    expect(isAdminAccount({ authorities: ['ROLE_ADMIN'] })).toBe(true)
    expect(isAdminAccount({ roles: ['ADMIN'] })).toBe(true)
    expect(isAdminAccount({ username: 'user1', role: 'USER' })).toBe(false)
    expect(isAdminAccount(null)).toBe(false)
  })

  it('checks specific permissions correctly', () => {
    expect(hasPermission({ role: 'ADMIN' }, 'ANY_PERMISSION')).toBe(true)
    expect(hasPermission({ permissions: ['DELETE_MESSAGE'] }, 'DELETE_MESSAGE')).toBe(true)
    expect(hasPermission({ permissions: ['READ_MESSAGE'] }, 'DELETE_MESSAGE')).toBe(false)
  })

  it('does not grant all permissions to users with only specific admin permissions', () => {
    const userWithOnlyCreate = { role: 'MANAGER', permissions: ['ADMIN_CREATE'] }
    expect(isAdminUser(userWithOnlyCreate)).toBe(true)
    expect(hasPermission(userWithOnlyCreate, 'ADMIN_CREATE')).toBe(true)
    expect(hasPermission(userWithOnlyCreate, 'ADMIN_LOCK')).toBe(false)
    expect(hasPermission(userWithOnlyCreate, 'ROLE_DELETE')).toBe(false)
    expect(hasPermission(userWithOnlyCreate, 'SEND_EMAIL')).toBe(false)
  })

  // New test suites for Milestone 1
  describe('PERMISSIONS constants', () => {
    it('defines exactly 22 permissions and is frozen', () => {
      const keys = Object.keys(PERMISSIONS)
      expect(keys).toHaveLength(22)
      expect(Object.isFrozen(PERMISSIONS)).toBe(true)
      expect(Object.isFrozen(SYSTEM_ROLES)).toBe(true)
      expect(Object.isFrozen(ADMIN_CONSOLE_PERMISSIONS)).toBe(true)
      expect(ADMIN_CONSOLE_PERMISSIONS).toHaveLength(22)
    })

    it('contains specific hyphenated ADMIN_SEND-MESSAGE', () => {
      expect(PERMISSIONS.ADMIN_SEND_MESSAGE).toBe('ADMIN_SEND-MESSAGE')
    })

    it('contains all required permissions', () => {
      const expected = [
        'ADMIN_VIEW_USERS',
        'ADMIN_VIEW_ONLINE_USERS',
        'ADMIN_VIEW_USER_DETAIL',
        'ADMIN_VIEW_USER_ADDRESSES',
        'ADMIN_CREATE',
        'ADMIN_UPDATE_USER',
        'ADMIN_UPDATE_USER_ADDRESS',
        'ADMIN_DELETE_USER',
        'ADMIN_DELETE_USER_ADDRESS',
        'ADMIN_LOCK',
        'ADMIN_UNLOCK',
        'ADMIN_DELETE_AVATAR',
        'ROLE_VIEW_ALL',
        'ROLE_VIEW_ALL_PERMISSION',
        'ROLE_ADD',
        'ROLE_UPDATE',
        'ROLE_DELETE',
        'SEND_EMAIL',
        'ADMIN_SEND-MESSAGE',
        'ADMIN_VIEW_REPORTS',
        'ADMIN_RESOLVE_REPORTS',
        'ADMIN_DELETE_REPORTS',
      ]
      expected.forEach((perm) => {
        expect(Object.values(PERMISSIONS)).toContain(perm)
      })
    })
  })

  describe('normalizeRoleName & isSystemRole', () => {
    it('normalizes string and object role names', () => {
      expect(normalizeRoleName('admin')).toBe('ADMIN')
      expect(normalizeRoleName(' role_user ')).toBe('ROLE_USER')
      expect(normalizeRoleName({ name: ' moderator ' })).toBe('MODERATOR')
      expect(normalizeRoleName(null)).toBe('')
      expect(normalizeRoleName(undefined)).toBe('')
    })

    it('identifies system roles strictly (ADMIN and USER)', () => {
      expect(isSystemRole('ADMIN')).toBe(true)
      expect(isSystemRole('ROLE_ADMIN')).toBe(true)
      expect(isSystemRole('USER')).toBe(true)
      expect(isSystemRole('ROLE_USER')).toBe(true)
      expect(isSystemRole({ name: 'ADMIN' })).toBe(true)
      expect(isSystemRole({ name: 'ROLE_USER' })).toBe(true)

      expect(isSystemRole('MODERATOR')).toBe(false)
      expect(isSystemRole('SUB_ADMIN')).toBe(false)
      expect(isSystemRole('CHAT_ADMIN')).toBe(false)
      expect(isSystemRole('')).toBe(false)
      expect(isSystemRole(null)).toBe(false)
    })
  })

  describe('isSuperAdmin strict matching', () => {
    it('returns true for ADMIN and ROLE_ADMIN', () => {
      expect(isSuperAdmin({ role: 'ADMIN' })).toBe(true)
      expect(isSuperAdmin({ role: 'ROLE_ADMIN' })).toBe(true)
      expect(isSuperAdmin({ roles: ['ROLE_ADMIN'] })).toBe(true)
      expect(isSuperAdmin({ roles: [{ name: 'ADMIN' }] })).toBe(true)
    })

    it('does NOT match false positives like SUB_ADMIN or CHAT_ADMIN', () => {
      expect(isSuperAdmin({ role: 'SUB_ADMIN' })).toBe(false)
      expect(isSuperAdmin({ role: 'CHAT_ADMIN' })).toBe(false)
      expect(isSuperAdmin({ role: 'ADMIN_ASSISTANT' })).toBe(false)
      expect(isSuperAdmin({ roles: ['SUB_ADMIN'] })).toBe(false)
      expect(isSuperAdmin({ roles: [{ name: 'CHAT_ADMIN' }] })).toBe(false)
      expect(isSuperAdmin({ role: 'USER' })).toBe(false)
      expect(isSuperAdmin(null)).toBe(false)
    })
  })

  describe('getUserPermissionSet', () => {
    it('handles arrays, sets, and falsy inputs', () => {
      const set1 = getUserPermissionSet({ permissions: ['P1', 'P2'] })
      expect(set1).toBeInstanceOf(Set)
      expect(set1.has('P1')).toBe(true)

      const existingSet = new Set(['P3'])
      expect(getUserPermissionSet({ permissions: existingSet })).toBe(existingSet)

      expect(getUserPermissionSet(null).size).toBe(0)
      expect(getUserPermissionSet({}).size).toBe(0)
    })
  })

  describe('hasAnyPermission', () => {
    it('returns false if no user', () => {
      expect(hasAnyPermission(null, ['P1'])).toBe(false)
    })

    it('returns true for empty or non-array permissions list', () => {
      expect(hasAnyPermission({ role: 'USER' }, [])).toBe(true)
      expect(hasAnyPermission({ role: 'USER' }, null)).toBe(true)
    })

    it('returns true if superadmin regardless of permission list', () => {
      expect(hasAnyPermission({ role: 'ADMIN' }, ['NON_EXISTENT'])).toBe(true)
    })

    it('evaluates OR logic correctly for normal users', () => {
      const user = { role: 'USER', permissions: ['ADMIN_VIEW_USERS'] }
      expect(hasAnyPermission(user, ['ADMIN_VIEW_USERS', 'ADMIN_CREATE'])).toBe(true)
      expect(hasAnyPermission(user, ['ADMIN_DELETE_USER', 'ADMIN_CREATE'])).toBe(false)
    })
  })

  describe('hasAllPermissions', () => {
    it('returns false if no user', () => {
      expect(hasAllPermissions(null, ['P1'])).toBe(false)
    })

    it('returns true for empty or non-array permissions list', () => {
      expect(hasAllPermissions({ role: 'USER' }, [])).toBe(true)
      expect(hasAllPermissions({ role: 'USER' }, null)).toBe(true)
    })

    it('returns true if superadmin regardless of permission list', () => {
      expect(hasAllPermissions({ role: 'ADMIN' }, ['NON_EXISTENT_1', 'NON_EXISTENT_2'])).toBe(true)
    })

    it('evaluates AND logic correctly for normal users', () => {
      const user = {
        role: 'USER',
        permissions: ['ADMIN_VIEW_USERS', 'ADMIN_VIEW_USER_DETAIL'],
      }
      expect(hasAllPermissions(user, ['ADMIN_VIEW_USERS'])).toBe(true)
      expect(hasAllPermissions(user, ['ADMIN_VIEW_USERS', 'ADMIN_VIEW_USER_DETAIL'])).toBe(true)
      expect(hasAllPermissions(user, ['ADMIN_VIEW_USERS', 'ADMIN_CREATE'])).toBe(false)
    })
  })

  describe('canAssignRole privilege escalation guard', () => {
    it('returns false if user or targetRole is missing', () => {
      expect(canAssignRole(null, { permissions: [] })).toBe(false)
      expect(canAssignRole({ role: 'USER' }, null)).toBe(false)
    })

    it('always allows superAdmin to assign any role', () => {
      const admin = { role: 'ADMIN' }
      const anyRole = { permissions: ['ADMIN_DELETE_USER', 'ROLE_DELETE'] }
      expect(canAssignRole(admin, anyRole)).toBe(true)
    })

    it('allows normal user to assign role if they possess all role permissions', () => {
      const manager = {
        role: 'MANAGER',
        permissions: ['ADMIN_VIEW_USERS', 'ADMIN_VIEW_USER_DETAIL'],
      }
      const roleSub = {
        name: 'VIEWER',
        permissions: ['ADMIN_VIEW_USERS'],
      }
      expect(canAssignRole(manager, roleSub)).toBe(true)

      const roleObjectPerms = {
        name: 'VIEWER_DETAIL',
        permissions: [{ name: 'ADMIN_VIEW_USERS' }, { name: 'ADMIN_VIEW_USER_DETAIL' }],
      }
      expect(canAssignRole(manager, roleObjectPerms)).toBe(true)
    })

    it('prevents privilege escalation when target role contains permissions the user lacks', () => {
      const manager = {
        role: 'MANAGER',
        permissions: ['ADMIN_VIEW_USERS'],
      }
      const elevatedRole = {
        name: 'ADMIN_ROLE',
        permissions: ['ADMIN_VIEW_USERS', 'ADMIN_DELETE_USER'],
      }
      expect(canAssignRole(manager, elevatedRole)).toBe(false)
    })
  })
})
