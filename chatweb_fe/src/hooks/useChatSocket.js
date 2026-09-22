import { useCallback, useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { API_BASE_URL, getAccessToken } from '../services/apiClient.js'
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
        const wasReconnect = isReconnectRef.current
        isReconnectRef.current = true
        callbacksRef.current.onConnected?.({ isReconnect: wasReconnect })
      },
      onStompError: (frame) => {
        setConnectionState('disconnected')
        callbacksRef.current.onError?.(parseFrame(frame))
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
