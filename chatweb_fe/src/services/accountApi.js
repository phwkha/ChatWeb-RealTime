import { apiRequest } from './apiClient.js'

export const accountApi = {
  getProfile: () => apiRequest('/api/users/profile'),
  updateProfile: (body) => apiRequest('/api/users/profile', { method: 'PUT', body }),
  updateAvatar: (file) => {
    const body = new FormData()
    body.append('file', file)
    return apiRequest('/api/users/avatar', { method: 'PATCH', body })
  },
  changePassword: (body) => apiRequest('/api/users/change-password', { method: 'POST', body }),
  deleteAccount: () => apiRequest('/api/users/me', { method: 'DELETE' }),
  getAddresses: () => apiRequest('/api/users/addresses'),
  getAddress: (id) => apiRequest(`/api/users/address/${encodeURIComponent(id)}`),
  addAddress: (body) => apiRequest('/api/users/address', { method: 'POST', body }),
  updateAddress: (id, body) => apiRequest(`/api/users/address/${encodeURIComponent(id)}`, { method: 'PUT', body }),
  deleteAddress: (id) => apiRequest(`/api/users/address/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  initiateEmailChange: (body) => apiRequest('/api/users/initiate-email-change', { method: 'POST', body }),
  verifyEmailChange: (body) => apiRequest('/api/users/verify-email-change', { method: 'POST', body }),
  resendEmailVerification: () => apiRequest('/api/users/resend-email-verification', { method: 'POST' }),
  initiatePhoneChange: (body) => apiRequest('/api/users/initiate-phone-change', { method: 'POST', body }),
  verifyPhoneChange: (body) => apiRequest('/api/users/verify-phone-change', { method: 'POST', body }),
  resendPhoneVerification: () => apiRequest('/api/users/resend-phone-change-verification', { method: 'POST' }),
  logoutAllDevices: () => apiRequest('/api/auth/logout-all-devices', { method: 'POST', skipRefresh: true }),
}
