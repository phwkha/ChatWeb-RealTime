import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import Brand from '../../components/Brand.jsx'
import { useAuth } from '../../context/auth-context.js'
import '../../styles/auth.css'

function VerifyAccountPage() {
  const { resendOtp, verifyAccount } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [email, setEmail] = useState(location.state?.email || '')
  const [otp, setOtp] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState(location.state?.message || 'Mã OTP đã được gửi tới email của bạn.')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [countdown, setCountdown] = useState(60)

  useEffect(() => {
    if (countdown <= 0) return undefined
    const timer = window.setInterval(() => setCountdown((current) => current - 1), 1000)
    return () => window.clearInterval(timer)
  }, [countdown])

  const handleOtpChange = (event) => {
    setOtp(event.target.value.replace(/\D/g, '').slice(0, 6))
    setError('')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (!email.trim()) {
      setError('Vui lòng nhập email đã dùng để đăng ký.')
      return
    }
    setIsSubmitting(true)
    setError('')
    try {
      const response = await verifyAccount({ email: email.trim(), otp })
      navigate('/login', {
        replace: true,
        state: { message: response?.message || 'Xác minh thành công. Bạn có thể đăng nhập ngay.' },
      })
    } catch (requestError) {
      setError(requestError.message || 'Mã OTP không hợp lệ hoặc đã hết hạn.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleResend = async () => {
    if (countdown > 0 || !email.trim()) return
    setError('')
    try {
      const response = await resendOtp(email.trim())
      setMessage(response?.message || 'Đã gửi lại mã OTP.')
      setCountdown(60)
    } catch (requestError) {
      setError(requestError.message || 'Chưa thể gửi lại mã OTP.')
    }
  }

  return (
    <main className="verify-page">
      <div className="verify-page__top"><Brand /><Link to="/login">Đăng nhập</Link></div>
      <section className="verify-card">
        <div className="verify-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="3" /><path d="m4 7 8 6 8-6" /></svg>
          <span>1</span>
        </div>
        <span className="auth-step">Xác minh email</span>
        <h1>Kiểm tra hộp thư của bạn</h1>
        <p>{message}</p>

        {error && <div className="form-alert form-alert--error" role="alert"><span>!</span>{error}</div>}

        <form className="verify-form" onSubmit={handleSubmit}>
          <label className="field-group">
            <span>Email đăng ký</span>
            <span className="field-control">
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="3" /><path d="m4 7 8 6 8-6" /></svg>
              <input name="email" type="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="ban@email.com" />
            </span>
          </label>
          <label className="otp-group">
            <span>Mã xác minh gồm 6 số</span>
            <input autoFocus autoComplete="one-time-code" inputMode="numeric" maxLength="6" pattern="[0-9]{6}" required value={otp} onChange={handleOtpChange} placeholder="000000" />
          </label>
          <button className="auth-submit" type="submit" disabled={isSubmitting || otp.length !== 6}>
            {isSubmitting ? <><span className="button-spinner" /> Đang xác minh...</> : <>Xác minh tài khoản <span>→</span></>}
          </button>
        </form>

        <p className="verify-resend">
          Chưa nhận được mã?{' '}
          <button type="button" disabled={countdown > 0} onClick={handleResend}>
            {countdown > 0 ? `Gửi lại sau ${countdown}s` : 'Gửi lại mã'}
          </button>
        </p>
        <small className="verify-note">Mã xác minh có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này với bất kỳ ai.</small>
      </section>
    </main>
  )
}

export default VerifyAccountPage
