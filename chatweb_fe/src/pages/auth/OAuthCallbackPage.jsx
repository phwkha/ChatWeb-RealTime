import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import Brand from '../../components/Brand.jsx'
import { useAuth } from '../../context/auth-context.js'
import '../../styles/auth.css'

function OAuthCallbackPage() {
  const { refreshUser } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const oauthError = searchParams.get('error') || ''
  const [error, setError] = useState(oauthError)
  const hasHandledCallback = useRef(false)

  useEffect(() => {
    if (hasHandledCallback.current) return
    hasHandledCallback.current = true

    if (oauthError) {
      window.setTimeout(() => navigate('/login', { replace: true, state: { error: oauthError } }), 1500)
      return
    }

    refreshUser().then((currentUser) => {
      if (currentUser) navigate('/', { replace: true })
      else {
        const message = 'Google đã xác thực nhưng ChatWeb chưa nhận được phiên đăng nhập.'
        setError(message)
        window.setTimeout(() => navigate('/login', { replace: true, state: { error: message } }), 1800)
      }
    })
  }, [navigate, oauthError, refreshUser])

  return (
    <main className="oauth-page">
      <Brand />
      <div className={`oauth-card${error ? ' oauth-card--error' : ''}`} role="status">
        <div className="oauth-loader">{error ? '!' : <><i /><i /><i /></>}</div>
        <h1>{error ? 'Chưa thể đăng nhập' : 'Đang hoàn tất đăng nhập'}</h1>
        <p>{error || 'ChatWeb đang kết nối tài khoản Google của bạn...'}</p>
      </div>
    </main>
  )
}

export default OAuthCallbackPage
