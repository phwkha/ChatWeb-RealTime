import { useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { CHAT_TRANSLATIONS } from '../i18n/chatTranslations.js'
import { accountApi } from '../services/accountApi.js'
import { AuthContext } from './auth-context.js'
import { LanguageContext } from './language-context.js'

export function LanguageProvider({ children }) {
  const authContext = useContext(AuthContext)
  const user = authContext?.user
  const lastSyncedRef = useRef(null)

  const [language, setLanguageState] = useState(() => {
    if (user?.language && CHAT_TRANSLATIONS[user.language]) {
      return user.language
    }
    const saved = localStorage.getItem('chatweb-language')
    return saved && CHAT_TRANSLATIONS[saved] ? saved : 'vi'
  })

  useEffect(() => {
    if (user?.username && user?.language && CHAT_TRANSLATIONS[user.language]) {
      const key = `${user.username}:${user.language}`
      if (lastSyncedRef.current !== key) {
        lastSyncedRef.current = key
        // oxlint-disable-next-line react/set-state-in-effect -- external profile language synchronization
        setLanguageState(user.language)
        localStorage.setItem('chatweb-language', user.language)
      }
    } else if (!user) {
      lastSyncedRef.current = null
    }
  }, [user?.username, user?.language])

  const setLanguage = useCallback((nextLanguage) => {
    const normalized = CHAT_TRANSLATIONS[nextLanguage] ? nextLanguage : 'vi'
    localStorage.setItem('chatweb-language', normalized)
    setLanguageState(normalized)

    if (user?.username) {
      lastSyncedRef.current = `${user.username}:${normalized}`
      accountApi.updateLanguage(normalized).catch(() => {
        // Background sync failure should not interrupt user UI
      })
    }
  }, [user?.username])

  const value = useMemo(() => ({
    language,
    setLanguage,
    t: (key) => CHAT_TRANSLATIONS[language]?.[key] || CHAT_TRANSLATIONS.vi[key] || key,
  }), [language, setLanguage])

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>
}
