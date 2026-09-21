import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { formatRelativeTime, formatTime, formatDateTime } from '../chatUtils.js'

describe('chatUtils notifications time formatting', () => {
  const fixedNow = new Date('2026-09-21T12:00:00.000Z')

  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(fixedNow)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('formats recent events (< 60 seconds) as just now across languages', () => {
    const thirtySecsAgo = new Date(fixedNow.getTime() - 30 * 1000).toISOString()

    expect(formatRelativeTime(thirtySecsAgo, 'vi')).toBe('vừa xong')
    expect(formatRelativeTime(thirtySecsAgo, 'en')).toBe('just now')
    expect(formatRelativeTime(thirtySecsAgo, 'ja')).toBe('たった今')
  })

  it('formats relative minutes ago', () => {
    const fiveMinsAgo = new Date(fixedNow.getTime() - 5 * 60 * 1000).toISOString()

    const viResult = formatRelativeTime(fiveMinsAgo, 'vi')
    const enResult = formatRelativeTime(fiveMinsAgo, 'en')
    const jaResult = formatRelativeTime(fiveMinsAgo, 'ja')

    expect(viResult).toMatch(/5 phút trước|5 phút/i)
    expect(enResult).toMatch(/5 minutes? ago/i)
    expect(jaResult).toMatch(/5\s*分前/i)
  })

  it('formats relative hours ago', () => {
    const threeHoursAgo = new Date(fixedNow.getTime() - 3 * 3600 * 1000).toISOString()

    const viResult = formatRelativeTime(threeHoursAgo, 'vi')
    const enResult = formatRelativeTime(threeHoursAgo, 'en')
    const jaResult = formatRelativeTime(threeHoursAgo, 'ja')

    expect(viResult).toMatch(/3 giờ trước|3 giờ/i)
    expect(enResult).toMatch(/3 hours? ago/i)
    expect(jaResult).toMatch(/3\s*時間前/i)
  })

  it('formats relative days ago for < 7 days', () => {
    const twoDaysAgo = new Date(fixedNow.getTime() - 2 * 24 * 3600 * 1000).toISOString()

    const viResult = formatRelativeTime(twoDaysAgo, 'vi')
    const enResult = formatRelativeTime(twoDaysAgo, 'en')
    const jaResult = formatRelativeTime(twoDaysAgo, 'ja')

    expect(viResult).toBeTruthy()
    expect(enResult).toMatch(/2 days? ago/i)
    expect(jaResult).toMatch(/(2\s*日前|一昨日)/i)
  })

  it('falls back to formatDateTime for dates older than 7 days', () => {
    const tenDaysAgo = new Date(fixedNow.getTime() - 10 * 24 * 3600 * 1000).toISOString()

    const result = formatRelativeTime(tenDaysAgo, 'en')
    expect(result).toBe(formatDateTime(tenDaysAgo, 'en'))
  })

  it('handles empty or invalid timestamp gracefully', () => {
    expect(formatRelativeTime(null)).toBe('')
    expect(formatRelativeTime('')).toBe('')
    expect(formatRelativeTime('invalid-date')).toBe('')
  })

  it('formats Japanese locale in formatTime and formatDateTime', () => {
    const ts = '2026-09-21T12:30:00.000Z'
    expect(formatTime(ts, 'ja')).toBeTruthy()
    expect(formatDateTime(ts, 'ja')).toBeTruthy()
  })
})
