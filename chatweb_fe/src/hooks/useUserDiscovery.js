import { useCallback, useEffect, useState } from 'react'
import { apiRequest, getErrorMessage } from '../services/apiClient.js'
import { isAdminAccount, isAdminUser } from '../services/authorization.js'
import { displayName } from '../components/chat/Avatar.jsx'

export function useUserDiscovery({
  activeSection,
  currentUsernameKey,
  language,
  showToast,
  t,
}) {
  const [searchQuery, setSearchQuery] = useState('')
  const [searchType, setSearchType] = useState('username')
  const [searchResults, setSearchResults] = useState([])
  const [suggestions, setSuggestions] = useState([])
  const [searching, setSearching] = useState(false)
  const [loadingSuggestions, setLoadingSuggestions] = useState(false)

  const loadSuggestions = useCallback(async () => {
    setLoadingSuggestions(true)
    try {
      const response = await apiRequest('/api/search/users?size=24&sortDir=asc')
      setSuggestions(response?.data?.content || [])
    } catch (error) {
      showToast(getErrorMessage(error, t('errorGeneric')), 'error')
    } finally {
      setLoadingSuggestions(false)
    }
  }, [showToast, t])

  useEffect(() => {
    if (activeSection !== 'friends' || searchQuery.trim() || suggestions.length) return
    void loadSuggestions()
  }, [activeSection, loadSuggestions, searchQuery, suggestions.length])

  useEffect(() => {
    const query = searchQuery.trim()
    if (activeSection !== 'friends' || !query) {
      setSearchResults([])
      setSearching(false)
      return undefined
    }

    const controller = new AbortController()
    const timeout = window.setTimeout(async () => {
      setSearching(true)
      try {
        const response = await apiRequest(
          `/api/search/users?keyword=${encodeURIComponent(query)}&size=30`,
          { signal: controller.signal },
        )
        const normalizedQuery = query.toLocaleLowerCase(language)
        const filtered = (response?.data?.content || []).filter((person) => {
          if (String(person.username || '').trim().toLocaleLowerCase('en-US') === currentUsernameKey) return false
          if (isAdminAccount(person) || isAdminUser(person)) return false
          if (searchType === 'username') return person.username?.toLocaleLowerCase(language).includes(normalizedQuery)
          return displayName(person).toLocaleLowerCase(language).includes(normalizedQuery)
        })
        setSearchResults(filtered)
      } catch (error) {
        if (error.name !== 'AbortError') showToast(getErrorMessage(error, t('errorGeneric')), 'error')
      } finally {
        setSearching(false)
      }
    }, 300)

    return () => {
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [activeSection, currentUsernameKey, language, searchQuery, searchType, showToast, t])

  return {
    searchQuery,
    setSearchQuery,
    searchType,
    setSearchType,
    searchResults,
    setSearchResults,
    suggestions,
    setSuggestions,
    searching,
    loadingSuggestions,
    loadSuggestions,
  }
}

export default useUserDiscovery
