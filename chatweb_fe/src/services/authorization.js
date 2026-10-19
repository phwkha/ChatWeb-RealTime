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
  ADMIN_ROLE_VIEW_ALL: 'ADMIN_ROLE_VIEW_ALL',
  ADMIN_ROLE_VIEW_ALL_PERMISSION: 'ADMIN_ROLE_VIEW_ALL_PERMISSION',
  ADMIN_ROLE_ADD: 'ADMIN_ROLE_ADD',
  ADMIN_ROLE_UPDATE: 'ADMIN_ROLE_UPDATE',
  ADMIN_ROLE_DELETE: 'ADMIN_ROLE_DELETE',

  // Backward-compatibility aliases for Role & Permission Management
  ROLE_VIEW_ALL: 'ADMIN_ROLE_VIEW_ALL',
  ROLE_VIEW_ALL_PERMISSION: 'ADMIN_ROLE_VIEW_ALL_PERMISSION',
  ROLE_ADD: 'ADMIN_ROLE_ADD',
  ROLE_UPDATE: 'ADMIN_ROLE_UPDATE',
  ROLE_DELETE: 'ADMIN_ROLE_DELETE',

  // System Communication
  ADMIN_SEND_EMAIL: 'ADMIN_SEND_EMAIL',
  SEND_EMAIL: 'ADMIN_SEND_EMAIL',
  ADMIN_SEND_MESSAGE: 'ADMIN_SEND-MESSAGE',

  // Report Moderation
  ADMIN_VIEW_REPORTS: 'ADMIN_VIEW_REPORTS',
  ADMIN_RESOLVE_REPORTS: 'ADMIN_RESOLVE_REPORTS',
  ADMIN_DELETE_REPORTS: 'ADMIN_DELETE_REPORTS',

  // User Permissions
  USER_SEND_WORLD_CHAT: 'USER_SEND_WORLD_CHAT',
})

export const SYSTEM_ROLES = Object.freeze({
  ADMIN: 'ADMIN',
  USER: 'USER',
})

export const ROOT_ADMIN_USERNAME = 'admin'

export function isRootAdmin(userOrUsername) {
  if (!userOrUsername) return false
  const username = typeof userOrUsername === 'string'
    ? userOrUsername
    : userOrUsername.username
  return typeof username === 'string' && username.trim().toLowerCase() === ROOT_ADMIN_USERNAME
}

function hasExplicitPermissions(user) {
  if (!user || user.permissions === undefined || user.permissions === null) return false
  if (user.permissions instanceof Set) return user.permissions.size > 0
  return Array.isArray(user.permissions) && user.permissions.length > 0
}

export const ADMIN_CONSOLE_PERMISSIONS = Object.freeze(
  Array.from(
    new Set(
      Object.values(PERMISSIONS).filter((p) => typeof p === 'string' && p.startsWith('ADMIN_'))
    )
  )
)

const ADMIN_PERMISSION_PATTERN = /^ADMIN_/

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
  const raw = Array.isArray(user.permissions) ? user.permissions : []
  const set = new Set()
  for (const p of raw) {
    if (typeof p === 'string') set.add(p)
    else if (p && typeof p.name === 'string') set.add(p.name)
  }
  return set
}

export function hasPermission(user, permission) {
  if (!user || !permission) return false
  const permissionSet = getUserPermissionSet(user)
  if (permissionSet.has(permission)) return true
  const mapped = PERMISSIONS[permission]
  if (mapped && permissionSet.has(mapped)) return true
  for (const [key, val] of Object.entries(PERMISSIONS)) {
    if (val === permission && permissionSet.has(key)) return true
  }
  if (!hasExplicitPermissions(user) && isSuperAdmin(user)) return true
  return false
}

export function hasAnyPermission(user, permissions = []) {
  if (!user) return false
  if (!Array.isArray(permissions) || permissions.length === 0) return true
  return permissions.some((permission) => hasPermission(user, permission))
}

export function hasAllPermissions(user, permissions = []) {
  if (!user) return false
  if (!Array.isArray(permissions) || permissions.length === 0) return true
  return permissions.every((permission) => hasPermission(user, permission))
}

export function isAdminUser(user) {
  if (!user) return false
  if (isSuperAdmin(user)) return true
  const perms = Array.isArray(user.permissions)
    ? user.permissions
    : user.permissions instanceof Set
      ? Array.from(user.permissions)
      : []
  return perms.some((permission) => {
    const permName = typeof permission === 'string' ? permission : permission?.name
    return typeof permName === 'string' && ADMIN_PERMISSION_PATTERN.test(permName)
  })
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
