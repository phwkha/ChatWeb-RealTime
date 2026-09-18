import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import ChatSidebar from '../ChatSidebar.jsx'
import ChatHeader from '../ChatHeader.jsx'
import MessageItem from '../MessageItem.jsx'
import MessageComposer from '../MessageComposer.jsx'
import ConfirmActionDialog from '../dialogs/ConfirmActionDialog.jsx'
import RevokeMessageDialog from '../dialogs/RevokeMessageDialog.jsx'
import ReportUserDialog from '../dialogs/ReportUserDialog.jsx'

describe('Extracted Chat Components', () => {
  const mockT = (key) => key

  it('renders ChatSidebar with friends list and profile', () => {
    const onSelectFriend = vi.fn()
    const friends = [{ username: 'john', firstName: 'John', lastName: 'Doe' }]

    render(
      <ChatSidebar
        activeSection="chat"
        conversationQuery=""
        onConversationQueryChange={() => {}}
        friends={friends}
        filteredFriends={friends}
        selectedUser={null}
        onSelectFriend={onSelectFriend}
        unreadCounts={{ john: 2 }}
        typingUsers={{}}
        currentUserWithPresence={{ username: 'me', isOnline: true }}
        currentUser={{ username: 'me' }}
        isAdmin={true}
        t={mockT}
        onOpenFriendsSection={() => {}}
      />
    )

    expect(screen.getByText('conversations')).toBeInTheDocument()
    expect(screen.getByText('John Doe')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()

    fireEvent.click(screen.getByText('John Doe'))
    expect(onSelectFriend).toHaveBeenCalledWith(friends[0])
  })

  it('renders ChatHeader with peer info and dropdown actions', () => {
    const onBack = vi.fn()
    const onToggleMenu = vi.fn()
    const selectedUser = { username: 'alice', firstName: 'Alice', isOnline: true }

    render(
      <ChatHeader
        selectedUser={selectedUser}
        connectionState="connected"
        conversationMenuOpen={true}
        conversationMenuRef={{ current: null }}
        selectedConversationBlocked={false}
        isTyping={false}
        t={mockT}
        onBack={onBack}
        onToggleMenu={onToggleMenu}
        onOpenSearch={() => {}}
        onUnblock={() => {}}
        onConfirmBlock={() => {}}
        onConfirmUnfriend={() => {}}
        onOpenReport={() => {}}
      />
    )

    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('online')).toBeInTheDocument()
    expect(screen.getByText('searchMessages')).toBeInTheDocument()
    expect(screen.getByText('blockMessages')).toBeInTheDocument()
    expect(screen.getByText('unfriend')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Back'))
    expect(onBack).toHaveBeenCalled()
  })

  it('renders MessageItem with reactions and bubble content', () => {
    const message = {
      id: 'msg-1',
      sender: 'alice',
      content: 'Hello world',
      timestamp: new Date().toISOString(),
      status: 'SENT',
      reactions: { bob: 'LIKE' },
    }

    render(
      <MessageItem
        message={message}
        messageKey="msg-1"
        selectedUser={{ username: 'alice' }}
        user={{ username: 'bob' }}
        isMine={false}
        isGrouped={false}
        isSearchTarget={false}
        showActions={false}
        showDetails={true}
        isReactionPickerOpen={false}
        isEditHistoryOpen={false}
        isEditing={false}
        editingContent=""
        reactionSubmitting={false}
        messageActionPending={false}
        connectionState="connected"
        language="en"
        t={mockT}
        onContextMenu={() => {}}
        onBubbleClick={() => {}}
        onBubbleKeyDown={() => {}}
        onToggleReactionPicker={() => {}}
        onToggleEditHistory={() => {}}
        onSelectReaction={() => {}}
        onRetry={() => {}}
        onBeginEdit={() => {}}
        onSaveEdit={() => {}}
        onCancelEdit={() => {}}
        onChangeEditContent={() => {}}
        onRevokeClick={() => {}}
      />
    )

    expect(screen.getByText('Hello world')).toBeInTheDocument()
    expect(screen.getByText('👍')).toBeInTheDocument()
  })

  it('renders MessageComposer with input and submit', () => {
    const onSubmit = vi.fn((e) => e.preventDefault())
    const onDraftChange = vi.fn()

    render(
      <MessageComposer
        messageDraft="New draft"
        rateLimitRemaining={0}
        uploadingMedia={false}
        connectionState="connected"
        emojiPickerOpen={false}
        mediaInputRef={{ current: null }}
        messageInputRef={{ current: null }}
        t={mockT}
        onSubmit={onSubmit}
        onDraftChange={onDraftChange}
        onMediaSelect={() => {}}
        onToggleEmojiPicker={() => {}}
        onInsertEmoji={() => {}}
      />
    )

    const input = screen.getByDisplayValue('New draft')
    expect(input).toBeInTheDocument()

    fireEvent.change(input, { target: { value: 'Updated' } })
    expect(onDraftChange).toHaveBeenCalled()

    fireEvent.submit(input.closest('form'))
    expect(onSubmit).toHaveBeenCalled()
  })

  it('renders ConfirmActionDialog and confirms', () => {
    const onConfirm = vi.fn()
    const onClose = vi.fn()

    render(
      <ConfirmActionDialog
        action="block"
        selectedUser={{ username: 'badguy' }}
        pending={false}
        t={mockT}
        onClose={onClose}
        onConfirm={onConfirm}
      />
    )

    expect(screen.getByText('blockConfirmTitle')).toBeInTheDocument()
    expect(screen.getByText('@badguy')).toBeInTheDocument()

    fireEvent.click(screen.getByText('confirm'))
    expect(onConfirm).toHaveBeenCalled()

    fireEvent.click(screen.getByText('cancel'))
    expect(onClose).toHaveBeenCalled()
  })

  it('renders RevokeMessageDialog and confirms', () => {
    const onConfirm = vi.fn()

    render(
      <RevokeMessageDialog
        message={{ id: 'm1', content: 'Secret text' }}
        pending={false}
        t={mockT}
        onClose={() => {}}
        onConfirm={onConfirm}
      />
    )

    expect(screen.getByText('revokeMessageTitle')).toBeInTheDocument()
    expect(screen.getByText('Secret text')).toBeInTheDocument()

    fireEvent.click(screen.getByText('revokeMessage'))
    expect(onConfirm).toHaveBeenCalled()
  })

  it('renders ReportUserDialog', () => {
    const onClose = vi.fn()

    render(
      <ReportUserDialog
        isOpen={true}
        selectedUser={{ username: 'spammer' }}
        t={mockT}
        onClose={onClose}
      />
    )

    expect(screen.getByText('reportTitle')).toBeInTheDocument()
    expect(screen.getByText('@spammer')).toBeInTheDocument()

    fireEvent.click(screen.getByText('cancel'))
    expect(onClose).toHaveBeenCalled()
  })
})
