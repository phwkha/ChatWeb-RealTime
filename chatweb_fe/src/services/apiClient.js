import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore.js'

export const API_BASE_URL = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '')

const IDEMPOTENCY_HEADER = 'X-Idempotency-Key'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

export function getApiLanguage() {
  return localStorage.getItem('chatweb-language') || 'vi'
}

const CLIENT_MESSAGES = {
  vi: {
    generic: 'Đã xảy ra lỗi. Vui lòng thử lại.',
    network: 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối và thử lại.',
    invalid: 'Thông tin gửi lên chưa hợp lệ. Vui lòng kiểm tra lại.',
    unauthorized: 'Thông tin đăng nhập không hợp lệ hoặc phiên đăng nhập đã hết hạn.',
    expired: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
    forbidden: 'Bạn không có quyền thực hiện thao tác này.',
    notFound: 'Không tìm thấy dữ liệu được yêu cầu.',
    method: 'Thao tác này hiện không được hỗ trợ.',
    conflict: 'Dữ liệu đã thay đổi hoặc bị trùng. Vui lòng tải lại và thử lại.',
    tooLarge: 'Tệp tải lên vượt quá dung lượng cho phép.',
    rateLimited: 'Bạn thao tác quá nhanh. Vui lòng chờ một lúc rồi thử lại.',
    server: 'Máy chủ đang gặp sự cố. Vui lòng thử lại sau.',
    unavailable: 'Dịch vụ đang tạm thời gián đoạn. Vui lòng thử lại sau.',
  },
  en: {
    generic: 'Something went wrong. Please try again.',
    network: 'Unable to connect to the server. Check your connection and try again.',
    invalid: 'Some submitted information is invalid. Please check it and try again.',
    unauthorized: 'Your credentials are invalid or your session has expired.',
    expired: 'Your session has expired. Please sign in again.',
    forbidden: 'You do not have permission to perform this action.',
    notFound: 'The requested data could not be found.',
    method: 'This action is not currently supported.',
    conflict: 'The data has changed or already exists. Refresh and try again.',
    tooLarge: 'The uploaded file exceeds the allowed size.',
    rateLimited: 'Too many attempts. Please wait a moment and try again.',
    server: 'The server encountered a problem. Please try again later.',
    unavailable: 'The service is temporarily unavailable. Please try again later.',
  },
}

const TECHNICAL_ERROR_PATTERN = /failed to fetch|networkerror|network error|load failed|fetch failed|socket closed|unexpected token|json parse|clipboard unavailable|bad credentials|access is denied|^access denied$|^unauthorized$|^forbidden$|internal server error/i

function fallbackMessage(key = 'generic') {
  const messages = CLIENT_MESSAGES[getApiLanguage()] || CLIENT_MESSAGES.vi
  return messages[key] || messages.generic
}

function fallbackKeyForCode(code) {
  const normalizedCode = String(code || '').toUpperCase()
  if (normalizedCode === 'NETWORK_ERROR') return 'network'
  if (['4011', '4012', 'TOKEN_EXPIRED', 'TOKEN_INVALID'].includes(normalizedCode)) return 'expired'
  if (['400', 'INVALID_INPUT', 'BAD_FORMAT', 'INVALID_DATA', 'CONSTRAINT_VIOLATION', 'ILLEGAL_ARGUMENT', 'ILLEGAL_STATE'].includes(normalizedCode)) return 'invalid'
  if (['401', 'UNAUTHORIZED'].includes(normalizedCode)) return 'unauthorized'
  if (['403', 'ACCESS_FORBIDDEN', 'ACCESS_DENIED'].includes(normalizedCode)) return 'forbidden'
  if (['404', 'RESOURCE_NOT_FOUND'].includes(normalizedCode)) return 'notFound'
  if (normalizedCode === '405') return 'method'
  if (['409', 'RESOURCE_CONFLICT'].includes(normalizedCode)) return 'conflict'
  if (['413', 'PAYLOAD_TOO_LARGE'].includes(normalizedCode)) return 'tooLarge'
  if (['429', 'RATE_LIMITED'].includes(normalizedCode)) return 'rateLimited'
  if (['502', '503', '504', 'SYSTEM_OVERLOAD'].includes(normalizedCode)) return 'unavailable'
  if (['500', 'INTERNAL_SERVER_ERROR', 'PROCESSING_ERROR'].includes(normalizedCode)) return 'server'
  return 'generic'
}

export class ApiError extends Error {
  constructor(message, { status = 0, code = 0, errorCode = '', data = null, fromServer = false } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.errorCode = errorCode
    this.data = data
    this.fromServer = fromServer
  }
}

export function getErrorMessage(error, fallback = '') {
  if (error?.name === 'AbortError') return ''
  const rawMessage = typeof error?.message === 'string' ? error.message.trim() : ''
  const isSafeMessage = rawMessage && !TECHNICAL_ERROR_PATTERN.test(rawMessage)
  const isStructuredServerError = error?.fromServer || Boolean(error?.errorCode) || Boolean(error?.request && error?.code)
  if (isStructuredServerError && isSafeMessage) return rawMessage

  const code = error?.errorCode || error?.code || error?.status
  const fallbackKey = fallbackKeyForCode(code)
  if (fallbackKey !== 'generic') return fallbackMessage(fallbackKey)
  if (isSafeMessage) return rawMessage
  return fallback || fallbackMessage('generic')
}

function networkError() {
  return new ApiError(fallbackMessage('network'), { code: 'NETWORK_ERROR' })
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
          fromServer: Boolean(payload?.message),
        })
      }
      setAccessToken(typeof payload?.data === 'string' ? payload.data : payload?.data?.accessToken)
      return payload
    }).catch((error) => {
      if (error instanceof ApiError || error?.name === 'AbortError') throw error
      throw networkError()
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

  try {
    return await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      body,
      headers,
      credentials: 'include',
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw networkError()
  }
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
    const code = payload?.code || response.status
    throw new ApiError(payload?.message || fallbackMessage(fallbackKeyForCode(code)), {
      status: response.status,
      code,
      data: payload?.data,
      fromServer: Boolean(payload?.message),
    })
  }

  return payload
}

export function getGoogleAuthUrl() {
  return `${API_BASE_URL}/oauth2/authorization/google`
}
