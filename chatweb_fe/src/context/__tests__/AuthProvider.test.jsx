import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import AuthProvider from '../AuthProvider.jsx'
import { useAuth } from '../auth-context.js'
import * as apiClient from '../../services/apiClient.js'

vi.mock('../../services/apiClient.js', () => ({
  apiRequest: vi.fn(),
  setAccessToken: vi.fn(),
  getAccessToken: vi.fn(() => 'mock-token'),
}))

function TestConsumer() {
  const { user, isInitializing } = useAuth()
  if (isInitializing) return <div>Loading session...</div>
  return <div>{user ? `User: ${user.username}` : 'Not logged in'}</div>
}

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('hydrates user session on mount when authenticated', async () => {
    apiClient.apiRequest
      .mockResolvedValueOnce({ data: 'mock-token' })
      .mockResolvedValueOnce({ data: { username: 'testuser', role: 'USER' } })

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    expect(screen.getByText('Loading session...')).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('User: testuser')).toBeInTheDocument()
    })
  })

  it('handles unauthenticated state gracefully', async () => {
    apiClient.apiRequest.mockRejectedValue(new Error('Unauthenticated'))

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByText('Not logged in')).toBeInTheDocument()
    })
  })
})
