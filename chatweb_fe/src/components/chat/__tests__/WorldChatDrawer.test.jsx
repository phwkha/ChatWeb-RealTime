import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import WorldChatDrawer from '../WorldChatDrawer.jsx'

describe('WorldChatDrawer permission and broadcast controls', () => {
  const defaultProps = {
    isOpen: true,
    worldMessages: [],
    worldHasMore: false,
    worldCursor: null,
    worldDraft: '',
    isAdmin: false,
    canBroadcast: undefined,
    language: 'vi',
    t: (key) => key,
    onClose: vi.fn(),
    onLoadOlder: vi.fn(),
    onChangeDraft: vi.fn(),
    onSubmitBroadcast: vi.fn(),
  }

  it('renders world-composer when canBroadcast is explicitly true', () => {
    render(<WorldChatDrawer {...defaultProps} canBroadcast={true} />)
    expect(screen.getByText('adminBroadcast')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /publish/i })).toBeInTheDocument()
  })

  it('hides world-composer when canBroadcast is explicitly false even if isAdmin is true', () => {
    render(<WorldChatDrawer {...defaultProps} isAdmin={true} canBroadcast={false} />)
    expect(screen.queryByText('adminBroadcast')).toBeNull()
  })

  it('falls back to isAdmin when canBroadcast is undefined (backward compatibility)', () => {
    const { rerender } = render(<WorldChatDrawer {...defaultProps} isAdmin={true} canBroadcast={undefined} />)
    expect(screen.getByText('adminBroadcast')).toBeInTheDocument()

    rerender(<WorldChatDrawer {...defaultProps} isAdmin={false} canBroadcast={undefined} />)
    expect(screen.queryByText('adminBroadcast')).toBeNull()
  })

  it('calls onSubmitBroadcast when form is submitted with non-empty draft', () => {
    const onSubmitBroadcast = vi.fn((e) => e.preventDefault())
    render(
      <WorldChatDrawer
        {...defaultProps}
        canBroadcast={true}
        worldDraft="Thông báo khẩn"
        onSubmitBroadcast={onSubmitBroadcast}
      />
    )
    const publishButton = screen.getByRole('button', { name: /publish/i })
    expect(publishButton).not.toBeDisabled()
    fireEvent.click(publishButton)
    expect(onSubmitBroadcast).toHaveBeenCalledTimes(1)
  })

  it('disables publish button when worldDraft is whitespace only', () => {
    render(<WorldChatDrawer {...defaultProps} canBroadcast={true} worldDraft="   " />)
    const publishButton = screen.getByRole('button', { name: /publish/i })
    expect(publishButton).toBeDisabled()
  })

  it('renders badge worldShort when message.type is WORLD_CHAT', () => {
    const messages = [
      { sender: 'vip_user', content: 'Xin chào thế giới!', timestamp: '2026-10-19T00:00:00Z', type: 'WORLD_CHAT' },
    ]
    render(<WorldChatDrawer {...defaultProps} worldMessages={messages} />)
    expect(screen.getByText('vip_user')).toBeInTheDocument()
    expect(screen.getByText('worldShort')).toBeInTheDocument()
  })

  it('renders badge admin when message.type is ANNOUNCEMENT or missing', () => {
    const messages = [
      { sender: 'system_admin', content: 'Bảo trì hệ thống!', timestamp: '2026-10-19T00:00:00Z', type: 'ANNOUNCEMENT' },
    ]
    render(<WorldChatDrawer {...defaultProps} worldMessages={messages} />)
    expect(screen.getByText('system_admin')).toBeInTheDocument()
    expect(screen.getByText('admin')).toBeInTheDocument()
  })
})
