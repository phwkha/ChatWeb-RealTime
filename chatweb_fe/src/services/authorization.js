const ADMIN_PERMISSION_PATTERN = /^(ADMIN[_-]|ROLE_|SEND_EMAIL)/

export function isAdminUser(user) {
  return String(user?.role || '').toUpperCase().includes('ADMIN')
    || (user?.permissions || []).some((permission) => ADMIN_PERMISSION_PATTERN.test(permission))
}

export function hasPermission(user, permission) {
  return String(user?.role || '').toUpperCase().includes('ADMIN')
    || (user?.permissions || []).includes(permission)
}
