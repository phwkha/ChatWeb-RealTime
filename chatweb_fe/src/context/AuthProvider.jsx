import { useCallback, useEffect, useMemo, useState } from 'react'
import { apiRequest } from '../services/apiClient.js'
import { clearAccessToken } from '../services/tokenStore.js'
import { initializeEncryption } from '../services/cryptoService.js'
import { AuthContext } from './auth-context.js'

function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [isInitializing, setIsInitializing] = useState(true)

  const refreshUser = useCallback(async () => {
    try {
      const response = await apiRequest('/api/users/me')
      const currentUser = response?.data || null
      setUser(currentUser)
      if (currentUser?.username) void initializeEncryption(currentUser.username).catch(() => {})
      return currentUser
    } catch {
      setUser(null)
      return null
    }
  }, [])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect -- session hydration is an external API synchronization
    refreshUser().finally(() => setIsInitializing(false))
  }, [refreshUser])

  const login = useCallback(async (credentials) => {
    const response = await apiRequest('/api/auth/login', {
      method: 'POST',
      body: credentials,
      skipRefresh: true,
    })
    setUser(response?.data || null)
    if (response?.data?.username) void initializeEncryption(response.data.username).catch(() => {})
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
      clearAccessToken()
      setUser(null)
    }
  }, [])

  const logoutEverywhere = useCallback(async () => {
    try {
      return await apiRequest('/api/auth/logout-all-devices', { method: 'POST', skipRefresh: true })
    } finally {
      clearAccessToken()
      setUser(null)
    }
  }, [])

  const deleteAccount = useCallback(async () => {
    const response = await apiRequest('/api/users/me', { method: 'DELETE' })
    clearAccessToken()
    setUser(null)
    return response
  }, [])

  const value = useMemo(() => ({
    user,
    isAuthenticated: Boolean(user),
    isInitializing,
    login,
    logout,
    logoutEverywhere,
    deleteAccount,
    refreshUser,
    register,
    resendOtp,
    verifyAccount,
  }), [deleteAccount, isInitializing, login, logout, logoutEverywhere, refreshUser, register, resendOtp, user, verifyAccount])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export default AuthProvider
