import { useCallback, useEffect, useRef } from 'react'
import inboxSoundUrl from '../assets/musics/new-inbox.mp3'
import notificationSoundUrl from '../assets/musics/new-notification.mp3'

const SOUND_URLS = {
  inbox: inboxSoundUrl,
  notification: notificationSoundUrl,
}

export function useNotificationSound(sound = 'notification') {
  const audioRef = useRef(null)
  const unlockedRef = useRef(false)
  const soundUrl = SOUND_URLS[sound] || SOUND_URLS.notification

  useEffect(() => {
    const audio = new Audio(soundUrl)
    audio.preload = 'auto'
    audio.volume = 0.55
    audioRef.current = audio

    const removeUnlockListeners = () => {
      window.removeEventListener('pointerdown', unlockAudio)
      window.removeEventListener('keydown', unlockAudio)
    }

    const finishUnlock = () => {
      audio.pause()
      audio.currentTime = 0
      audio.muted = false
      unlockedRef.current = true
      removeUnlockListeners()
    }

    const unlockAudio = () => {
      if (unlockedRef.current) return
      audio.muted = true
      const playback = audio.play()
      if (playback?.then) playback.then(finishUnlock).catch(() => { audio.muted = false })
      else finishUnlock()
    }

    window.addEventListener('pointerdown', unlockAudio)
    window.addEventListener('keydown', unlockAudio)

    return () => {
      removeUnlockListeners()
      audio.pause()
      audioRef.current = null
      unlockedRef.current = false
    }
  }, [soundUrl])

  return useCallback(() => {
    const audio = audioRef.current
    if (!audio || !unlockedRef.current) return false
    audio.currentTime = 0
    const playback = audio.play()
    if (playback?.catch) playback.catch(() => {})
    return true
  }, [])
}
