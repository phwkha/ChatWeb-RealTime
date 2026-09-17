import React from 'react'
import ChatIcon from '../ChatIcon.jsx'

export const ConfirmActionDialog = React.memo(function ConfirmActionDialog({
  action,
  selectedUser,
  pending = false,
  t,
  onClose,
  onConfirm,
}) {
  if (!action || !selectedUser) return null

  const isBlock = action === 'block'

  return (
    <div
      className="dialog-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <section
        className="conversation-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="conversation-confirm-title"
      >
        <span className={`conversation-dialog__icon${isBlock ? ' is-danger' : ''}`}>
          <ChatIcon name={isBlock ? 'block' : 'trash'} size={22} />
        </span>
        <h2 id="conversation-confirm-title">
          {t(isBlock ? 'blockConfirmTitle' : 'unfriendConfirmTitle')}
        </h2>
        <p>{t(isBlock ? 'blockConfirmBody' : 'unfriendConfirmBody')}</p>
        <strong>@{selectedUser.username}</strong>
        <div className="conversation-dialog__actions">
          <button type="button" onClick={onClose}>
            {t('cancel')}
          </button>
          <button
            className="is-danger"
            type="button"
            disabled={pending}
            onClick={onConfirm}
          >
            {t('confirm')}
          </button>
        </div>
      </section>
    </div>
  )
})

export default ConfirmActionDialog
