import { useCallback, useMemo } from 'react'
import { useAuth } from '../context/auth-context.js'
import {
  PERMISSIONS,
  canAssignRole as checkCanAssignRole,
  isRootAdmin as checkIsRootAdmin,
  isSystemRole,
} from '../services/authorization.js'

export function usePermission() {
  const {
    user,
    permissions,
    can,
    canAny,
    canAll,
    isSuperAdmin,
    hasAdminAccess,
  } = useAuth()

  const isRootAdmin = useMemo(() => checkIsRootAdmin(user), [user])

  const canAssignRole = useCallback(
    (role) => checkCanAssignRole(user, role),
    [user]
  )

  const canEditRole = useCallback(
    (role) => Boolean(role) && !isSystemRole(role) && Boolean(can?.(PERMISSIONS.ROLE_UPDATE)),
    [can]
  )

  const canDeleteRole = useCallback(
    (role) => Boolean(role) && !isSystemRole(role) && Boolean(can?.(PERMISSIONS.ROLE_DELETE)),
    [can]
  )

  return {
    user,
    permissions,
    can: can || (() => false),
    canAny: canAny || (() => false),
    canAll: canAll || (() => false),
    isSuperAdmin: Boolean(isSuperAdmin),
    hasAdminAccess: Boolean(hasAdminAccess),
    isRootAdmin,
    canAssignRole,
    canEditRole,
    canDeleteRole,
    PERMISSIONS,
  }
}

export default usePermission
