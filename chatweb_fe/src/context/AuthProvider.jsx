import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { apiRequest, setAccessToken, setSessionExpiredHandler } from '../services/apiClient.js'
import { isAdminUser, isSuperAdmin } from '../services/authorization.js'
import { AuthContext } from './auth-context.js'

function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [isInitializing, setIsInitializing] = useState(true)
  const [sessionExpiredMessage, setSessionExpiredMessage] = useState('')
  const lastPermissionDeniedSyncRef = useRef(0)

  const refreshUser = useCallback(async () => {
    try {
      await apiRequest('/api/auth/refresh-token', { method: 'POST', skipRefresh: true })
      const response = await apiRequest('/api/users/me')
      const currentUser = response?.data || null
      setUser(currentUser)
      return currentUser
    } catch {
      setAccessToken(null)
      setUser(null)
      return null
    }
  }, [])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect -- session hydration is an external API synchronization
    refreshUser().finally(() => setIsInitializing(false))
  }, [refreshUser])

  useEffect(() => {
    const handleSessionExpired = (event) => {
      const msg = event?.detail?.message || 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
      setSessionExpiredMessage(msg)
      setAccessToken(null)
      setUser(null)
    }
    if (typeof setSessionExpiredHandler === 'function') {
      setSessionExpiredHandler(handleSessionExpired)
    }
    window.addEventListener('chatweb:session-expired', handleSessionExpired)
    return () => {
      if (typeof setSessionExpiredHandler === 'function') {
        setSessionExpiredHandler(null)
      }
      window.removeEventListener('chatweb:session-expired', handleSessionExpired)
    }
  }, [])

  useEffect(() => {
    const handlePermissionDenied = () => {
      const now = Date.now()
      if (now - lastPermissionDeniedSyncRef.current < 5000) return
      lastPermissionDeniedSyncRef.current = now
      refreshUser()
    }

    window.addEventListener('chatweb:permission-denied', handlePermissionDenied)
    return () => {
      window.removeEventListener('chatweb:permission-denied', handlePermissionDenied)
    }
  }, [refreshUser])

  const login = useCallback(async (credentials) => {
    const response = await apiRequest('/api/auth/login', {
      method: 'POST',
      body: credentials,
      skipRefresh: true,
    })
    setSessionExpiredMessage('')
    setUser(response?.data || null)
    return response
  }, [])

  const register = useCallback((registrationData) => apiRequest('/api/auth/register', {
    method: 'POST',
    body: registrationData,
    skipRefresh: true,
  }), [])

  const verifyAccount = useCallback((verificationData) => apiRequest('/api/auth/verify-account', {
    method: 'POST',
    body: verificationData,
    skipRefresh: true,
  }), [])

  const resendOtp = useCallback((email) => apiRequest(`/api/auth/resend-otp?email=${encodeURIComponent(email)}`, {
    method: 'POST',
    skipRefresh: true,
  }), [])

  const logout = useCallback(async () => {
    try {
      await apiRequest('/api/auth/logout', { method: 'POST', skipRefresh: true })
    } finally {
      setAccessToken(null)
      setUser(null)
    }
  }, [])

  const logoutEverywhere = useCallback(async () => {
    try {
      return await apiRequest('/api/auth/logout-all-devices', { method: 'POST', skipRefresh: true })
    } finally {
      setAccessToken(null)
      setUser(null)
    }
  }, [])

  const deleteAccount = useCallback(async () => {
    const response = await apiRequest('/api/users/me', { method: 'DELETE' })
    setAccessToken(null)
    setUser(null)
    return response
  }, [])

  const clearSessionExpiredMessage = useCallback(() => {
    setSessionExpiredMessage('')
  }, [])

  const permissionSet = useMemo(
    () => new Set(Array.isArray(user?.permissions) ? user.permissions : []),
    [user?.permissions]
  )

  const superAdmin = useMemo(() => isSuperAdmin(user), [user])
  const hasAdminAccess = useMemo(() => isAdminUser(user), [user])

  const can = useCallback(
    (permission) => {
      if (!permission) return true
      if (!user) return false
      return superAdmin || permissionSet.has(permission)
    },
    [permissionSet, superAdmin, user]
  )

  const canAny = useCallback(
    (permissions = []) => {
      if (!Array.isArray(permissions) || permissions.length === 0) return true
      if (!user) return false
      return superAdmin || permissions.some((p) => permissionSet.has(p))
    },
    [permissionSet, superAdmin, user]
  )

  const canAll = useCallback(
    (permissions = []) => {
      if (!Array.isArray(permissions) || permissions.length === 0) return true
      if (!user) return false
      return superAdmin || permissions.every((p) => permissionSet.has(p))
    },
    [permissionSet, superAdmin, user]
  )

  const value = useMemo(() => ({
    user,
    isAuthenticated: Boolean(user),
    isInitializing,
    sessionExpiredMessage,
    clearSessionExpiredMessage,
    login,
    logout,
    logoutEverywhere,
    deleteAccount,
    refreshUser,
    register,
    resendOtp,
    verifyAccount,
    permissions: permissionSet,
    can,
    canAny,
    canAll,
    isSuperAdmin: superAdmin,
    hasAdminAccess,
  }), [
    clearSessionExpiredMessage,
    deleteAccount,
    isInitializing,
    login,
    logout,
    logoutEverywhere,
    refreshUser,
    register,
    resendOtp,
    sessionExpiredMessage,
    user,
    verifyAccount,
    permissionSet,
    can,
    canAny,
    canAll,
    superAdmin,
    hasAdminAccess,
  ])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export default AuthProvider
