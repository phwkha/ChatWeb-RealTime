import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as apiClient from '../apiClient.js'
import { accountApi } from '../accountApi.js'

describe('accountApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('calls getProfile', async () => {
    const mockRes = { code: 200, data: { username: 'testuser' } }
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue(mockRes)

    const res = await accountApi.getProfile()
    expect(spy).toHaveBeenCalledWith('/api/users/profile')
    expect(res).toEqual(mockRes)
  })

  it('calls updateProfile with body', async () => {
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200 })
    const body = { firstName: 'John', lastName: 'Doe' }

    await accountApi.updateProfile(body)
    expect(spy).toHaveBeenCalledWith('/api/users/profile', { method: 'PUT', body })
  })

  it('calls updateLanguage', async () => {
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200 })

    await accountApi.updateLanguage('vi')
    expect(spy).toHaveBeenCalledWith('/api/users/language', { method: 'PATCH', body: { language: 'vi' } })
  })

  it('calls updateAvatar with FormData', async () => {
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200 })
    const fakeFile = new File(['avatar content'], 'avatar.png', { type: 'image/png' })

    await accountApi.updateAvatar(fakeFile)
    expect(spy).toHaveBeenCalledWith('/api/users/avatar', expect.objectContaining({ method: 'PATCH' }))
  })

  it('calls changePassword and deleteAccount', async () => {
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200 })
    const pwdBody = { oldPassword: 'old', newPassword: 'new' }

    await accountApi.changePassword(pwdBody)
    expect(spy).toHaveBeenCalledWith('/api/users/change-password', { method: 'POST', body: pwdBody })

    await accountApi.deleteAccount()
    expect(spy).toHaveBeenCalledWith('/api/users/me', { method: 'DELETE' })
  })

  it('calls address endpoints', async () => {
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200 })

    await accountApi.getAddresses()
    expect(spy).toHaveBeenCalledWith('/api/users/addresses')

    await accountApi.getAddress('addr/1')
    expect(spy).toHaveBeenCalledWith('/api/users/address/addr%2F1')

    const newAddr = { street: '123 Main St' }
    await accountApi.addAddress(newAddr)
    expect(spy).toHaveBeenCalledWith('/api/users/address', { method: 'POST', body: newAddr })

    await accountApi.updateAddress('addr/1', newAddr)
    expect(spy).toHaveBeenCalledWith('/api/users/address/addr%2F1', { method: 'PUT', body: newAddr })

    await accountApi.deleteAddress('addr/1')
    expect(spy).toHaveBeenCalledWith('/api/users/address/addr%2F1', { method: 'DELETE' })
  })

  it('calls verification and logout endpoints', async () => {
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200 })

    await accountApi.initiateEmailChange({ newEmail: 'test@example.com' })
    expect(spy).toHaveBeenCalledWith('/api/users/initiate-email-change', { method: 'POST', body: { newEmail: 'test@example.com' } })

    await accountApi.verifyEmailChange({ otp: '123456' })
    expect(spy).toHaveBeenCalledWith('/api/users/verify-email-change', { method: 'POST', body: { otp: '123456' } })

    await accountApi.resendEmailVerification()
    expect(spy).toHaveBeenCalledWith('/api/users/resend-email-verification', { method: 'POST' })

    await accountApi.logoutAllDevices()
    expect(spy).toHaveBeenCalledWith('/api/auth/logout-all-devices', { method: 'POST', skipRefresh: true })
  })
})
