import React, { useState } from 'react'
import ChatIcon from '../ChatIcon.jsx'
import { reportApi } from '../../../services/reportApi.js'
import { getErrorMessage } from '../../../services/apiClient.js'

export const ReportUserDialog = React.memo(function ReportUserDialog({
  isOpen,
  selectedUser,
  t,
  onClose,
  onSubmitSuccess,
}) {
  const [reportReason, setReportReason] = useState('SPAM')
  const [reportDetails, setReportDetails] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')

  if (!isOpen || !selectedUser) return null

  const handleClose = () => {
    if (submitting) return
    setReportReason('SPAM')
    setReportDetails('')
    setErrorMsg('')
    onClose()
  }

  const handleSubmit = async (event) => {
    event?.preventDefault?.()
    if (submitting) return
    setSubmitting(true)
    setErrorMsg('')
    try {
      await reportApi.createReport({
        reportedUsername: selectedUser.username,
        reason: reportReason,
        details: reportDetails.trim(),
      })
      setReportReason('SPAM')
      setReportDetails('')
      setErrorMsg('')
      onClose()
      if (onSubmitSuccess) {
        onSubmitSuccess(t('reportSuccess'))
      }
    } catch (err) {
      setErrorMsg(getErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
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
          <select
            value={reportReason}
            disabled={submitting}
            onChange={(event) => setReportReason(event.target.value)}
          >
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
            disabled={submitting}
            maxLength={1000}
            onChange={(event) => setReportDetails(event.target.value)}
          />
        </label>
        {errorMsg && (
          <div
            className="report-dialog__notice is-error"
            style={{ color: '#dc2626', background: '#fee2e2' }}
          >
            {errorMsg}
          </div>
        )}
        <div className="conversation-dialog__actions">
          <button type="button" onClick={handleClose} disabled={submitting}>
            {t('cancel')}
          </button>
          <button
            className="is-danger"
            type="button"
            disabled={submitting}
            onClick={handleSubmit}
          >
            {submitting ? t('submittingReport') : t('submitReport')}
          </button>
        </div>
      </section>
    </div>
  )
})

export default ReportUserDialog
