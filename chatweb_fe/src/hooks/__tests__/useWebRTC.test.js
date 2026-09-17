import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useWebRTC } from '../useWebRTC.js'

describe('useWebRTC', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    navigator.mediaDevices = {
      getUserMedia: vi.fn().mockResolvedValue({
        getTracks: () => [{ stop: vi.fn(), enabled: true }],
        getAudioTracks: () => [{ stop: vi.fn(), enabled: true }],
        getVideoTracks: () => [{ stop: vi.fn(), enabled: true }],
      }),
    }
  })

  it('initializes in idle state', () => {
    const { result } = renderHook(() => useWebRTC())
    expect(result.current.callStatus).toBe('idle')
    expect(result.current.remotePeer).toBeNull()
    expect(result.current.isMuted).toBe(false)
  })

  it('starts a call', async () => {
    const onSignal = vi.fn()
    const { result } = renderHook(() => useWebRTC({ onSignal }))

    await act(async () => {
      const ok = await result.current.startCall({ username: 'bob' }, 'video')
      expect(ok).toBe(true)
    })

    expect(result.current.callStatus).toBe('calling')
    expect(result.current.callType).toBe('video')
    expect(result.current.remotePeer).toEqual({ username: 'bob' })
    expect(onSignal).toHaveBeenCalledWith({
      type: 'CALL_OFFER',
      recipient: 'bob',
      callType: 'video',
    })
  })

  it('answers a call', async () => {
    const onSignal = vi.fn()
    const { result } = renderHook(() => useWebRTC({ onSignal }))

    act(() => {
      result.current.setIncomingCall({ username: 'alice' }, 'audio')
    })
    expect(result.current.callStatus).toBe('incoming')

    await act(async () => {
      const ok = await result.current.answerCall()
      expect(ok).toBe(true)
    })

    expect(result.current.callStatus).toBe('connected')
    expect(onSignal).toHaveBeenCalledWith({
      type: 'CALL_ANSWER',
      recipient: 'alice',
    })
  })

  it('toggles audio mute and video tracks', async () => {
    const { result } = renderHook(() => useWebRTC())

    await act(async () => {
      await result.current.startCall({ username: 'bob' }, 'audio')
    })

    act(() => {
      result.current.toggleMute()
    })
    expect(result.current.isMuted).toBe(true)

    act(() => {
      result.current.toggleVideo()
    })
    expect(result.current.isVideoOff).toBe(true)
  })

  it('ends call', async () => {
    const onCallEnded = vi.fn()
    const { result } = renderHook(() => useWebRTC({ onCallEnded }))

    await act(async () => {
      await result.current.startCall({ username: 'bob' }, 'audio')
    })

    act(() => {
      result.current.endCall()
    })
    expect(result.current.callStatus).toBe('ended')
    expect(onCallEnded).toHaveBeenCalled()
  })
})
