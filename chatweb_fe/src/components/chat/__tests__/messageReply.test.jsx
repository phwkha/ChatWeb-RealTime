import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import MessageItem from '../MessageItem.jsx'
import MessageContextMenu from '../MessageContextMenu.jsx'
import MessageComposer from '../MessageComposer.jsx'

describe('Message Reply Component Suite', () => {
  const mockT = (key) => key

  describe('MessageContextMenu Reply Action', () => {
    it('renders Reply menu item and calls onReply on click', () => {
      const onReply = vi.fn()
      const onClose = vi.fn()
      const message = { id: 'msg-1', sender: 'bob', content: 'Hello there' }

      render(
        <MessageContextMenu
          menu={{ message, x: 100, y: 200 }}
          user={{ username: 'alice' }}
          t={mockT}
          onClose={onClose}
          onToggleReaction={vi.fn()}
          onCopy={vi.fn()}
          onReply={onReply}
          onBeginEdit={vi.fn()}
          onRevoke={vi.fn()}
        />
      )

      const replyButton = screen.getByRole('menuitem', { name: /reply/i })
      expect(replyButton).toBeInTheDocument()

      fireEvent.click(replyButton)
      expect(onClose).toHaveBeenCalled()
      expect(onReply).toHaveBeenCalledWith(message)
    })

    it('does not render Reply menu item if message is deleted', () => {
      const message = { id: 'msg-deleted', sender: 'bob', content: '', deleted: true }

      render(
        <MessageContextMenu
          menu={{ message, x: 100, y: 200 }}
          user={{ username: 'alice' }}
          t={mockT}
          onClose={vi.fn()}
          onToggleReaction={vi.fn()}
          onCopy={vi.fn()}
          onReply={vi.fn()}
          onBeginEdit={vi.fn()}
          onRevoke={vi.fn()}
        />
      )

      expect(screen.queryByRole('menuitem', { name: /reply/i })).not.toBeInTheDocument()
    })
  })

  describe('MessageItem Reply and Quote Rendering', () => {
    it('renders quick reply button on hover and triggers onReply', () => {
      const onReply = vi.fn()
      const message = { id: 'msg-123', sender: 'bob', content: 'Good morning' }

      render(
        <MessageItem
          message={message}
          messageKey="msg-123"
          selectedUser={{ username: 'bob', firstName: 'Bob' }}
          user={{ username: 'alice' }}
          isMine={false}
          isGrouped={false}
          isSearchTarget={false}
          showActions={false}
          showDetails={false}
          t={mockT}
          onContextMenu={vi.fn()}
          onBubbleClick={vi.fn()}
          onBubbleKeyDown={vi.fn()}
          onReply={onReply}
        />
      )

      const hoverReplyBtn = screen.getByRole('button', { name: 'reply' })
      expect(hoverReplyBtn).toBeInTheDocument()

      fireEvent.click(hoverReplyBtn)
      expect(onReply).toHaveBeenCalledWith(message)
    })

    it('renders quote box when message has replyToId and quotedMessage is provided', () => {
      const onQuoteClick = vi.fn()
      const message = {
        id: 'msg-2',
        sender: 'alice',
        content: 'I agree with you',
        replyToId: 'msg-1',
      }
      const quotedMessage = {
        id: 'msg-1',
        sender: 'bob',
        content: 'Original message text',
      }

      render(
        <MessageItem
          message={message}
          messageKey="msg-2"
          selectedUser={{ username: 'bob', firstName: 'Bob' }}
          user={{ username: 'alice' }}
          isMine={true}
          isGrouped={false}
          isSearchTarget={false}
          showActions={false}
          showDetails={false}
          quotedMessage={quotedMessage}
          t={mockT}
          onContextMenu={vi.fn()}
          onBubbleClick={vi.fn()}
          onBubbleKeyDown={vi.fn()}
          onQuoteClick={onQuoteClick}
        />
      )

      const quoteBox = screen.getByRole('button', { name: 'replyingTo' })
      expect(quoteBox).toBeInTheDocument()
      expect(quoteBox).toHaveClass('is-mine')
      expect(screen.getByText('Original message text')).toBeInTheDocument()

      // Clicking quote box triggers onQuoteClick with replyToId
      fireEvent.click(quoteBox)
      expect(onQuoteClick).toHaveBeenCalledWith('msg-1')

      // Keydown Enter triggers onQuoteClick
      fireEvent.keyDown(quoteBox, { key: 'Enter' })
      expect(onQuoteClick).toHaveBeenCalledTimes(2)
    })

    it('displays media indicator when quoted message has image contentType', () => {
      const message = {
        id: 'msg-3',
        sender: 'bob',
        content: 'Nice photo!',
        replyToId: 'msg-photo',
      }
      const quotedMessage = {
        id: 'msg-photo',
        sender: 'alice',
        contentType: 'IMAGE',
        fileName: 'vacation.png',
        fileUrl: 'https://example.com/vacation.png',
      }

      render(
        <MessageItem
          message={message}
          messageKey="msg-3"
          selectedUser={{ username: 'bob', firstName: 'Bob' }}
          user={{ username: 'alice' }}
          isMine={false}
          isGrouped={false}
          isSearchTarget={false}
          showActions={false}
          showDetails={false}
          quotedMessage={quotedMessage}
          t={mockT}
          onContextMenu={vi.fn()}
          onBubbleClick={vi.fn()}
          onBubbleKeyDown={vi.fn()}
        />
      )

      expect(screen.getByText('vacation.png')).toBeInTheDocument()
    })

    it('triggers onFetchReplyMessage when quotedMessage is not provided', () => {
      const onFetchReplyMessage = vi.fn()
      const message = {
        id: 'msg-4',
        sender: 'bob',
        content: 'Looking back at older info',
        replyToId: 'msg-old-id',
      }

      render(
        <MessageItem
          message={message}
          messageKey="msg-4"
          selectedUser={{ username: 'bob', firstName: 'Bob' }}
          user={{ username: 'alice' }}
          isMine={false}
          isGrouped={false}
          isSearchTarget={false}
          showActions={false}
          showDetails={false}
          quotedMessage={null}
          t={mockT}
          onContextMenu={vi.fn()}
          onBubbleClick={vi.fn()}
          onBubbleKeyDown={vi.fn()}
          onFetchReplyMessage={onFetchReplyMessage}
        />
      )

      expect(onFetchReplyMessage).toHaveBeenCalledWith('msg-old-id')
    })

    it('applies is-highlighted class when isHighlighted is true', () => {
      const message = { id: 'msg-5', sender: 'bob', content: 'Target message' }

      const { container } = render(
        <MessageItem
          message={message}
          messageKey="msg-5"
          selectedUser={{ username: 'bob', firstName: 'Bob' }}
          user={{ username: 'alice' }}
          isMine={false}
          isGrouped={false}
          isSearchTarget={false}
          isHighlighted={true}
          showActions={false}
          showDetails={false}
          t={mockT}
          onContextMenu={vi.fn()}
          onBubbleClick={vi.fn()}
          onBubbleKeyDown={vi.fn()}
        />
      )

      const row = container.querySelector('.message-row')
      expect(row).toHaveClass('is-highlighted')
    })
  })

  describe('MessageComposer Reply Banner', () => {
    it('renders quote banner when replyingToMessage is active', () => {
      const onCancelReply = vi.fn()
      const replyingToMessage = {
        id: 'msg-parent',
        sender: 'bob',
        content: 'Original question?',
      }

      render(
        <MessageComposer
          messageDraft=""
          rateLimitRemaining={0}
          uploadingMedia={false}
          connectionState="connected"
          emojiPickerOpen={false}
          mediaInputRef={{ current: null }}
          messageInputRef={{ current: null }}
          replyingToMessage={replyingToMessage}
          currentUser={{ username: 'alice' }}
          selectedUser={{ username: 'bob', firstName: 'Bob' }}
          t={mockT}
          onSubmit={vi.fn()}
          onDraftChange={vi.fn()}
          onMediaSelect={vi.fn()}
          onToggleEmojiPicker={vi.fn()}
          onInsertEmoji={vi.fn()}
          onCancelReply={onCancelReply}
        />
      )

      const banner = screen.getByRole('region', { name: 'replyingTo' })
      expect(banner).toBeInTheDocument()
      expect(screen.getByText('Bob')).toBeInTheDocument()
      expect(screen.getByText('Original question?')).toBeInTheDocument()

      const closeButton = screen.getByRole('button', { name: 'cancelReply' })
      expect(closeButton).toBeInTheDocument()
      fireEvent.click(closeButton)
      expect(onCancelReply).toHaveBeenCalled()
    })

    it('cancels reply on Escape keydown in composer input', () => {
      const onCancelReply = vi.fn()
      const replyingToMessage = {
        id: 'msg-parent',
        sender: 'bob',
        content: 'Original question?',
      }

      render(
        <MessageComposer
          messageDraft="Drafting answer"
          rateLimitRemaining={0}
          uploadingMedia={false}
          connectionState="connected"
          emojiPickerOpen={false}
          mediaInputRef={{ current: null }}
          messageInputRef={{ current: null }}
          replyingToMessage={replyingToMessage}
          currentUser={{ username: 'alice' }}
          selectedUser={{ username: 'bob', firstName: 'Bob' }}
          t={mockT}
          onSubmit={vi.fn()}
          onDraftChange={vi.fn()}
          onMediaSelect={vi.fn()}
          onToggleEmojiPicker={vi.fn()}
          onInsertEmoji={vi.fn()}
          onCancelReply={onCancelReply}
        />
      )

      const input = screen.getByDisplayValue('Drafting answer')
      fireEvent.keyDown(input, { key: 'Escape' })
      expect(onCancelReply).toHaveBeenCalled()
    })
  })
})
