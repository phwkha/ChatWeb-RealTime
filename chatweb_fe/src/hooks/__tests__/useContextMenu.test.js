import { renderHook, act } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { useContextMenu } from '../useContextMenu.js'

describe('useContextMenu', () => {
  it('initializes with null contextMenu', () => {
    const { result } = renderHook(() => useContextMenu())
    expect(result.current.contextMenu).toBeNull()
  })

  it('opens context menu for valid message', () => {
    const { result } = renderHook(() => useContextMenu())
    const mockEvent = {
      preventDefault: () => {},
      clientX: 100,
      clientY: 150,
    }
    const mockMessage = { id: 'msg-1', content: 'Hello' }

    act(() => {
      result.current.openContextMenu(mockEvent, mockMessage, 'key-1')
    })

    expect(result.current.contextMenu).not.toBeNull()
    expect(result.current.contextMenu.message.id).toBe('msg-1')
    expect(result.current.contextMenu.messageKey).toBe('key-1')
  })

  it('does not open context menu for deleted message', () => {
    const { result } = renderHook(() => useContextMenu())
    const mockEvent = {
      preventDefault: () => {},
      clientX: 100,
      clientY: 150,
    }
    const deletedMessage = { id: 'msg-2', deleted: true }

    act(() => {
      result.current.openContextMenu(mockEvent, deletedMessage, 'key-2')
    })

    expect(result.current.contextMenu).toBeNull()
  })

  it('closes context menu', () => {
    const { result } = renderHook(() => useContextMenu())
    const mockEvent = {
      preventDefault: () => {},
      clientX: 100,
      clientY: 150,
    }
    act(() => {
      result.current.openContextMenu(mockEvent, { id: 'msg-1', content: 'Hello' }, 'key-1')
    })
    expect(result.current.contextMenu).not.toBeNull()

    act(() => {
      result.current.closeContextMenu()
    })
    expect(result.current.contextMenu).toBeNull()
  })
})
