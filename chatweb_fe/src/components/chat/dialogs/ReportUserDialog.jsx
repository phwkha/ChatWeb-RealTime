import React, { useState } from 'react'
import ChatIcon from '../ChatIcon.jsx'

export const ReportUserDialog = React.memo(function ReportUserDialog({
  isOpen,
  selectedUser,
  t,
  onClose,
}) {
  const [reportReason, setReportReason] = useState('SPAM')
  const [reportDetails, setReportDetails] = useState('')

  if (!isOpen || !selectedUser) return null

  const handleClose = () => {
    setReportReason('SPAM')
    setReportDetails('')
    onClose()
  }

  return (
    <div
      className="dialog-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && handleClose()}
    >
      <section
        className="conversation-dialog report-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-dialog-title"
      >
        <span className="conversation-dialog__icon is-danger">
          <ChatIcon name="flag" size={22} />
        </span>
        <h2 id="report-dialog-title">{t('reportTitle')}</h2>
        <p>@{selectedUser.username}</p>
        <label>
          <span>{t('reportReason')}</span>
          <select value={reportReason} onChange={(event) => setReportReason(event.target.value)}>
            <option value="SPAM">{t('reportSpam')}</option>
            <option value="HARASSMENT">{t('reportHarassment')}</option>
            <option value="INAPPROPRIATE">{t('reportInappropriate')}</option>
            <option value="IMPERSONATION">{t('reportImpersonation')}</option>
            <option value="OTHER">{t('reportOther')}</option>
          </select>
        </label>
        <label>
          <span>{t('reportDetails')}</span>
          <textarea
            rows="4"
            value={reportDetails}
            onChange={(event) => setReportDetails(event.target.value)}
          />
        </label>
        <div className="report-dialog__notice">{t('reportUnavailable')}</div>
        <div className="conversation-dialog__actions">
          <button type="button" onClick={handleClose}>
            {t('cancel')}
          </button>
          <button className="is-danger" type="button" disabled>
            {t('submitReport')}
          </button>
        </div>
      </section>
    </div>
  )
})

export default ReportUserDialog
