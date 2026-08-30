export function normalizeSocketPayload(payload) {
  // Backward compatibility for backend images that still wrap socket events
  // as { type: 'MESSAGE'|'NOTIFICATIONS', message, data }.
  if (payload?.type === 'MESSAGE' && payload.data) return payload.data
  if (payload?.type === 'NOTIFICATIONS' && payload.data) {
    return { ...payload.data, message: payload.data.message || payload.message }
  }
  return payload
}
