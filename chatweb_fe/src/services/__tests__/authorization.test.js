import { describe, it, expect } from 'vitest'
import { isAdminUser, isAdminAccount, hasPermission } from '../authorization.js'

describe('authorization service', () => {
  it('identifies admin user by role string', () => {
    expect(isAdminUser({ role: 'ROLE_ADMIN' })).toBe(true)
    expect(isAdminUser({ role: 'ADMIN' })).toBe(true)
    expect(isAdminUser({ role: 'USER' })).toBe(false)
    expect(isAdminUser(null)).toBe(false)
  })

  it('identifies admin user by permissions', () => {
    expect(isAdminUser({ permissions: ['ADMIN_VIEW_USERS'] })).toBe(true)
    expect(isAdminUser({ permissions: ['ROLE_MODERATOR'] })).toBe(true)
    expect(isAdminUser({ permissions: ['SEND_EMAIL'] })).toBe(true)
    expect(isAdminUser({ permissions: ['READ_MESSAGE'] })).toBe(false)
  })

  it('identifies admin account properly', () => {
    expect(isAdminAccount({ role: 'ADMIN' })).toBe(true)
    expect(isAdminAccount({ authorities: ['ROLE_ADMIN'] })).toBe(true)
    expect(isAdminAccount({ roles: ['ADMIN'] })).toBe(true)
    expect(isAdminAccount({ username: 'user1', role: 'USER' })).toBe(false)
    expect(isAdminAccount(null)).toBe(false)
  })

  it('checks specific permissions correctly', () => {
    expect(hasPermission({ role: 'ADMIN' }, 'ANY_PERMISSION')).toBe(true)
    expect(hasPermission({ permissions: ['DELETE_MESSAGE'] }, 'DELETE_MESSAGE')).toBe(true)
    expect(hasPermission({ permissions: ['READ_MESSAGE'] }, 'DELETE_MESSAGE')).toBe(false)
  })
})
