import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import NotificationsSection from '../NotificationsSection.jsx'
import { CHAT_TRANSLATIONS } from '../../../i18n/chatTranslations.js'

describe('NotificationsSection Component', () => {
  const mockT = (key) => key

  const sampleNotifications = [
    {
      id: 1,
      type: 'FRIEND_REQUEST',
      content: 'Alice sent you a friend request',
      isRead: false,
      createdAt: new Date().toISOString(),
      senderUsername: 'alice',
      senderFirstName: 'Alice',
      senderLastName: 'Smith',
      senderAvatar: 'https://example.com/alice.jpg',
    },
    {
      id: 2,
      type: 'REACT_MESSAGE',
      content: 'Bob liked your message',
      isRead: true,
      createdAt: new Date().toISOString(),
      senderUsername: 'bob',
      senderFirstName: 'Bob',
      senderLastName: 'Johnson',
      senderAvatar: null,
    },
  ]

  it('renders notifications with sender name, content, and unread dot', () => {
    render(
      <NotificationsSection
        notifications={sampleNotifications}
        unreadCount={1}
        loading={false}
        t={mockT}
      />
    )

    expect(screen.getByText('Alice Smith')).toBeInTheDocument()
    expect(screen.getByText('Alice sent you a friend request')).toBeInTheDocument()
    expect(screen.getByText('Bob Johnson')).toBeInTheDocument()
    expect(screen.getByText('Bob liked your message')).toBeInTheDocument()

    // Alice is unread, Bob is read. The unread dot has title="unreadNotification"
    expect(screen.getByTitle('unreadNotification')).toBeInTheDocument()
  })

  it('handles "Mark all as read" button state and click', () => {
    const onMarkAllAsRead = vi.fn()

    const { rerender } = render(
      <NotificationsSection
        notifications={sampleNotifications}
        unreadCount={1}
        loading={false}
        onMarkAllAsRead={onMarkAllAsRead}
        t={mockT}
      />
    )

    const markBtn = screen.getByRole('button', { name: 'markAllAsRead' })
    expect(markBtn).not.toBeDisabled()

    fireEvent.click(markBtn)
    expect(onMarkAllAsRead).toHaveBeenCalledTimes(1)

    // When unreadCount is 0, button should be disabled
    rerender(
      <NotificationsSection
        notifications={sampleNotifications}
        unreadCount={0}
        loading={false}
        onMarkAllAsRead={onMarkAllAsRead}
        t={mockT}
      />
    )
    expect(markBtn).toBeDisabled()
  })

  it('handles "Load more" button when hasMore is true', () => {
    const onLoadMore = vi.fn()

    const { rerender } = render(
      <NotificationsSection
        notifications={sampleNotifications}
        hasMore={true}
        loadingMore={false}
        onLoadMore={onLoadMore}
        t={mockT}
      />
    )

    const loadMoreBtn = screen.getByRole('button', { name: 'loadMore' })
    expect(loadMoreBtn).toBeInTheDocument()
    expect(loadMoreBtn).not.toBeDisabled()

    fireEvent.click(loadMoreBtn)
    expect(onLoadMore).toHaveBeenCalledTimes(1)

    // When loadingMore is true
    rerender(
      <NotificationsSection
        notifications={sampleNotifications}
        hasMore={true}
        loadingMore={true}
        onLoadMore={onLoadMore}
        t={mockT}
      />
    )
    expect(screen.getByRole('button', { name: 'loadingNotifications' })).toBeDisabled()
  })

  it('triggers onNotificationClick with correct item for different notification types', () => {
    const onNotificationClick = vi.fn()

    render(
      <NotificationsSection
        notifications={sampleNotifications}
        onNotificationClick={onNotificationClick}
        t={mockT}
      />
    )

    // Click FRIEND_REQUEST notification
    fireEvent.click(screen.getByText('Alice sent you a friend request'))
    expect(onNotificationClick).toHaveBeenCalledWith(sampleNotifications[0])

    // Click REACT_MESSAGE notification
    fireEvent.click(screen.getByText('Bob liked your message'))
    expect(onNotificationClick).toHaveBeenCalledWith(sampleNotifications[1])
  })

  it('renders empty state when there are no notifications', () => {
    render(
      <NotificationsSection
        notifications={[]}
        loading={false}
        t={mockT}
      />
    )

    expect(screen.getByText('noNotifications')).toBeInTheDocument()
  })

  it('filters out non-persisted notification types not saved by BE (e.g. USER_ONLINE, STATUS_MESSAGE)', () => {
    const mixedNotifications = [
      ...sampleNotifications,
      { id: 99, type: 'USER_ONLINE', content: 'User is online', isRead: false },
      { id: 100, type: 'STATUS_MESSAGE', content: 'Read receipt status', isRead: false },
    ]

    render(
      <NotificationsSection
        notifications={mixedNotifications}
        unreadCount={3}
        loading={false}
        t={mockT}
      />
    )

    // Only Alice and Bob should be rendered
    expect(screen.getByText('Alice sent you a friend request')).toBeInTheDocument()
    expect(screen.getByText('Bob liked your message')).toBeInTheDocument()
    expect(screen.queryByText('User is online')).toBeNull()
    expect(screen.queryByText('Read receipt status')).toBeNull()
  })

  it('renders notification content in the selected user language and updates on language switch', () => {
    const rawNotifications = [
      {
        id: 10,
        type: 'FRIEND_REQUEST',
        content: 'Lời mời kết bạn mới',
        isRead: false,
        createdAt: new Date().toISOString(),
        senderUsername: 'alice',
        senderFirstName: 'Alice',
      },
      {
        id: 11,
        type: 'REACT_MESSAGE',
        content: 'đã bày tỏ cảm xúc về một tin nhắn',
        isRead: false,
        createdAt: new Date().toISOString(),
        senderUsername: 'bob',
        senderFirstName: 'Bob',
      },
    ]

    const viT = (key) => CHAT_TRANSLATIONS.vi[key] || key
    const enT = (key) => CHAT_TRANSLATIONS.en[key] || key
    const jaT = (key) => CHAT_TRANSLATIONS.ja[key] || key

    const { rerender } = render(
      <NotificationsSection
        notifications={rawNotifications}
        unreadCount={2}
        loading={false}
        language="vi"
        t={viT}
      />
    )

    expect(screen.getByText('Lời mời kết bạn mới')).toBeInTheDocument()
    expect(screen.getByText('Đã bày tỏ cảm xúc về một tin nhắn')).toBeInTheDocument()

    // Switch to English
    rerender(
      <NotificationsSection
        notifications={rawNotifications}
        unreadCount={2}
        loading={false}
        language="en"
        t={enT}
      />
    )

    expect(screen.getByText('New friend invite')).toBeInTheDocument()
    expect(screen.getByText('Reacted to a message')).toBeInTheDocument()

    // Switch to Japanese
    rerender(
      <NotificationsSection
        notifications={rawNotifications}
        unreadCount={2}
        loading={false}
        language="ja"
        t={jaT}
      />
    )

    expect(screen.getByText('新しい友達招待')).toBeInTheDocument()
    expect(screen.getByText('メッセージにリアクションしました')).toBeInTheDocument()
  })
})
