import { describe, it, expect } from 'vitest'
import {
  PERMISSIONS,
  SYSTEM_ROLES,
  ADMIN_CONSOLE_PERMISSIONS,
  ROOT_ADMIN_USERNAME,
  isRootAdmin,
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
  USER_MANAGEMENT_PERMISSIONS,
  USER_DRAWER_ACTION_PERMISSIONS,
  REPORT_MODERATION_PERMISSIONS,
  ROLE_MANAGEMENT_PERMISSIONS,
  PERMISSION_PREREQUISITES,
  resolvePermissionIdsWithPrerequisites,
} from '../authorization.js'

describe('authorization service', () => {
  // Pre-existing tests
  it('identifies admin user by role string', () => {
    expect(isAdminUser({ role: 'ROLE_ADMIN' })).toBe(true)
    expect(isAdminUser({ role: 'ADMIN' })).toBe(true)
    expect(isAdminUser({ role: 'USER' })).toBe(false)
    expect(isAdminUser(null)).toBe(false)
  })

  it('identifies admin user strictly by permissions starting with ADMIN_', () => {
    expect(isAdminUser({ permissions: ['ADMIN_VIEW_USERS'] })).toBe(true)
    expect(isAdminUser({ permissions: ['ADMIN_ROLE_VIEW_ALL'] })).toBe(true)
    expect(isAdminUser({ permissions: ['ADMIN_SEND_EMAIL'] })).toBe(true)
    expect(isAdminUser({ permissions: ['ADMIN_SEND-MESSAGE'] })).toBe(true)
    expect(isAdminUser({ permissions: [{ name: 'ADMIN_CREATE' }] })).toBe(true)
    expect(isAdminUser({ permissions: ['USER_SEND_WORLD_CHAT'] })).toBe(false)
    expect(isAdminUser({ permissions: ['ROLE_MODERATOR'] })).toBe(false)
    expect(isAdminUser({ permissions: ['SEND_EMAIL'] })).toBe(false)
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
    it('defines frozen permission objects and ADMIN_CONSOLE_PERMISSIONS with all 22 ADMIN_ permissions', () => {
      expect(Object.isFrozen(PERMISSIONS)).toBe(true)
      expect(Object.isFrozen(SYSTEM_ROLES)).toBe(true)
      expect(Object.isFrozen(ADMIN_CONSOLE_PERMISSIONS)).toBe(true)
      expect(ADMIN_CONSOLE_PERMISSIONS).toHaveLength(22)
      ADMIN_CONSOLE_PERMISSIONS.forEach((perm) => {
        expect(perm.startsWith('ADMIN_')).toBe(true)
      })
    })

    it('contains specific hyphenated ADMIN_SEND-MESSAGE', () => {
      expect(PERMISSIONS.ADMIN_SEND_MESSAGE).toBe('ADMIN_SEND-MESSAGE')
    })

    it('contains all required permissions with ADMIN_ prefix and aliases', () => {
      const expectedAdminPerms = [
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
        'ADMIN_ROLE_VIEW_ALL',
        'ADMIN_ROLE_VIEW_ALL_PERMISSION',
        'ADMIN_ROLE_ADD',
        'ADMIN_ROLE_UPDATE',
        'ADMIN_ROLE_DELETE',
        'ADMIN_SEND_EMAIL',
        'ADMIN_SEND-MESSAGE',
        'ADMIN_VIEW_REPORTS',
        'ADMIN_RESOLVE_REPORTS',
        'ADMIN_DELETE_REPORTS',
      ]
      expectedAdminPerms.forEach((perm) => {
        expect(Object.values(PERMISSIONS)).toContain(perm)
      })
      expect(PERMISSIONS.USER_SEND_WORLD_CHAT).toBe('USER_SEND_WORLD_CHAT')
      expect(PERMISSIONS.ROLE_VIEW_ALL).toBe('ADMIN_ROLE_VIEW_ALL')
      expect(PERMISSIONS.SEND_EMAIL).toBe('ADMIN_SEND_EMAIL')
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

    it('enforces explicit permissions even if user has ADMIN role', () => {
      const scopedAdmin = {
        role: 'ADMIN',
        permissions: ['ADMIN_VIEW_USERS'],
      }
      const roleNeedingDelete = {
        name: 'DELETER',
        permissions: ['ADMIN_DELETE_USER'],
      }
      expect(canAssignRole(scopedAdmin, roleNeedingDelete)).toBe(false)
    })
  })

  describe('ROOT_ADMIN_USERNAME and isRootAdmin', () => {
    it('defines ROOT_ADMIN_USERNAME as admin', () => {
      expect(ROOT_ADMIN_USERNAME).toBe('admin')
    })

    it('correctly identifies root admin from username string or user object case-insensitively', () => {
      expect(isRootAdmin('admin')).toBe(true)
      expect(isRootAdmin('ADMIN')).toBe(true)
      expect(isRootAdmin('  admin  ')).toBe(true)
      expect(isRootAdmin({ username: 'admin' })).toBe(true)
      expect(isRootAdmin({ username: 'ADMIN' })).toBe(true)
      expect(isRootAdmin({ username: '  admin  ' })).toBe(true)
    })

    it('returns false for other usernames, null, or undefined', () => {
      expect(isRootAdmin('superadmin')).toBe(false)
      expect(isRootAdmin('root')).toBe(false)
      expect(isRootAdmin({ username: 'moderator' })).toBe(false)
      expect(isRootAdmin({ username: '' })).toBe(false)
      expect(isRootAdmin(null)).toBe(false)
      expect(isRootAdmin(undefined)).toBe(false)
      expect(isRootAdmin({})).toBe(false)
    })
  })

  describe('explicit permissions behavior vs superAdmin fallback', () => {
    it('restricts user to explicit permissions even if user has ADMIN role', () => {
      const scopedAdmin = {
        role: 'ADMIN',
        permissions: ['ADMIN_VIEW_USERS'],
      }
      expect(hasPermission(scopedAdmin, 'ADMIN_VIEW_USERS')).toBe(true)
      expect(hasPermission(scopedAdmin, 'ADMIN_DELETE_USER')).toBe(false)
      expect(hasAnyPermission(scopedAdmin, ['ADMIN_VIEW_USERS', 'ADMIN_DELETE_USER'])).toBe(true)
      expect(hasAllPermissions(scopedAdmin, ['ADMIN_VIEW_USERS', 'ADMIN_DELETE_USER'])).toBe(false)
    })

    it('falls back to superAdmin bypass when user has no explicit permissions', () => {
      const legacyAdmin1 = { role: 'ADMIN' }
      const legacyAdmin2 = { role: 'ADMIN', permissions: [] }
      const legacyAdmin3 = { role: 'ADMIN', permissions: null }

      expect(hasPermission(legacyAdmin1, 'ADMIN_DELETE_USER')).toBe(true)
      expect(hasPermission(legacyAdmin2, 'ADMIN_DELETE_USER')).toBe(true)
      expect(hasPermission(legacyAdmin3, 'ADMIN_DELETE_USER')).toBe(true)

      expect(hasAllPermissions(legacyAdmin1, ['ADMIN_DELETE_USER', 'ADMIN_VIEW_USERS'])).toBe(true)
      expect(canAssignRole(legacyAdmin1, { permissions: ['ADMIN_DELETE_USER'] })).toBe(true)
    })
  })

  describe('permission groups and prerequisites', () => {
    it('exports frozen permission groups with correct members', () => {
      expect(Object.isFrozen(USER_MANAGEMENT_PERMISSIONS)).toBe(true)
      expect(USER_MANAGEMENT_PERMISSIONS).toHaveLength(12)
      expect(USER_MANAGEMENT_PERMISSIONS).toContain(PERMISSIONS.ADMIN_VIEW_USERS)
      expect(USER_MANAGEMENT_PERMISSIONS).toContain(PERMISSIONS.ADMIN_LOCK)
      expect(USER_MANAGEMENT_PERMISSIONS).toContain(PERMISSIONS.ADMIN_DELETE_AVATAR)

      expect(Object.isFrozen(USER_DRAWER_ACTION_PERMISSIONS)).toBe(true)
      expect(USER_DRAWER_ACTION_PERMISSIONS).toHaveLength(9)
      expect(USER_DRAWER_ACTION_PERMISSIONS).toContain(PERMISSIONS.ADMIN_VIEW_USER_DETAIL)
      expect(USER_DRAWER_ACTION_PERMISSIONS).toContain(PERMISSIONS.ADMIN_UNLOCK)

      expect(Object.isFrozen(REPORT_MODERATION_PERMISSIONS)).toBe(true)
      expect(REPORT_MODERATION_PERMISSIONS).toHaveLength(3)
      expect(REPORT_MODERATION_PERMISSIONS).toEqual([
        PERMISSIONS.ADMIN_VIEW_REPORTS,
        PERMISSIONS.ADMIN_RESOLVE_REPORTS,
        PERMISSIONS.ADMIN_DELETE_REPORTS,
      ])

      expect(Object.isFrozen(ROLE_MANAGEMENT_PERMISSIONS)).toBe(true)
      expect(ROLE_MANAGEMENT_PERMISSIONS).toHaveLength(5)
      expect(ROLE_MANAGEMENT_PERMISSIONS).toContain(PERMISSIONS.ADMIN_ROLE_VIEW_ALL)
      expect(ROLE_MANAGEMENT_PERMISSIONS).toContain(PERMISSIONS.ADMIN_ROLE_ADD)
    })

    it('exports PERMISSION_PREREQUISITES mapping properly', () => {
      expect(Object.isFrozen(PERMISSION_PREREQUISITES)).toBe(true)
      expect(PERMISSION_PREREQUISITES[PERMISSIONS.ADMIN_LOCK]).toEqual([
        PERMISSIONS.ADMIN_VIEW_USERS,
        PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
      ])
      expect(PERMISSION_PREREQUISITES[PERMISSIONS.ADMIN_RESOLVE_REPORTS]).toEqual([
        PERMISSIONS.ADMIN_VIEW_REPORTS,
      ])
    })

    describe('resolvePermissionIdsWithPrerequisites', () => {
      const mockAvailablePermissions = [
        { id: 1, name: 'ADMIN_VIEW_USERS' },
        { id: 2, name: 'ADMIN_VIEW_USER_DETAIL' },
        { id: 3, name: 'ADMIN_LOCK' },
        { id: 4, name: 'ADMIN_VIEW_USER_ADDRESSES' },
        { id: 5, name: 'ADMIN_UPDATE_USER_ADDRESS' },
        { id: 6, name: 'ADMIN_VIEW_REPORTS' },
        { id: 7, name: 'ADMIN_RESOLVE_REPORTS' },
        { id: 8, name: 'ADMIN_ROLE_VIEW_ALL' },
        { id: 9, name: 'ADMIN_ROLE_VIEW_ALL_PERMISSION' },
        { id: 10, name: 'ADMIN_ROLE_ADD' },
      ]

      it('adds ADMIN_VIEW_USERS and ADMIN_VIEW_USER_DETAIL when adding ADMIN_LOCK', () => {
        const result = resolvePermissionIdsWithPrerequisites([], 3, mockAvailablePermissions)
        expect(result).toContain(3)
        expect(result).toContain(1)
        expect(result).toContain(2)
      })

      it('transitively adds all prerequisites when adding ADMIN_UPDATE_USER_ADDRESS', () => {
        const result = resolvePermissionIdsWithPrerequisites([], 5, mockAvailablePermissions)
        expect(result).toContain(5)
        expect(result).toContain(4)
        expect(result).toContain(2)
        expect(result).toContain(1)
      })

      it('adds ADMIN_VIEW_REPORTS when adding ADMIN_RESOLVE_REPORTS', () => {
        const result = resolvePermissionIdsWithPrerequisites([99], 7, mockAvailablePermissions)
        expect(result).toContain(99)
        expect(result).toContain(7)
        expect(result).toContain(6)
      })

      it('adds ADMIN_ROLE_VIEW_ALL and ADMIN_ROLE_VIEW_ALL_PERMISSION when adding ADMIN_ROLE_ADD', () => {
        const result = resolvePermissionIdsWithPrerequisites([], 10, mockAvailablePermissions)
        expect(result).toContain(10)
        expect(result).toContain(8)
        expect(result).toContain(9)
      })

      it('handles null or undefined addedPermissionId safely', () => {
        expect(resolvePermissionIdsWithPrerequisites([1, 2], null, mockAvailablePermissions)).toEqual([1, 2])
        expect(resolvePermissionIdsWithPrerequisites([1, 2], undefined, mockAvailablePermissions)).toEqual([1, 2])
        expect(resolvePermissionIdsWithPrerequisites(undefined, 3, mockAvailablePermissions)).toContain(3)
      })

      it('resolves when permissions use backward-compatibility aliases', () => {
        const aliasedPerms = [
          { id: 101, name: 'ROLE_VIEW_ALL' },
          { id: 102, name: 'ROLE_VIEW_ALL_PERMISSION' },
          { id: 103, name: 'ROLE_ADD' },
        ]
        const result = resolvePermissionIdsWithPrerequisites([], 103, aliasedPerms)
        expect(result).toContain(103)
        expect(result).toContain(101)
        expect(result).toContain(102)
      })
    })
  })
})
