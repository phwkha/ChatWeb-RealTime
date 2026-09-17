import { useCallback, useEffect, useRef } from 'react'
import { useNotificationSound } from './useNotificationSound.js'
import inboxSoundUrl from '../assets/musics/new-inbox.mp3'

export function useChatAudio() {
  const playNotificationSound = useNotificationSound('notification')
  const playInboxSound = useNotificationSound('inbox')
  const ringtoneAudioRef = useRef(null)

  useEffect(() => {
    return () => {
      if (ringtoneAudioRef.current) {
        ringtoneAudioRef.current.pause()
        ringtoneAudioRef.current = null
      }
    }
  }, [])

  const playRingtone = useCallback(() => {
    try {
      if (!ringtoneAudioRef.current) {
        const audio = new Audio(inboxSoundUrl)
        audio.loop = true
        audio.volume = 0.6
        ringtoneAudioRef.current = audio
      }
      const playback = ringtoneAudioRef.current.play()
      if (playback?.catch) playback.catch(() => {})
      return true
    } catch {
      return false
    }
  }, [])

  const stopRingtone = useCallback(() => {
    if (ringtoneAudioRef.current) {
      ringtoneAudioRef.current.pause()
      ringtoneAudioRef.current.currentTime = 0
    }
  }, [])

  return {
    playNotificationSound,
    playInboxSound,
    playRingtone,
    stopRingtone,
  }
}

export default useChatAudio
