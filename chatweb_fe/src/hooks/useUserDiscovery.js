import { useCallback, useEffect, useMemo, useState } from 'react'
import { apiRequest, getErrorMessage } from '../services/apiClient.js'
import { isAdminAccount, isAdminUser } from '../services/authorization.js'
import { displayName } from '../components/chat/Avatar.jsx'

function formatLocalDate(d) {
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

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

  const [filters, setFilters] = useState({
    gender: 'ALL',
    city: '',
    minAge: '',
    maxAge: '',
  })

  const hasActiveFilters = useMemo(() => {
    return filters.gender !== 'ALL' || Boolean(filters.city.trim()) || Boolean(filters.minAge) || Boolean(filters.maxAge)
  }, [filters])

  const appliedFilterCount = useMemo(() => {
    let count = 0
    if (filters.gender !== 'ALL') count += 1
    if (filters.city.trim()) count += 1
    if (filters.minAge || filters.maxAge) count += 1
    return count
  }, [filters])

  const applyFilters = useCallback((draftFilters) => {
    setFilters(draftFilters)
  }, [])

  const resetFilters = useCallback(() => {
    setFilters({ gender: 'ALL', city: '', minAge: '', maxAge: '' })
  }, [])

  const loadSuggestions = useCallback(async () => {
    setLoadingSuggestions(true)
    try {
      const response = await apiRequest('/api/users/search?size=24&sortDir=asc')
      setSuggestions(response?.data?.content || [])
    } catch (error) {
      showToast(getErrorMessage(error, t('errorGeneric')), 'error')
    } finally {
      setLoadingSuggestions(false)
    }
  }, [showToast, t])

  useEffect(() => {
    if (activeSection !== 'friends' || searchQuery.trim() || hasActiveFilters || suggestions.length) return
    void loadSuggestions()
  }, [activeSection, hasActiveFilters, loadSuggestions, searchQuery, suggestions.length])

  useEffect(() => {
    const query = searchQuery.trim()
    if (activeSection !== 'friends' || (!query && !hasActiveFilters)) {
      setSearchResults([])
      setSearching(false)
      return undefined
    }

    const controller = new AbortController()
    const timeout = window.setTimeout(async () => {
      setSearching(true)
      try {
        let endpoint = ''
        if (hasActiveFilters) {
          const params = new URLSearchParams()
          params.set('page', '0')
          params.set('size', '30')

          if (filters.gender === 'MAN' || filters.gender === 'WOMAN') {
            params.append('user', `gender:${filters.gender}`)
          }

          const sanitizedCity = filters.city.trim().replace(/[*%]/g, '')
          if (sanitizedCity) {
            params.append('address', `city:*${sanitizedCity}*`)
          }

          const now = new Date()
          let minAgeNum = parseInt(filters.minAge, 10)
          let maxAgeNum = parseInt(filters.maxAge, 10)
          const hasValidMin = !Number.isNaN(minAgeNum) && minAgeNum > 0
          const hasValidMax = !Number.isNaN(maxAgeNum) && maxAgeNum > 0

          if (hasValidMin && hasValidMax && minAgeNum > maxAgeNum) {
            const temp = minAgeNum
            minAgeNum = maxAgeNum
            maxAgeNum = temp
          }

          if (hasValidMin) {
            const cutoff = new Date(now.getFullYear() - minAgeNum, now.getMonth(), now.getDate() + 1)
            params.append('user', `birthday<${formatLocalDate(cutoff)}`)
          }
          if (hasValidMax) {
            const cutoff = new Date(now.getFullYear() - (maxAgeNum + 1), now.getMonth(), now.getDate())
            params.append('user', `birthday>${formatLocalDate(cutoff)}`)
          }
          if (query && searchType === 'username') {
            params.append('user', `username:*${query}*`)
          }
          endpoint = `/api/users/search/filter?${params.toString()}`
        } else {
          endpoint = `/api/users/search?keyword=${encodeURIComponent(query)}&size=30`
        }

        const response = await apiRequest(endpoint, { signal: controller.signal })
        const normalizedQuery = query.toLocaleLowerCase(language)
        const filtered = (response?.data?.content || []).filter((person) => {
          if (String(person.username || '').trim().toLocaleLowerCase('en-US') === currentUsernameKey) return false
          if (isAdminAccount(person) || isAdminUser(person)) return false
          if (!query) return true
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
  }, [activeSection, currentUsernameKey, filters, hasActiveFilters, language, searchQuery, searchType, showToast, t])

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
    filters,
    hasActiveFilters,
    appliedFilterCount,
    applyFilters,
    resetFilters,
  }
}

export default useUserDiscovery
