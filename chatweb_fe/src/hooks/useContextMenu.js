import { useCallback, useEffect, useState } from 'react'
import { isMessageDeleted } from '../components/chat/chatUtils.js'

export function useContextMenu() {
  const [contextMenu, setContextMenu] = useState(null)

  const openContextMenu = useCallback((event, message, messageKey) => {
    event.preventDefault()
    if (!message?.id || isMessageDeleted(message)) {
      setContextMenu(null)
      return
    }
    const menuWidth = 236
    const menuHeight = 220
    setContextMenu({
      message,
      messageKey,
      x: Math.max(8, Math.min(event.clientX, window.innerWidth - menuWidth - 8)),
      y: Math.max(8, Math.min(event.clientY, window.innerHeight - menuHeight - 8)),
    })
  }, [])

  const closeContextMenu = useCallback(() => {
    setContextMenu(null)
  }, [])

  useEffect(() => {
    if (!contextMenu) return undefined
    const closeMenu = (event) => {
      if (!event.target.closest('.message-context-menu')) {
        setContextMenu(null)
      }
    }
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') {
        setContextMenu(null)
      }
    }
    document.addEventListener('pointerdown', closeMenu)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeMenu)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [contextMenu])

  return {
    contextMenu,
    openContextMenu,
    closeContextMenu,
    setContextMenu,
  }
}

export default useContextMenu
