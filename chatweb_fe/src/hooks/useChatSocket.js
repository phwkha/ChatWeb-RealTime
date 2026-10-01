import { useCallback, useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { API_BASE_URL, getAccessToken, refreshAccessToken } from '../services/apiClient.js'
import { normalizeSocketPayload } from '../services/socketPayload.js'

function parseFrame(frame) {
  try {
    return normalizeSocketPayload(JSON.parse(frame.body))
  } catch {
    return { errorCode: 'STOMP_ERROR' }
  }
}

export function useChatSocket({ enabled, language, subscribeToWorld, onMessage, onNotification, onWorldMessage, onError, onConnected }) {
  const clientRef = useRef(null)
  const callbacksRef = useRef({ onMessage, onNotification, onWorldMessage, onError, onConnected })
  const [connectionState, setConnectionState] = useState('connecting')
  const isReconnectRef = useRef(false)

  useEffect(() => {
    callbacksRef.current = { onMessage, onNotification, onWorldMessage, onError, onConnected }
  }, [onConnected, onError, onMessage, onNotification, onWorldMessage])

  useEffect(() => {
    if (!enabled) {
      return undefined
    }

    let disposed = false
    const token = getAccessToken()
    const connectHeaders = {
      'Accept-Language': language,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    }

    let isRefreshingAuth = false
    let refreshPromiseRef = null

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
      connectHeaders,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
      beforeConnect: async () => {
        if (isRefreshingAuth && refreshPromiseRef) {
          try {
            await refreshPromiseRef
          } catch {
            // ignore
          }
        }
        const currentToken = getAccessToken()
        if (currentToken) {
          client.connectHeaders = {
            ...client.connectHeaders,
            Authorization: `Bearer ${currentToken}`,
          }
        }
      },
      onConnect: () => {
        if (disposed) return
        setConnectionState('connected')
        client.subscribe('/user/queue/messages', (frame) => callbacksRef.current.onMessage?.(parseFrame(frame)))
        client.subscribe('/user/queue/notifications', (frame) => callbacksRef.current.onNotification?.(parseFrame(frame)))
        client.subscribe('/user/queue/errors', (frame) => callbacksRef.current.onError?.(parseFrame(frame)))
        if (subscribeToWorld) {
          client.subscribe('/topic/public', (frame) => callbacksRef.current.onWorldMessage?.(parseFrame(frame)))
        }
        const wasReconnect = isReconnectRef.current
        isReconnectRef.current = true
        callbacksRef.current.onConnected?.({ isReconnect: wasReconnect })
      },
      onStompError: async (frame) => {
        setConnectionState('disconnected')
        const payload = parseFrame(frame)
        const errorCode = String(payload?.errorCode || payload?.code || '').toUpperCase()
        const isAuthError = errorCode === 'TOKEN_EXPIRED' ||
                            errorCode === 'TOKEN_INVALID' ||
                            errorCode === '401' ||
                            errorCode === '4011' ||
                            errorCode === '4012' ||
                            payload?.message?.includes('Phiên đăng nhập') ||
                            payload?.message?.includes('expired')

        if (isAuthError && !disposed) {
          try {
            client.reconnectDelay = 0
            isRefreshingAuth = true
            refreshPromiseRef = refreshAccessToken()
            await refreshPromiseRef
            if (clientRef.current && !disposed) {
              const newToken = getAccessToken()
              clientRef.current.connectHeaders = {
                ...clientRef.current.connectHeaders,
                ...(newToken ? { Authorization: `Bearer ${newToken}` } : {}),
              }
              clientRef.current.reconnectDelay = 3000
              clientRef.current.activate()
              return
            }
          } catch {
            // refresh failed - notifySessionExpired() is automatically triggered by apiClient
          } finally {
            isRefreshingAuth = false
            refreshPromiseRef = null
            if (clientRef.current) {
              clientRef.current.reconnectDelay = 3000
            }
          }
        }
        callbacksRef.current.onError?.(payload)
      },
      onWebSocketClose: () => {
        if (!disposed) {
          isReconnectRef.current = true
          setConnectionState('reconnecting')
        }
      },
      onWebSocketError: () => {
        if (!disposed) {
          isReconnectRef.current = true
          setConnectionState('reconnecting')
        }
      },
    })

    clientRef.current = client
    client.activate()

    return () => {
      disposed = true
      clientRef.current = null
      void client.deactivate()
    }
  }, [enabled, language, subscribeToWorld])

  const publish = useCallback((destination, body) => {
    const client = clientRef.current
    if (!client?.connected) return false
    client.publish({ destination, body: JSON.stringify(body) })
    return true
  }, [])

  return {
    connectionState,
    sendPrivateMessage: useCallback((message) => publish('/app/chat/sendPrivateMessage', message), [publish]),
    sendWorldMessage: useCallback((message) => publish('/app/chat/sendMessageSystem', message), [publish]),
  }
}
