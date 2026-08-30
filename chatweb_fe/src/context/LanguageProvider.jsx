import { useMemo, useState } from 'react'
import { CHAT_TRANSLATIONS } from '../i18n/chatTranslations.js'
import { LanguageContext } from './language-context.js'

export function LanguageProvider({ children }) {
  const [language, setLanguageState] = useState(() => localStorage.getItem('chatweb-language') || 'vi')

  const setLanguage = (nextLanguage) => {
    const normalized = CHAT_TRANSLATIONS[nextLanguage] ? nextLanguage : 'vi'
    localStorage.setItem('chatweb-language', normalized)
    setLanguageState(normalized)
  }

  const value = useMemo(() => ({
    language,
    setLanguage,
    t: (key) => CHAT_TRANSLATIONS[language]?.[key] || CHAT_TRANSLATIONS.vi[key] || key,
  }), [language])

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>
}
