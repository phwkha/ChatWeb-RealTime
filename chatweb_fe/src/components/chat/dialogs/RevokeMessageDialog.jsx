import React from 'react'
import ChatIcon from '../ChatIcon.jsx'

export const RevokeMessageDialog = React.memo(function RevokeMessageDialog({
  message,
  pending = false,
  t,
  onClose,
  onConfirm,
}) {
  if (!message) return null

  return (
    <div
      className="dialog-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && !pending && onClose()}
    >
      <section
        className="conversation-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="revoke-message-title"
      >
        <span className="conversation-dialog__icon is-danger">
          <ChatIcon name="trash" size={22} />
        </span>
        <h2 id="revoke-message-title">{t('revokeMessageTitle')}</h2>
        <p>{t('revokeMessageBody')}</p>
        <div className="revoke-message-preview">
          {message.content || message.fileName || t('sharedImage')}
        </div>
        <div className="conversation-dialog__actions">
          <button type="button" disabled={pending} onClick={onClose}>
            {t('cancel')}
          </button>
          <button
            className="is-danger"
            type="button"
            disabled={pending}
            onClick={onConfirm}
          >
            {pending ? t('revokingMessage') : t('revokeMessage')}
          </button>
        </div>
      </section>
    </div>
  )
})

export default RevokeMessageDialog
