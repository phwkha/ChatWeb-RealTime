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
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage.jsx'
import SettingsPage from './pages/SettingsPage.jsx'
import AdminPage from './pages/AdminPage.jsx'
import { isAdminUser } from './services/authorization.js'

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

  return user ? <Navigate to={isAdminUser(user) ? '/admin' : '/chat'} replace /> : children
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
          <Route path="/" element={<Navigate to="/home" replace />} />
          <Route path="/home" element={<LandingPage />} />
          <Route path="/chat" element={<ProtectedRoute><ChatPage /></ProtectedRoute>} />
          <Route path="/settings" element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
          <Route path="/admin" element={<ProtectedRoute><AdminPage /></ProtectedRoute>} />
          <Route path="/login" element={<PublicOnlyRoute><LoginPage /></PublicOnlyRoute>} />
          <Route path="/register" element={<PublicOnlyRoute><RegisterPage /></PublicOnlyRoute>} />
          <Route path="/forgot-password" element={<PublicOnlyRoute><ForgotPasswordPage /></PublicOnlyRoute>} />
          <Route path="/verify-account" element={<VerifyAccountPage />} />
          <Route path="/oauth2/redirect" element={<OAuthCallbackPage />} />
          <Route path="*" element={<Navigate to="/home" replace />} />
        </Routes>
      </LanguageProvider>
    </AuthProvider>
  )
}

export default App
