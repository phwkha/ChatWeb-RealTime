import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthShell from '../../components/auth/AuthShell.jsx'
import GoogleButton from '../../components/auth/GoogleButton.jsx'
import { useAuth } from '../../context/auth-context.js'
import { getErrorMessage } from '../../services/apiClient.js'

function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', password: '', confirmPassword: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const passwordScore = useMemo(() => [
    form.password.length >= 8,
    /[A-Z]/.test(form.password) && /[a-z]/.test(form.password),
    /\d/.test(form.password),
    /[^A-Za-z0-9]/.test(form.password),
  ].filter(Boolean).length, [form.password])

  const handleChange = (event) => {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
    setFieldErrors((current) => ({ ...current, [event.target.name]: '' }))
    setError('')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    setFieldErrors({})

    const nextFieldErrors = {}
    if (!form.username.trim()) nextFieldErrors.username = 'Vui lòng nhập tên đăng nhập.'
    if (!form.email.trim()) nextFieldErrors.email = 'Vui lòng nhập email.'
    else if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) nextFieldErrors.email = 'Email chưa đúng định dạng.'
    if (!form.password) nextFieldErrors.password = 'Vui lòng nhập mật khẩu.'
    else if (form.password.length < 8) nextFieldErrors.password = 'Mật khẩu phải có ít nhất 8 ký tự.'
    if (!form.confirmPassword) nextFieldErrors.confirmPassword = 'Vui lòng xác nhận mật khẩu.'
    else if (form.password !== form.confirmPassword) nextFieldErrors.confirmPassword = 'Mật khẩu xác nhận chưa khớp.'
    if (!termsAccepted) nextFieldErrors.terms = 'Vui lòng đồng ý với điều khoản để tiếp tục.'
    if (Object.keys(nextFieldErrors).length) {
      setFieldErrors(nextFieldErrors)
      return
    }

    setIsSubmitting(true)
    try {
      const response = await register({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
      })
      navigate('/verify-account', {
        state: { email: form.email.trim(), message: response?.message },
        replace: true,
      })
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Đăng ký không thành công. Vui lòng thử lại.'))
      if (requestError.data && typeof requestError.data === 'object') setFieldErrors(requestError.data)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthShell
      eyebrow="Bắt đầu hành trình"
      title={<>Một tài khoản.<br /><em>Vạn kết nối.</em></>}
      description="Tạo không gian trò chuyện của riêng bạn và gần hơn với những người quan trọng."
      variant="register"
    >
      <div className="auth-form-heading">
        <span className="auth-step">Tạo tài khoản</span>
        <h2>Tham gia ChatWeb</h2>
        <p>Chỉ mất một phút để bắt đầu kết nối.</p>
      </div>

      {error && <div className="form-alert form-alert--error" role="alert"><span>!</span>{error}</div>}

      <GoogleButton label="Đăng ký với Google" />
      <div className="auth-divider"><span>hoặc đăng ký bằng email</span></div>

      <form className="auth-form auth-form--compact" noValidate onSubmit={handleSubmit}>
        <div className="field-row">
          <label className="field-group">
            <span>Tên đăng nhập</span>
            <span className={`field-control${fieldErrors.username ? ' field-control--error' : ''}`}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4" /><path d="M4.5 21a7.5 7.5 0 0 1 15 0" /></svg>
              <input autoComplete="username" name="username" placeholder="chatwithme" aria-invalid={Boolean(fieldErrors.username)} value={form.username} onChange={handleChange} />
            </span>
            {fieldErrors.username && <small className="field-error">{fieldErrors.username}</small>}
          </label>

          <label className="field-group">
            <span>Email</span>
            <span className={`field-control${fieldErrors.email ? ' field-control--error' : ''}`}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="3" /><path d="m4 7 8 6 8-6" /></svg>
              <input autoComplete="email" name="email" placeholder="ban@email.com" aria-invalid={Boolean(fieldErrors.email)} value={form.email} onChange={handleChange} />
            </span>
            {fieldErrors.email && <small className="field-error">{fieldErrors.email}</small>}
          </label>
        </div>

        <label className="field-group">
          <span>Mật khẩu</span>
          <span className={`field-control${fieldErrors.password ? ' field-control--error' : ''}`}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="10" width="16" height="11" rx="3" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></svg>
            <input autoComplete="new-password" name="password" placeholder="Tối thiểu 8 ký tự" aria-invalid={Boolean(fieldErrors.password)} type={showPassword ? 'text' : 'password'} value={form.password} onChange={handleChange} />
            <button className="password-toggle" type="button" onClick={() => setShowPassword((current) => !current)}>{showPassword ? 'Ẩn' : 'Hiện'}</button>
          </span>
          <span className="password-strength" aria-label={`Độ mạnh mật khẩu ${passwordScore} trên 4`}>
            {[1, 2, 3, 4].map((level) => <i key={level} className={level <= passwordScore ? 'is-active' : ''} />)}
            <small>{passwordScore < 2 ? 'Mật khẩu yếu' : passwordScore < 4 ? 'Mật khẩu khá' : 'Mật khẩu mạnh'}</small>
          </span>
          {fieldErrors.password && <small className="field-error">{fieldErrors.password}</small>}
        </label>

        <label className="field-group">
          <span>Xác nhận mật khẩu</span>
          <span className={`field-control${fieldErrors.confirmPassword ? ' field-control--error' : ''}`}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="10" width="16" height="11" rx="3" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></svg>
            <input autoComplete="new-password" name="confirmPassword" placeholder="Nhập lại mật khẩu" aria-invalid={Boolean(fieldErrors.confirmPassword)} type={showPassword ? 'text' : 'password'} value={form.confirmPassword} onChange={handleChange} />
          </span>
          {fieldErrors.confirmPassword && <small className="field-error">{fieldErrors.confirmPassword}</small>}
        </label>

        <label className={`terms-check${fieldErrors.terms ? ' terms-check--error' : ''}`}>
          <input type="checkbox" checked={termsAccepted} onChange={(event) => { setTermsAccepted(event.target.checked); setFieldErrors((current) => ({ ...current, terms: '' })) }} />
          <span>Tôi đồng ý với <a href="#terms">Điều khoản sử dụng</a> và <a href="#privacy">Chính sách riêng tư</a>.</span>
        </label>
        {fieldErrors.terms && <small className="field-error">{fieldErrors.terms}</small>}

        <button className="auth-submit" type="submit" disabled={isSubmitting}>
          {isSubmitting ? <><span className="button-spinner" /> Đang tạo tài khoản...</> : <>Tạo tài khoản <span>→</span></>}
        </button>
      </form>

      <p className="auth-switch">Đã có tài khoản? <Link to="/login">Đăng nhập ngay</Link></p>
    </AuthShell>
  )
}

export default RegisterPage
