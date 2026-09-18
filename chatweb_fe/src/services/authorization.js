const ADMIN_PERMISSION_PATTERN = /^(ADMIN[_-]|ROLE_|SEND_EMAIL)/

export function isAdminUser(user) {
  return String(user?.role || '').toUpperCase().includes('ADMIN')
    || (user?.permissions || []).some((permission) => ADMIN_PERMISSION_PATTERN.test(permission))
    || (user?.roles || []).some((role) => String(role).toUpperCase().includes('ADMIN'))
}

export function isAdminAccount(person) {
  if (!person) return false
  if (isAdminUser(person)) return true
  const role = String(person?.role || person?.userRole || '').toUpperCase()
  if (role.includes('ADMIN')) return true
  const roles = person?.roles || person?.authorities || []
  return roles.some((r) => String(r).toUpperCase().includes('ADMIN'))
}

export function hasPermission(user, permission) {
  return isAdminUser(user)
    || (user?.permissions || []).includes(permission)
}
