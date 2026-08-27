import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './context/auth-context.js'
import AuthProvider from './context/AuthProvider.jsx'
import LandingPage from './pages/LandingPage.jsx'
import LoginPage from './pages/auth/LoginPage.jsx'
import OAuthCallbackPage from './pages/auth/OAuthCallbackPage.jsx'
import RegisterPage from './pages/auth/RegisterPage.jsx'
import VerifyAccountPage from './pages/auth/VerifyAccountPage.jsx'

function PublicOnlyRoute({ children }) {
  const { user, isInitializing } = useAuth()

  if (isInitializing) {
    return (
      <div className="app-loading" role="status" aria-label="Đang kiểm tra phiên đăng nhập">
        <span className="app-loading__mark"><i /><i /></span>
        <p>Đang kết nối ChatWeb...</p>
      </div>
    )
  }

  return user ? <Navigate to="/" replace /> : children
}

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<PublicOnlyRoute><LoginPage /></PublicOnlyRoute>} />
        <Route path="/register" element={<PublicOnlyRoute><RegisterPage /></PublicOnlyRoute>} />
        <Route path="/verify-account" element={<VerifyAccountPage />} />
        <Route path="/oauth2/redirect" element={<OAuthCallbackPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}

export default App
