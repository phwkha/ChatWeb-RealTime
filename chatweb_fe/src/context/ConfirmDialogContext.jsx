import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import ChatIcon from '../components/chat/ChatIcon.jsx'
import '../styles/confirm-dialog.css'

export const ConfirmDialogContext = createContext(null)

export function useConfirm() {
  const context = useContext(ConfirmDialogContext)
  if (!context) {
    return useCallback(() => Promise.resolve(false), [])
  }
  return context.confirm
}

export function ConfirmDialogProvider({ children }) {
  const [dialogState, setDialogState] = useState(null)
  const resolverRef = useRef(null)
  const cancelButtonRef = useRef(null)
  const confirmButtonRef = useRef(null)

  const handleCancel = useCallback(() => {
    resolverRef.current?.(false)
    resolverRef.current = null
    setDialogState(null)
  }, [])

  const handleConfirm = useCallback(() => {
    resolverRef.current?.(true)
    resolverRef.current = null
    setDialogState(null)
  }, [])

  const confirm = useCallback((options = {}) => {
    resolverRef.current?.(false)
    return new Promise((resolve) => {
      resolverRef.current = resolve
      setDialogState({
        title: options.title || 'Xác nhận thao tác',
        message: options.message || '',
        detail: options.detail || '',
        confirmText: options.confirmText || 'Xác nhận',
        cancelText: options.cancelText || 'Hủy',
        tone: options.tone || 'danger',
        icon: options.icon || (options.tone === 'primary' ? 'shield' : 'trash'),
      })
    })
  }, [])

  useEffect(() => {
    return () => {
      resolverRef.current?.(false)
      resolverRef.current = null
    }
  }, [])

  useEffect(() => {
    if (!dialogState) return

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        event.stopPropagation()
        handleCancel()
      }
    }

    window.addEventListener('keydown', handleKeyDown, true)

    const timer = setTimeout(() => {
      if (dialogState.tone === 'primary') {
        confirmButtonRef.current?.focus()
      } else {
        cancelButtonRef.current?.focus()
      }
    }, 0)

    return () => {
      clearTimeout(timer)
      window.removeEventListener('keydown', handleKeyDown, true)
    }
  }, [dialogState, handleCancel])

  return (
    <ConfirmDialogContext.Provider value={{ confirm }}>
      {children}
      {dialogState && (
        <div
          className="cw-confirm-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              event.preventDefault()
              event.stopPropagation()
              handleCancel()
            }
          }}
        >
          <section
            className="cw-confirm-dialog"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="cw-confirm-title"
            aria-describedby={dialogState.message ? 'cw-confirm-desc' : undefined}
          >
            <span className={`cw-confirm-dialog__icon${dialogState.tone === 'danger' ? ' is-danger' : ''}`}>
              <ChatIcon name={dialogState.icon} size={22} />
            </span>
            <h2 id="cw-confirm-title">{dialogState.title}</h2>
            {dialogState.message && <p id="cw-confirm-desc">{dialogState.message}</p>}
            {dialogState.detail && <strong>{dialogState.detail}</strong>}
            <div className="cw-confirm-dialog__actions">
              <button
                ref={cancelButtonRef}
                type="button"
                onClick={handleCancel}
              >
                {dialogState.cancelText}
              </button>
              <button
                ref={confirmButtonRef}
                className={dialogState.tone === 'danger' ? 'is-danger' : 'is-primary'}
                type="button"
                onClick={handleConfirm}
              >
                {dialogState.confirmText}
              </button>
            </div>
          </section>
        </div>
      )}
    </ConfirmDialogContext.Provider>
  )
}

export default ConfirmDialogProvider
