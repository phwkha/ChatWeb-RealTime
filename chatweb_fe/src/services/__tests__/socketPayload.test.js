import { describe, it, expect } from 'vitest'
import { normalizeSocketPayload } from '../socketPayload.js'

describe('socketPayload', () => {
  it('normalizes MESSAGE payload with nested data', () => {
    const raw = {
      type: 'MESSAGE',
      message: 'Incoming message',
      data: { id: 'm-1', content: 'Hello World', sender: 'alice' },
    }
    const result = normalizeSocketPayload(raw)
    expect(result).toEqual({ id: 'm-1', content: 'Hello World', sender: 'alice' })
  })

  it('normalizes NOTIFICATIONS payload and preserves message property', () => {
    const raw = {
      type: 'NOTIFICATIONS',
      message: 'Outer message',
      data: { id: 'n-1', senderUsername: 'bob' },
    }
    const result = normalizeSocketPayload(raw)
    expect(result).toEqual({ id: 'n-1', senderUsername: 'bob', message: 'Outer message' })
  })

  it('normalizes NOTIFICATIONS payload preferring data.message when present', () => {
    const raw = {
      type: 'NOTIFICATIONS',
      message: 'Outer message',
      data: { id: 'n-1', message: 'Inner message' },
    }
    const result = normalizeSocketPayload(raw)
    expect(result).toEqual({ id: 'n-1', message: 'Inner message' })
  })

  it('returns untouched payload when not MESSAGE or NOTIFICATIONS', () => {
    const directPayload = { id: 100, text: 'Direct content' }
    expect(normalizeSocketPayload(directPayload)).toBe(directPayload)
  })

  it('handles null or undefined safely', () => {
    expect(normalizeSocketPayload(null)).toBeNull()
    expect(normalizeSocketPayload(undefined)).toBeUndefined()
  })
})
