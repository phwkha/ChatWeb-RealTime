import { useCallback, useEffect, useRef, useState } from 'react'

export function useWebRTC({ onCallEnded, onSignal } = {}) {
  const [callStatus, setCallStatus] = useState('idle') // 'idle' | 'calling' | 'incoming' | 'connected' | 'ended'
  const [callType, setCallType] = useState('audio') // 'audio' | 'video'
  const [remotePeer, setRemotePeer] = useState(null)
  const [localStream, setLocalStream] = useState(null)
  const [remoteStream, setRemoteStream] = useState(null)
  const [isMuted, setIsMuted] = useState(false)
  const [isVideoOff, setIsVideoOff] = useState(false)

  const peerConnectionRef = useRef(null)
  const localStreamRef = useRef(null)

  const stopTracks = useCallback(() => {
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop())
      localStreamRef.current = null
      setLocalStream(null)
    }
    if (peerConnectionRef.current) {
      peerConnectionRef.current.close()
      peerConnectionRef.current = null
    }
    setRemoteStream(null)
  }, [])

  const endCall = useCallback(() => {
    stopTracks()
    setCallStatus('ended')
    setRemotePeer(null)
    if (onCallEnded) onCallEnded()
    const timer = setTimeout(() => {
      setCallStatus('idle')
    }, 1500)
    return () => clearTimeout(timer)
  }, [onCallEnded, stopTracks])

  const startCall = useCallback(async (peer, type = 'audio') => {
    if (!peer || typeof navigator === 'undefined' || !navigator.mediaDevices) return false
    setCallStatus('calling')
    setCallType(type)
    setRemotePeer(peer)

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: type === 'video',
      })
      localStreamRef.current = stream
      setLocalStream(stream)

      if (typeof RTCPeerConnection !== 'undefined') {
        const pc = new RTCPeerConnection({
          iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
        })
        stream.getTracks().forEach((track) => pc.addTrack(track, stream))
        pc.ontrack = (event) => {
          if (event.streams && event.streams[0]) {
            setRemoteStream(event.streams[0])
          }
        }
        peerConnectionRef.current = pc
      }

      if (onSignal) {
        onSignal({ type: 'CALL_OFFER', recipient: peer.username, callType: type })
      }
      return true
    } catch {
      endCall()
      return false
    }
  }, [endCall, onSignal])

  const answerCall = useCallback(async () => {
    if (!remotePeer || typeof navigator === 'undefined' || !navigator.mediaDevices) return false
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: callType === 'video',
      })
      localStreamRef.current = stream
      setLocalStream(stream)
      setCallStatus('connected')
      if (onSignal) {
        onSignal({ type: 'CALL_ANSWER', recipient: remotePeer.username })
      }
      return true
    } catch {
      endCall()
      return false
    }
  }, [callType, endCall, onSignal, remotePeer])

  const rejectCall = useCallback(() => {
    if (remotePeer && onSignal) {
      onSignal({ type: 'CALL_REJECT', recipient: remotePeer.username })
    }
    endCall()
  }, [endCall, onSignal, remotePeer])

  const toggleMute = useCallback(() => {
    if (localStreamRef.current) {
      const audioTracks = localStreamRef.current.getAudioTracks()
      audioTracks.forEach((track) => {
        track.enabled = !track.enabled
      })
      setIsMuted((prev) => !prev)
    }
  }, [])

  const toggleVideo = useCallback(() => {
    if (localStreamRef.current) {
      const videoTracks = localStreamRef.current.getVideoTracks()
      videoTracks.forEach((track) => {
        track.enabled = !track.enabled
      })
      setIsVideoOff((prev) => !prev)
    }
  }, [])

  useEffect(() => {
    return () => {
      stopTracks()
    }
  }, [stopTracks])

  return {
    callStatus,
    callType,
    remotePeer,
    localStream,
    remoteStream,
    isMuted,
    isVideoOff,
    startCall,
    answerCall,
    rejectCall,
    endCall,
    toggleMute,
    toggleVideo,
    setIncomingCall: (peer, type) => {
      setRemotePeer(peer)
      setCallType(type || 'audio')
      setCallStatus('incoming')
    },
  }
}

export default useWebRTC
