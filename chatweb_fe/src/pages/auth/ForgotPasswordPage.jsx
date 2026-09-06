import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthShell from '../../components/auth/AuthShell.jsx'
import { apiRequest } from '../../services/apiClient.js'

function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState('request')
  const [form, setForm] = useState({ email: '', otp: '', newPassword: '', confirmPassword: '' })
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const update = (event) => {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: name === 'otp' ? value.replace(/\D/g, '').slice(0, 6) : value }))
    setError('')
  }

  const requestOtp = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const response = await apiRequest('/api/auth/forgot-password', {
        method: 'POST', body: { email: form.email.trim() }, skipRefresh: true,
      })
      setMessage(response?.message || 'Mã xác nhận đã được gửi đến email của bạn.')
      setStep('reset')
    } catch (requestError) {
      setError(requestError.message || 'Không thể gửi mã xác nhận.')
    } finally {
      setSubmitting(false)
    }
  }

  const resetPassword = async (event) => {
    event.preventDefault()
    if (form.newPassword !== form.confirmPassword) {
      setError('Mật khẩu xác nhận chưa khớp.')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      const response = await apiRequest('/api/auth/reset-password', {
        method: 'POST',
        body: { email: form.email.trim(), otp: form.otp, newPassword: form.newPassword },
        skipRefresh: true,
      })
      navigate('/login', { replace: true, state: { message: response?.message || 'Đổi mật khẩu thành công.' } })
    } catch (requestError) {
      setError(requestError.message || 'Không thể đặt lại mật khẩu.')
    } finally {
      setSubmitting(false)
    }
  }

  const resend = async () => {
    setError('')
    try {
      const response = await apiRequest(`/api/auth/resend-forgot-password?email=${encodeURIComponent(form.email.trim())}`, {
        method: 'POST', skipRefresh: true,
      })
      setMessage(response?.message || 'Đã gửi lại mã xác nhận.')
    } catch (requestError) {
      setError(requestError.message || 'Không thể gửi lại mã.')
    }
  }

  return (
    <AuthShell
      eyebrow="Khôi phục tài khoản"
      title={<>Trở lại cuộc trò chuyện<br /><em>chỉ trong ít phút.</em></>}
      description="Xác minh email và đặt một mật khẩu mới an toàn cho tài khoản ChatWeb."
      variant="login"
    >
      <div className="auth-form-heading">
        <span className="auth-step">{step === 'request' ? 'Bước 1 · Email' : 'Bước 2 · Xác nhận'}</span>
        <h2>Quên mật khẩu</h2>
        <p>{step === 'request' ? 'Nhập email đã đăng ký để nhận mã OTP.' : message}</p>
      </div>
      {error && <div className="form-alert form-alert--error" role="alert"><span>!</span>{error}</div>}
      {message && step === 'request' && <div className="form-alert form-alert--success"><span>✓</span>{message}</div>}

      {step === 'request' ? (
        <form className="auth-form" onSubmit={requestOtp}>
          <label className="field-group"><span>Email</span><span className="field-control"><input name="email" type="email" autoComplete="email" required value={form.email} onChange={update} placeholder="ban@email.com" /></span></label>
          <button className="auth-submit" type="submit" disabled={submitting}>{submitting ? 'Đang gửi...' : 'Gửi mã xác nhận →'}</button>
        </form>
      ) : (
        <form className="auth-form" onSubmit={resetPassword}>
          <label className="otp-group"><span>Mã OTP gồm 6 số</span><input name="otp" inputMode="numeric" pattern="[0-9]{6}" required value={form.otp} onChange={update} placeholder="000000" /></label>
          <label className="field-group"><span>Mật khẩu mới</span><span className="field-control"><input name="newPassword" type="password" minLength="8" autoComplete="new-password" required value={form.newPassword} onChange={update} /></span></label>
          <label className="field-group"><span>Xác nhận mật khẩu</span><span className="field-control"><input name="confirmPassword" type="password" minLength="8" autoComplete="new-password" required value={form.confirmPassword} onChange={update} /></span></label>
          <button className="auth-submit" type="submit" disabled={submitting || form.otp.length !== 6}>{submitting ? 'Đang cập nhật...' : 'Đặt lại mật khẩu →'}</button>
          <button className="auth-text-button" type="button" onClick={resend}>Gửi lại mã OTP</button>
        </form>
      )}
      <p className="auth-switch"><Link to="/login">← Quay lại đăng nhập</Link></p>
    </AuthShell>
  )
}

export default ForgotPasswordPage
