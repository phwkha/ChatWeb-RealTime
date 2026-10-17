export const PERMISSIONS = Object.freeze({
  // User Administration
  ADMIN_VIEW_USERS: 'ADMIN_VIEW_USERS',
  ADMIN_VIEW_ONLINE_USERS: 'ADMIN_VIEW_ONLINE_USERS',
  ADMIN_VIEW_USER_DETAIL: 'ADMIN_VIEW_USER_DETAIL',
  ADMIN_VIEW_USER_ADDRESSES: 'ADMIN_VIEW_USER_ADDRESSES',
  ADMIN_CREATE: 'ADMIN_CREATE',
  ADMIN_UPDATE_USER: 'ADMIN_UPDATE_USER',
  ADMIN_UPDATE_USER_ADDRESS: 'ADMIN_UPDATE_USER_ADDRESS',
  ADMIN_DELETE_USER: 'ADMIN_DELETE_USER',
  ADMIN_DELETE_USER_ADDRESS: 'ADMIN_DELETE_USER_ADDRESS',
  ADMIN_LOCK: 'ADMIN_LOCK',
  ADMIN_UNLOCK: 'ADMIN_UNLOCK',
  ADMIN_DELETE_AVATAR: 'ADMIN_DELETE_AVATAR',

  // Role & Permission Management
  ROLE_VIEW_ALL: 'ROLE_VIEW_ALL',
  ROLE_VIEW_ALL_PERMISSION: 'ROLE_VIEW_ALL_PERMISSION',
  ROLE_ADD: 'ROLE_ADD',
  ROLE_UPDATE: 'ROLE_UPDATE',
  ROLE_DELETE: 'ROLE_DELETE',

  // System Communication
  SEND_EMAIL: 'SEND_EMAIL',
  ADMIN_SEND_MESSAGE: 'ADMIN_SEND-MESSAGE',

  // Report Moderation
  ADMIN_VIEW_REPORTS: 'ADMIN_VIEW_REPORTS',
  ADMIN_RESOLVE_REPORTS: 'ADMIN_RESOLVE_REPORTS',
  ADMIN_DELETE_REPORTS: 'ADMIN_DELETE_REPORTS',
})

export const SYSTEM_ROLES = Object.freeze({
  ADMIN: 'ADMIN',
  USER: 'USER',
})

export const ADMIN_CONSOLE_PERMISSIONS = Object.freeze(Object.values(PERMISSIONS))

const ADMIN_PERMISSION_PATTERN = /^(ADMIN[_-]|ROLE_|SEND_EMAIL)/

export function normalizeRoleName(role) {
  if (!role) return ''
  if (typeof role === 'object' && role.name) return String(role.name).trim().toUpperCase()
  return String(role).trim().toUpperCase()
}

export function isSystemRole(roleOrName) {
  const normalized = normalizeRoleName(roleOrName).replace(/^ROLE_/, '')
  return normalized === SYSTEM_ROLES.ADMIN || normalized === SYSTEM_ROLES.USER
}

export function isSuperAdmin(user) {
  if (!user) return false
  const primaryRole = normalizeRoleName(user.role || user.userRole).replace(/^ROLE_/, '')
  if (primaryRole === SYSTEM_ROLES.ADMIN) return true

  const roles = user.roles || user.authorities || []
  return Array.isArray(roles) && roles.some(
    (role) => normalizeRoleName(role).replace(/^ROLE_/, '') === SYSTEM_ROLES.ADMIN
  )
}

export function getUserPermissionSet(user) {
  if (!user || !user.permissions) {
    return new Set()
  }
  if (user.permissions instanceof Set) {
    return user.permissions
  }
  if (Array.isArray(user.permissions)) {
    return new Set(user.permissions)
  }
  return new Set()
}

export function hasPermission(user, permission) {
  if (!user || !permission) return false
  if (isSuperAdmin(user)) return true
  const permissionSet = getUserPermissionSet(user)
  return permissionSet.has(permission)
}

export function hasAnyPermission(user, permissions = []) {
  if (!user) return false
  if (!Array.isArray(permissions) || permissions.length === 0) return true
  if (isSuperAdmin(user)) return true
  const permissionSet = getUserPermissionSet(user)
  return permissions.some((permission) => permissionSet.has(permission))
}

export function hasAllPermissions(user, permissions = []) {
  if (!user) return false
  if (!Array.isArray(permissions) || permissions.length === 0) return true
  if (isSuperAdmin(user)) return true
  const permissionSet = getUserPermissionSet(user)
  return permissions.every((permission) => permissionSet.has(permission))
}

export function isAdminUser(user) {
  if (!user) return false
  if (isSuperAdmin(user)) return true
  const perms = Array.isArray(user.permissions)
    ? user.permissions
    : user.permissions instanceof Set
      ? Array.from(user.permissions)
      : []
  return perms.some((permission) => ADMIN_PERMISSION_PATTERN.test(permission))
}

export function isAdminAccount(person) {
  if (!person) return false
  if (isAdminUser(person)) return true
  const role = normalizeRoleName(person.role || person.userRole).replace(/^ROLE_/, '')
  if (role === SYSTEM_ROLES.ADMIN) return true
  const roles = person.roles || person.authorities || []
  return Array.isArray(roles) && roles.some((r) => normalizeRoleName(r).replace(/^ROLE_/, '') === SYSTEM_ROLES.ADMIN)
}

export function canAssignRole(user, targetRole) {
  if (!user || !targetRole) return false
  if (isSuperAdmin(user)) return true
  const rawPermissions = Array.isArray(targetRole.permissions)
    ? targetRole.permissions
    : targetRole.permissions instanceof Set
      ? Array.from(targetRole.permissions)
      : []
  const rolePermissions = rawPermissions
    .map((p) => (typeof p === 'string' ? p : p?.name))
    .filter(Boolean)
  return hasAllPermissions(user, rolePermissions)
}
