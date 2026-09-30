import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import AppRail from '../AppRail.jsx'
import * as authContext from '../../../context/auth-context.js'
import * as languageContext from '../../../context/language-context.js'

vi.mock('../../../context/auth-context.js', () => ({
  useAuth: vi.fn(),
}))

vi.mock('../../../context/language-context.js', () => ({
  useLanguage: vi.fn(),
}))

describe('AppRail Component', () => {
  const mockUser = { username: 'testuser', firstName: 'Test', lastName: 'User', role: 'USER' }
  const mockT = (key) => key

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authContext.useAuth).mockReturnValue({
      user: mockUser,
      logout: vi.fn(),
    })
    vi.mocked(languageContext.useLanguage).mockReturnValue({
      language: 'vi',
      setLanguage: vi.fn(),
      t: mockT,
    })
  })

  it('does NOT render red badge on friends button even when friendRequestCount > 0', () => {
    render(
      <MemoryRouter>
        <AppRail
          activeSection="chat"
          online={true}
          totalUnreadMessages={0}
          friendRequestCount={5}
          unreadNotificationCount={0}
          onSelectSection={vi.fn()}
        />
      </MemoryRouter>
    )

    const friendsBtn = screen.getByTitle('friends')
    expect(friendsBtn).toBeInTheDocument()
    // Should not contain badge span with count
    expect(friendsBtn.querySelector('span')).toBeNull()
    expect(friendsBtn.classList.contains('rail-badge')).toBe(false)
  })

  it('renders badges on chat and notifications buttons when unread counts > 0', () => {
    render(
      <MemoryRouter>
        <AppRail
          activeSection="chat"
          online={true}
          totalUnreadMessages={3}
          friendRequestCount={5}
          unreadNotificationCount={7}
          onSelectSection={vi.fn()}
        />
      </MemoryRouter>
    )

    const chatBtn = screen.getByTitle('conversations')
    expect(chatBtn.querySelector('span')?.textContent).toBe('3')

    const notifBtn = screen.getByTitle('notifications')
    expect(notifBtn.querySelector('span')?.textContent).toBe('7')

    const friendsBtn = screen.getByTitle('friends')
    expect(friendsBtn.querySelector('span')).toBeNull()
  })

  it('calls onSelectSection when buttons are clicked', () => {
    const onSelectSection = vi.fn()
    render(
      <MemoryRouter>
        <AppRail
          activeSection="chat"
          online={true}
          totalUnreadMessages={0}
          friendRequestCount={0}
          unreadNotificationCount={0}
          onSelectSection={onSelectSection}
        />
      </MemoryRouter>
    )

    fireEvent.click(screen.getByTitle('friends'))
    expect(onSelectSection).toHaveBeenCalledWith('friends')

    fireEvent.click(screen.getByTitle('notifications'))
    expect(onSelectSection).toHaveBeenCalledWith('notifications')

    fireEvent.click(screen.getByTitle('conversations'))
    expect(onSelectSection).toHaveBeenCalledWith('chat')
  })
})
