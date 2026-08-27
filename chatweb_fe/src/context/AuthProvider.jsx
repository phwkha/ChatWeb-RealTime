import { useCallback, useEffect, useMemo, useState } from 'react'
import { apiRequest } from '../services/apiClient.js'
import { AuthContext } from './auth-context.js'

function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [isInitializing, setIsInitializing] = useState(true)

  const refreshUser = useCallback(async () => {
    try {
      const response = await apiRequest('/api/users/me')
      const currentUser = response?.data || null
      setUser(currentUser)
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
      setUser(null)
    }
  }, [])

  const value = useMemo(() => ({
    user,
    isAuthenticated: Boolean(user),
    isInitializing,
    login,
    logout,
    refreshUser,
    register,
    resendOtp,
    verifyAccount,
  }), [isInitializing, login, logout, refreshUser, register, resendOtp, user, verifyAccount])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export default AuthProvider
