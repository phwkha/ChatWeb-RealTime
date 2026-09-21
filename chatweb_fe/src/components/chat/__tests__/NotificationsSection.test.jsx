import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import NotificationsSection from '../NotificationsSection.jsx'

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

  it('triggers onNotificationClick when a notification card is clicked', () => {
    const onNotificationClick = vi.fn()

    render(
      <NotificationsSection
        notifications={sampleNotifications}
        onNotificationClick={onNotificationClick}
        t={mockT}
      />
    )

    fireEvent.click(screen.getByText('Alice sent you a friend request'))
    expect(onNotificationClick).toHaveBeenCalledWith(sampleNotifications[0])
  })

  it('renders empty state when there are no notifications or friend requests', () => {
    render(
      <NotificationsSection
        notifications={[]}
        friendRequests={[]}
        worldNotifications={[]}
        loading={false}
        t={mockT}
      />
    )

    expect(screen.getByText('noNotifications')).toBeInTheDocument()
  })
})
