import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore.js'

export const API_BASE_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '')

const IDEMPOTENCY_HEADER = 'X-Idempotency-Key'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

export function getApiLanguage() {
  return localStorage.getItem('chatweb-language') || 'vi'
}

function fallbackMessage(key) {
  const messages = {
    vi: { expired: 'Phiên đăng nhập đã hết hạn.', network: 'Không thể kết nối đến máy chủ.' },
    en: { expired: 'Your session has expired.', network: 'Unable to connect to the server.' },
  }
  return (messages[getApiLanguage()] || messages.vi)[key]
}

export class ApiError extends Error {
  constructor(message, { status = 0, code = 0, data = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.data = data
  }
}

let refreshPromise = null

function withIdempotencyKey(options = {}) {
  const method = (options.method || 'GET').toUpperCase()
  if (SAFE_METHODS.has(method)) return options

  const headers = new Headers(options.headers || {})
  if (!headers.has(IDEMPOTENCY_HEADER)) {
    headers.set(IDEMPOTENCY_HEADER, crypto.randomUUID())
  }

  return { ...options, headers }
}

async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) return null

  try {
    return await response.json()
  } catch {
    return null
  }
}

async function refreshAccessToken() {
  if (!refreshPromise) {
    const refreshOptions = withIdempotencyKey({
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept-Language': getApiLanguage() },
    })
    refreshPromise = fetch(`${API_BASE_URL}/api/auth/refresh-token`, {
      ...refreshOptions,
    }).then(async (response) => {
      const payload = await parseResponse(response)
      if (!response.ok) {
        clearAccessToken()
        throw new ApiError(payload?.message || fallbackMessage('expired'), {
          status: response.status,
          code: payload?.code,
          data: payload?.data,
        })
      }
      setAccessToken(typeof payload?.data === 'string' ? payload.data : payload?.data?.accessToken)
      return payload
    }).finally(() => {
      refreshPromise = null
    })
  }

  return refreshPromise
}

async function sendRequest(path, options) {
  const headers = new Headers(options.headers || {})
  headers.set('Accept-Language', getApiLanguage())

  const token = getAccessToken()
  if (token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`)

  let body = options.body
  if (body && !(body instanceof FormData) && typeof body !== 'string') {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(body)
  }

  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    body,
    headers,
    credentials: 'include',
  })
}

export async function apiRequest(path, options = {}) {
  const { skipRefresh = false, ...requestOptions } = options
  const preparedRequestOptions = withIdempotencyKey(requestOptions)
  let response = await sendRequest(path, preparedRequestOptions)

  if (response.status === 401 && !skipRefresh && path !== '/api/auth/refresh-token') {
    try {
      await refreshAccessToken()
      response = await sendRequest(path, preparedRequestOptions)
    } catch {
      // The original response below provides the most relevant request context.
    }
  }

  const payload = await parseResponse(response)
  if (!response.ok) {
    throw new ApiError(payload?.message || fallbackMessage('network'), {
      status: response.status,
      code: payload?.code,
      data: payload?.data,
    })
  }

  return payload
}

export function getGoogleAuthUrl() {
  return `${API_BASE_URL}/oauth2/authorization/google`
}
