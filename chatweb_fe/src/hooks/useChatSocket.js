import { useCallback, useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { API_BASE_URL } from '../services/apiClient.js'
import { normalizeSocketPayload } from '../services/socketPayload.js'
import { getAccessToken } from '../services/tokenStore.js'

function parseFrame(frame) {
  try {
    return normalizeSocketPayload(JSON.parse(frame.body))
  } catch {
    return { message: frame.body }
  }
}

export function useChatSocket({ enabled, language, subscribeToWorld, onMessage, onNotification, onWorldMessage, onError, onConnected }) {
  const clientRef = useRef(null)
  const callbacksRef = useRef({ onMessage, onNotification, onWorldMessage, onError, onConnected })
  const [connectionState, setConnectionState] = useState('connecting')

  useEffect(() => {
    callbacksRef.current = { onMessage, onNotification, onWorldMessage, onError, onConnected }
  }, [onConnected, onError, onMessage, onNotification, onWorldMessage])

  useEffect(() => {
    if (!enabled) {
      return undefined
    }

    let disposed = false
    const token = getAccessToken()
    const connectHeaders = { 'Accept-Language': language }
    if (token) connectHeaders.Authorization = `Bearer ${token}`

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
      connectHeaders,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
      onConnect: () => {
        if (disposed) return
        setConnectionState('connected')
        client.subscribe('/user/queue/messages', (frame) => callbacksRef.current.onMessage?.(parseFrame(frame)))
        client.subscribe('/user/queue/notifications', (frame) => callbacksRef.current.onNotification?.(parseFrame(frame)))
        client.subscribe('/user/queue/errors', (frame) => callbacksRef.current.onError?.(parseFrame(frame)))
        if (subscribeToWorld) {
          client.subscribe('/topic/public', (frame) => callbacksRef.current.onWorldMessage?.(parseFrame(frame)))
        }
        callbacksRef.current.onConnected?.()
      },
      onStompError: (frame) => {
        setConnectionState('disconnected')
        callbacksRef.current.onError?.(parseFrame(frame))
      },
      onWebSocketClose: () => {
        if (!disposed) setConnectionState('reconnecting')
      },
      onWebSocketError: () => {
        if (!disposed) setConnectionState('reconnecting')
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
