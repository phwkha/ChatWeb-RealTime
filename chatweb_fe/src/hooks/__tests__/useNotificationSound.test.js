import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useNotificationSound } from '../useNotificationSound.js'

describe('useNotificationSound', () => {
  let playMock
  let pauseMock

  beforeEach(() => {
    playMock = vi.fn().mockReturnValue(Promise.resolve())
    pauseMock = vi.fn()

    vi.stubGlobal('Audio', class MockAudio {
      constructor(src) {
        this.src = src
        this.play = playMock
        this.pause = pauseMock
        this.currentTime = 0
        this.volume = 1
        this.muted = false
      }
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('returns a callable play function', () => {
    const { result } = renderHook(() => useNotificationSound('inbox'))
    expect(typeof result.current).toBe('function')
  })

  it('does not play if not yet unlocked by user interaction', () => {
    const { result } = renderHook(() => useNotificationSound('notification'))
    let played = false
    act(() => {
      played = result.current()
    })
    expect(played).toBe(false)
  })

  it('unlocks audio on pointerdown and allows playback', async () => {
    const { result } = renderHook(() => useNotificationSound('notification'))

    await act(async () => {
      window.dispatchEvent(new Event('pointerdown'))
      await Promise.resolve()
    })

    let played = false
    act(() => {
      played = result.current()
    })

    expect(played).toBe(true)
    expect(playMock).toHaveBeenCalled()
  })

  it('cleans up audio and event listeners on unmount', () => {
    const { unmount } = renderHook(() => useNotificationSound('notification'))
    unmount()
    expect(pauseMock).toHaveBeenCalled()
  })
})
