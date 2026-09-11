import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import AuthShell from '../../components/auth/AuthShell.jsx'
import GoogleButton from '../../components/auth/GoogleButton.jsx'
import { useAuth } from '../../context/auth-context.js'
import { getErrorMessage } from '../../services/apiClient.js'
import { isAdminUser } from '../../services/authorization.js'

function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ username: '', password: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(() => location.state?.error
    ? getErrorMessage({ message: location.state.error }, 'Đăng nhập không thành công. Vui lòng thử lại.')
    : '')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleChange = (event) => {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
    setFieldErrors((current) => ({ ...current, [event.target.name]: '' }))
    setError('')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    const nextFieldErrors = {}
    if (!form.username.trim()) nextFieldErrors.username = 'Vui lòng nhập tên đăng nhập.'
    if (!form.password) nextFieldErrors.password = 'Vui lòng nhập mật khẩu.'
    else if (form.password.length < 6) nextFieldErrors.password = 'Mật khẩu phải có ít nhất 6 ký tự.'
    if (Object.keys(nextFieldErrors).length) {
      setFieldErrors(nextFieldErrors)
      return
    }
    setFieldErrors({})
    setIsSubmitting(true)

    try {
      const response = await login({ username: form.username.trim(), password: form.password })
      navigate(isAdminUser(response?.data) ? '/admin' : '/chat', { replace: true })
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Đăng nhập không thành công. Vui lòng thử lại.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthShell
      eyebrow="Chào mừng trở lại"
      title={<>Tiếp tục những<br />câu chuyện <em>đang chờ.</em></>}
      description="Đăng nhập để kết nối lại với bạn bè và không bỏ lỡ bất kỳ khoảnh khắc nào."
      variant="login"
    >
      <div className="auth-form-heading">
        <span className="auth-step">Đăng nhập</span>
        <h2>Chào bạn trở lại!</h2>
        <p>Nhập thông tin tài khoản ChatWeb của bạn.</p>
      </div>

      {location.state?.message && <div className="form-alert form-alert--success"><span>✓</span>{location.state.message}</div>}
      {error && <div className="form-alert form-alert--error" role="alert"><span>!</span>{error}</div>}

      <form className="auth-form" noValidate onSubmit={handleSubmit}>
        <label className="field-group">
          <span>Tên đăng nhập</span>
          <span className={`field-control${fieldErrors.username ? ' field-control--error' : ''}`}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4" /><path d="M4.5 21a7.5 7.5 0 0 1 15 0" /></svg>
            <input
              autoComplete="username"
              name="username"
              placeholder="Nhập tên đăng nhập"
              aria-invalid={Boolean(fieldErrors.username)}
              value={form.username}
              onChange={handleChange}
            />
          </span>
          {fieldErrors.username && <small className="field-error">{fieldErrors.username}</small>}
        </label>

        <label className="field-group">
          <span className="field-label-row"><span>Mật khẩu</span><Link className="auth-muted-action" to="/forgot-password">Quên mật khẩu?</Link></span>
          <span className={`field-control${fieldErrors.password ? ' field-control--error' : ''}`}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="10" width="16" height="11" rx="3" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></svg>
            <input
              autoComplete="current-password"
              minLength="6"
              name="password"
              placeholder="Nhập mật khẩu"
              aria-invalid={Boolean(fieldErrors.password)}
              type={showPassword ? 'text' : 'password'}
              value={form.password}
              onChange={handleChange}
            />
            <button className="password-toggle" type="button" aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} onClick={() => setShowPassword((current) => !current)}>
              {showPassword ? 'Ẩn' : 'Hiện'}
            </button>
          </span>
          {fieldErrors.password && <small className="field-error">{fieldErrors.password}</small>}
        </label>

        <button className="auth-submit" type="submit" disabled={isSubmitting}>
          {isSubmitting ? <><span className="button-spinner" /> Đang đăng nhập...</> : <>Đăng nhập <span>→</span></>}
        </button>
      </form>

      <div className="auth-divider"><span>hoặc</span></div>
      <GoogleButton label="Đăng nhập với Google" />
      <p className="auth-switch">Chưa có tài khoản? <Link to="/register">Đăng ký miễn phí</Link></p>
      <p className="auth-privacy">Bằng cách tiếp tục, bạn đồng ý với <a href="#terms">Điều khoản</a> và <a href="#privacy">Chính sách riêng tư</a>.</p>
    </AuthShell>
  )
}

export default LoginPage
