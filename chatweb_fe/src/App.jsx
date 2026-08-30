import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './context/auth-context.js'
import AuthProvider from './context/AuthProvider.jsx'
import { LanguageProvider } from './context/LanguageProvider.jsx'
import LandingPage from './pages/LandingPage.jsx'
import ChatPage from './pages/ChatPage.jsx'
import LoginPage from './pages/auth/LoginPage.jsx'
import OAuthCallbackPage from './pages/auth/OAuthCallbackPage.jsx'
import RegisterPage from './pages/auth/RegisterPage.jsx'
import VerifyAccountPage from './pages/auth/VerifyAccountPage.jsx'

function PublicOnlyRoute({ children }) {
  const { user, isInitializing } = useAuth()

  if (isInitializing) {
    return (
      <div className="app-loading" role="status" aria-label="Đang kiểm tra phiên đăng nhập">
        <img className="app-loading__logo" src="/logo_chatweb.png" alt="" aria-hidden="true" />
        <p>Đang kết nối ChatWeb...</p>
      </div>
    )
  }

  return user ? <Navigate to="/chat" replace /> : children
}

function ProtectedRoute({ children }) {
  const { user, isInitializing } = useAuth()

  if (isInitializing) {
    return (
      <div className="app-loading" role="status" aria-label="Đang kiểm tra phiên đăng nhập">
        <img className="app-loading__logo" src="/logo_chatweb.png" alt="" aria-hidden="true" />
        <p>Đang kết nối ChatWeb...</p>
      </div>
    )
  }

  return user ? children : <Navigate to="/login" replace state={{ error: 'Vui lòng đăng nhập để mở trò chuyện.' }} />
}

function App() {
  return (
    <AuthProvider>
      <LanguageProvider>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/chat" element={<ProtectedRoute><ChatPage /></ProtectedRoute>} />
          <Route path="/login" element={<PublicOnlyRoute><LoginPage /></PublicOnlyRoute>} />
          <Route path="/register" element={<PublicOnlyRoute><RegisterPage /></PublicOnlyRoute>} />
          <Route path="/verify-account" element={<VerifyAccountPage />} />
          <Route path="/oauth2/redirect" element={<OAuthCallbackPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </LanguageProvider>
    </AuthProvider>
  )
}

export default App
