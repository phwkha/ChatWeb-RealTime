import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as apiClient from '../apiClient.js'
import { adminApi } from '../adminApi.js'

describe('adminApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('calls getUsers and getOnlineUsers', async () => {
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200, data: [] })

    await adminApi.getUsers({ role: 'ADMIN', page: 0, size: 10 })
    expect(spy).toHaveBeenCalledWith('/api/admin/users?role=ADMIN&page=0&size=10')

    await adminApi.getOnlineUsers(1, 15)
    expect(spy).toHaveBeenCalledWith('/api/admin/users/online?page=1&size=15')
  })

  it('calls user administration endpoints with encoded username', async () => {
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200 })

    await adminApi.getUser('alice/admin')
    expect(spy).toHaveBeenCalledWith('/api/admin/users/alice%2Fadmin')

    const createBody = { username: 'newuser', email: 'new@example.com' }
    await adminApi.createUser(createBody)
    expect(spy).toHaveBeenCalledWith('/api/admin/users', { method: 'POST', body: createBody })

    const updateBody = { email: 'updated@example.com' }
    await adminApi.updateUser('alice/admin', updateBody)
    expect(spy).toHaveBeenCalledWith('/api/admin/users/alice%2Fadmin', { method: 'PUT', body: updateBody })

    await adminApi.deleteUser('alice/admin')
    expect(spy).toHaveBeenCalledWith('/api/admin/users/alice%2Fadmin', { method: 'DELETE' })

    await adminApi.lockUser('alice/admin')
    expect(spy).toHaveBeenCalledWith('/api/admin/users/alice%2Fadmin/lock', { method: 'POST' })

    await adminApi.unlockUser('alice/admin')
    expect(spy).toHaveBeenCalledWith('/api/admin/users/alice%2Fadmin/unlock', { method: 'POST' })

    await adminApi.deleteAvatar('alice/admin')
    expect(spy).toHaveBeenCalledWith('/api/admin/users/alice%2Fadmin/avatar', { method: 'DELETE' })
  })

  it('calls role and permission endpoints', async () => {
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200 })

    await adminApi.getRoles()
    expect(spy).toHaveBeenCalledWith('/api/admin/roles')

    await adminApi.getPermissions()
    expect(spy).toHaveBeenCalledWith('/api/admin/roles/permissions')

    const roleBody = { name: 'ROLE_MODERATOR', permissions: ['READ_REPORTS'] }
    await adminApi.createRole(roleBody)
    expect(spy).toHaveBeenCalledWith('/api/admin/roles', { method: 'POST', body: roleBody })

    await adminApi.updateRole('r-123', roleBody)
    expect(spy).toHaveBeenCalledWith('/api/admin/roles/r-123', { method: 'PUT', body: roleBody })

    await adminApi.deleteRole('r-123')
    expect(spy).toHaveBeenCalledWith('/api/admin/roles/r-123', { method: 'DELETE' })
  })

  it('calls email, system messages, and reports', async () => {
    const spy = vi.spyOn(apiClient, 'apiRequest').mockResolvedValue({ code: 200 })

    const emailBody = { to: 'user@test.com', subject: 'Notice', content: 'Hello' }
    await adminApi.sendEmail(emailBody)
    expect(spy).toHaveBeenCalledWith('/api/admin/emails/send', { method: 'POST', body: emailBody })

    await adminApi.getSystemMessages('cur-99', 10)
    expect(spy).toHaveBeenCalledWith('/api/messages/system?size=10&cursor=cur-99')

    await adminApi.getReports({ status: 'PENDING' })
    expect(spy).toHaveBeenCalledWith('/api/admin/reports?status=PENDING')

    await adminApi.getReportStatistics()
    expect(spy).toHaveBeenCalledWith('/api/admin/reports/statistics')

    await adminApi.getReport('rep-1')
    expect(spy).toHaveBeenCalledWith('/api/admin/reports/rep-1')

    await adminApi.resolveReport('rep-1', { status: 'RESOLVED' })
    expect(spy).toHaveBeenCalledWith('/api/admin/reports/rep-1/resolve', { method: 'PUT', body: { status: 'RESOLVED' } })

    await adminApi.deleteReport('rep-1')
    expect(spy).toHaveBeenCalledWith('/api/admin/reports/rep-1', { method: 'DELETE' })
  })
})
