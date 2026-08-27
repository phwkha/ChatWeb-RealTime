export const API_BASE_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '')

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
    refreshPromise = fetch(`${API_BASE_URL}/api/auth/refresh-token`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept-Language': 'vi' },
    }).then(async (response) => {
      if (!response.ok) {
        const payload = await parseResponse(response)
        throw new ApiError(payload?.message || 'Phiên đăng nhập đã hết hạn.', {
          status: response.status,
          code: payload?.code,
          data: payload?.data,
        })
      }
      return response
    }).finally(() => {
      refreshPromise = null
    })
  }

  return refreshPromise
}

async function sendRequest(path, options) {
  const headers = new Headers(options.headers || {})
  headers.set('Accept-Language', 'vi')

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
  let response = await sendRequest(path, requestOptions)

  if (response.status === 401 && !skipRefresh && path !== '/api/auth/refresh-token') {
    try {
      await refreshAccessToken()
      response = await sendRequest(path, requestOptions)
    } catch {
      // The original response below provides the most relevant request context.
    }
  }

  const payload = await parseResponse(response)
  if (!response.ok) {
    throw new ApiError(payload?.message || 'Không thể kết nối đến máy chủ.', {
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
