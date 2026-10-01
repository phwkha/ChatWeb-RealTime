import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import AppRail from '../components/chat/AppRail.jsx'
import ChatIcon from '../components/chat/ChatIcon.jsx'
import { useAuth } from '../context/auth-context.js'
import { useLanguage } from '../context/language-context.js'
import { accountApi } from '../services/accountApi.js'
import { getErrorMessage } from '../services/apiClient.js'
import { reportApi } from '../services/reportApi.js'
import '../styles/workspace.css'
import '../styles/chat.css'

const EMPTY_ADDRESS = {
  houseNumber: '', street: '', ward: '', district: '', city: '', country: 'Việt Nam', postalCode: '',
}
const SETTINGS_TABS = new Set(['profile', 'addresses', 'contact', 'security', 'reports'])

function addressPayload(address) {
  return Object.fromEntries(Object.keys(EMPTY_ADDRESS).map((key) => [key, address[key] || '']))
}

function formatReportDate(dateString, lang) {
  if (!dateString) return '—'
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return '—'
  const locale = lang === 'vi' ? 'vi-VN' : lang === 'ja' ? 'ja-JP' : 'en-US'
  return date.toLocaleString(locale)
}

function SettingsPage() {
  const { user, refreshUser, logoutEverywhere, deleteAccount } = useAuth()
  const { t, language } = useLanguage()
  const navigate = useNavigate()
  const isMountedRef = useRef(true)
  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])
  const [searchParams, setSearchParams] = useSearchParams()
  const [tab, setTab] = useState(() => {
    const requestedTab = searchParams.get('tab')
    return SETTINGS_TABS.has(requestedTab) ? requestedTab : 'profile'
  })

  useEffect(() => {
    const requestedTab = searchParams.get('tab')
    const nextTab = SETTINGS_TABS.has(requestedTab) ? requestedTab : 'profile'
    if (nextTab !== tab) {
      setTab(nextTab)
    }
  }, [searchParams, tab])

  const [profile, setProfile] = useState(null)
  const [profileForm, setProfileForm] = useState({ firstName: '', lastName: '', phone: '', birthday: '', gender: '' })
  const [addresses, setAddresses] = useState([])
  const [addressForm, setAddressForm] = useState(EMPTY_ADDRESS)
  const [editingAddressId, setEditingAddressId] = useState(null)
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [emailFlow, setEmailFlow] = useState({ newEmail: '', currentPassword: '', otp: '', pending: false })
  const [phoneFlow, setPhoneFlow] = useState({ newPhone: '', currentPassword: '', otp: '', pending: false })
  const [reports, setReports] = useState([])
  const [reportsPage, setReportsPage] = useState({ content: [], pageNo: 0, pageSize: 10, totalElements: 0, totalPages: 0, last: true })
  const [reportsLoading, setReportsLoading] = useState(false)
  const [reportsError, setReportsError] = useState(false)
  const [cancellingReportId, setCancellingReportId] = useState(null)
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState(null)

  const notify = useCallback((message, tone = 'success') => setNotice({ message, tone }), [])

  const selectTab = (nextTab) => {
    setTab(nextTab)
    setSearchParams({ tab: nextTab }, { replace: true })
  }

  const loadReports = useCallback(async (page = 0) => {
    setReportsLoading(true)
    setReportsError(false)
    try {
      const response = await reportApi.getMyReports(page, 10, 'desc')
      if (!isMountedRef.current) return
      const data = response?.data || {}
      const content = Array.isArray(data.content) ? data.content : []
      setReports(content)
      setReportsPage({
        content,
        pageNo: data.pageNo ?? page,
        pageSize: data.pageSize ?? 10,
        totalElements: data.totalElements ?? 0,
        totalPages: data.totalPages ?? 0,
        last: data.last ?? true,
      })
    } catch (error) {
      if (!isMountedRef.current) return
      setReportsError(true)
      notify(getErrorMessage(error, t('loadReportsFailed') || 'Không thể tải danh sách báo cáo.'), 'error')
    } finally {
      if (isMountedRef.current) {
        setReportsLoading(false)
      }
    }
  }, [notify, t])

  // oxlint-disable-next-line react/set-state-in-effect -- hydrates submitted reports on tab switch
  useEffect(() => {
    if (tab === 'reports') {
      void loadReports(0)
    }
  }, [tab, loadReports])

  const handleCancelReport = async (reportId) => {
    const confirmed = window.confirm(t('confirmCancelReport') || 'Bạn có chắc chắn muốn hủy báo cáo này?')
    if (!confirmed) return

    setCancellingReportId(reportId)
    try {
      const response = await reportApi.cancelReport(reportId)
      if (!isMountedRef.current) return
      notify(response?.message || t('reportCancelledSuccess') || 'Đã hủy báo cáo thành công.')
      const remaining = reports.filter((r) => String(r.id) !== String(reportId))
      const nextPage = (remaining.length === 0 && reportsPage.pageNo > 0)
        ? reportsPage.pageNo - 1
        : reportsPage.pageNo
      await loadReports(nextPage)
    } catch (error) {
      if (!isMountedRef.current) return
      notify(getErrorMessage(error, t('cancelReportFailed') || 'Không thể hủy báo cáo.'), 'error')
    } finally {
      if (isMountedRef.current) {
        setCancellingReportId(null)
      }
    }
  }

  const loadProfile = useCallback(async () => {
    try {
      const [profileResponse, addressResponse] = await Promise.all([
        accountApi.getProfile(), accountApi.getAddresses(),
      ])
      const data = profileResponse?.data || {}
      setProfile(data)
      setProfileForm({
        firstName: data.firstName || '', lastName: data.lastName || '', phone: data.phone || '',
        birthday: data.birthday || '', gender: data.gender || '',
      })
      setAddresses(addressResponse?.data || data.addresses || [])
    } catch (error) {
      notify(getErrorMessage(error, 'Không thể tải thông tin tài khoản.'), 'error')
    }
  }, [notify])

  // oxlint-disable-next-line react/set-state-in-effect -- the page hydrates account data from remote APIs.
  useEffect(() => { void loadProfile() }, [loadProfile])

  const run = async (name, action, successMessage) => {
    setBusy(name)
    setNotice(null)
    try {
      const response = await action()
      notify(response?.message || successMessage)
      return response
    } catch (error) {
      notify(getErrorMessage(error, 'Thao tác không thành công.'), 'error')
      return null
    } finally {
      setBusy('')
    }
  }

  const saveProfile = async (event) => {
    event.preventDefault()
    const body = { ...profileForm, phone: profileForm.phone || null, birthday: profileForm.birthday || null, gender: profileForm.gender || null }
    const response = await run('profile', () => accountApi.updateProfile(body), 'Đã cập nhật hồ sơ.')
    if (response) { await refreshUser(); await loadProfile() }
  }

  const uploadAvatar = async (event) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    const response = await run('avatar', () => accountApi.updateAvatar(file), 'Đã cập nhật ảnh đại diện.')
    if (response) { await refreshUser(); await loadProfile() }
  }

  const saveAddress = async (event) => {
    event.preventDefault()
    const response = await run('address', () => editingAddressId
      ? accountApi.updateAddress(editingAddressId, addressPayload(addressForm))
      : accountApi.addAddress(addressPayload(addressForm)), editingAddressId ? 'Đã cập nhật địa chỉ.' : 'Đã thêm địa chỉ.')
    if (response) { setAddressForm(EMPTY_ADDRESS); setEditingAddressId(null); await loadProfile() }
  }

  const editAddress = async (id) => {
    const response = await run('address-detail', () => accountApi.getAddress(id), 'Đã tải địa chỉ.')
    if (response?.data) { setAddressForm(addressPayload(response.data)); setEditingAddressId(id) }
  }

  const removeAddress = async (id) => {
    if (!window.confirm('Xóa địa chỉ này?')) return
    const response = await run('address-delete', () => accountApi.deleteAddress(id), 'Đã xóa địa chỉ.')
    if (response) await loadProfile()
  }

  const changePassword = async (event) => {
    event.preventDefault()
    if (passwordForm.newPassword !== passwordForm.confirmPassword) return notify('Mật khẩu xác nhận chưa khớp.', 'error')
    const response = await run('password', () => accountApi.changePassword({
      currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword,
    }), 'Đã đổi mật khẩu.')
    if (response) setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
  }

  const startEmailChange = async (event) => {
    event.preventDefault()
    const response = await run('email-start', () => accountApi.initiateEmailChange({
      newEmail: emailFlow.newEmail.trim(), currentPassword: emailFlow.currentPassword,
    }), 'Đã gửi OTP đến email mới.')
    if (response) setEmailFlow((current) => ({ ...current, pending: true }))
  }

  const verifyEmail = async (event) => {
    event.preventDefault()
    const response = await run('email-verify', () => accountApi.verifyEmailChange({
      email: emailFlow.newEmail.trim(), otp: emailFlow.otp,
    }), 'Đã đổi email.')
    if (response) { setEmailFlow({ newEmail: '', currentPassword: '', otp: '', pending: false }); await refreshUser(); await loadProfile() }
  }

  const startPhoneChange = async (event) => {
    event.preventDefault()
    const response = await run('phone-start', () => accountApi.initiatePhoneChange({
      newPhone: phoneFlow.newPhone.trim(), currentPassword: phoneFlow.currentPassword,
    }), 'Đã gửi OTP đổi số điện thoại.')
    if (response) setPhoneFlow((current) => ({ ...current, pending: true }))
  }

  const verifyPhone = async (event) => {
    event.preventDefault()
    const response = await run('phone-verify', () => accountApi.verifyPhoneChange({
      email: profile?.email || user.email, otp: phoneFlow.otp,
    }), 'Đã đổi số điện thoại.')
    if (response) { setPhoneFlow({ newPhone: '', currentPassword: '', otp: '', pending: false }); await refreshUser(); await loadProfile() }
  }

  const endAllSessions = async () => {
    if (!window.confirm('Đăng xuất tài khoản khỏi tất cả thiết bị?')) return
    await logoutEverywhere().catch(() => {})
    navigate('/login', { replace: true, state: { message: 'Đã đăng xuất khỏi tất cả thiết bị.' } })
  }

  const removeAccount = async () => {
    if (!window.confirm('Xóa tài khoản là thao tác không thể hoàn tác. Bạn chắc chắn chứ?')) return
    try {
      await deleteAccount()
      navigate('/home', { replace: true })
    } catch (error) {
      notify(getErrorMessage(error, 'Không thể xóa tài khoản.'), 'error')
    }
  }

  return (
    <main className="settings-app-shell">
      <AppRail activeSection="settings" online onSelectSettings={selectTab} />
      <section className="workspace-page workspace-page--embedded">
      <header className="workspace-topbar workspace-topbar--embedded"><nav><Link to="/chat">← Trò chuyện</Link>{String(user.role || '').includes('ADMIN') && <Link to="/admin">Quản trị</Link>}</nav></header>
      <div className="workspace-layout">
        <aside className="workspace-sidebar">
          <div className="workspace-user"><span>{(user.firstName?.[0] || user.username?.[0] || 'U').toUpperCase()}</span><div><strong>{user.firstName || user.username}</strong><small>@{user.username}</small></div></div>
          <h1>Cài đặt</h1>
          {[
            ['profile', t('profile') || 'Hồ sơ'],
            ['addresses', t('addressSettings') || 'Địa chỉ'],
            ['contact', t('contactSettings') || 'Email & điện thoại'],
            ['security', t('securitySettings') || 'Bảo mật'],
            ['reports', t('myReports') || 'Báo cáo của tôi'],
          ].map(([value, label]) => (
            <button key={value} className={tab === value ? 'is-active' : ''} type="button" onClick={() => selectTab(value)}>{label}</button>
          ))}
        </aside>

        <section className="workspace-content">
          {notice && <div className={`workspace-notice is-${notice.tone}`} role="status">{notice.message}<button type="button" onClick={() => setNotice(null)}>×</button></div>}

          {tab === 'profile' && <div className="workspace-section"><header><small>TÀI KHOẢN</small><h2>Hồ sơ cá nhân</h2><p>Cập nhật thông tin hiển thị của bạn trên ChatWeb.</p></header>
            <div className="profile-avatar-row"><span>{profile?.avatar ? <img src={profile.avatar} alt="Ảnh đại diện" /> : (user.username?.[0] || 'U').toUpperCase()}</span><label className="workspace-button is-secondary">{busy === 'avatar' ? 'Đang tải...' : 'Đổi ảnh'}<input hidden type="file" accept="image/*" onChange={uploadAvatar} /></label></div>
            <form className="workspace-form" onSubmit={saveProfile}><div className="form-grid">
              <label>Họ<input value={profileForm.firstName} onChange={(e) => setProfileForm({ ...profileForm, firstName: e.target.value })} /></label>
              <label>Tên<input value={profileForm.lastName} onChange={(e) => setProfileForm({ ...profileForm, lastName: e.target.value })} /></label>
              <label>Số điện thoại<input value={profileForm.phone} onChange={(e) => setProfileForm({ ...profileForm, phone: e.target.value })} placeholder="0xxxxxxxxx" /></label>
              <label>Ngày sinh<input type="date" value={profileForm.birthday} onChange={(e) => setProfileForm({ ...profileForm, birthday: e.target.value })} /></label>
              <label>Giới tính<select value={profileForm.gender} onChange={(e) => setProfileForm({ ...profileForm, gender: e.target.value })}><option value="">Chưa chọn</option><option value="MAN">Nam</option><option value="WOMAN">Nữ</option></select></label>
              <label>Email<input value={profile?.email || ''} disabled /></label>
            </div><button className="workspace-button" disabled={busy === 'profile'}>{busy === 'profile' ? 'Đang lưu...' : 'Lưu hồ sơ'}</button></form>
          </div>}

          {tab === 'addresses' && <div className="workspace-section"><header><small>ĐỊA CHỈ</small><h2>Sổ địa chỉ</h2><p>Quản lý đầy đủ các địa chỉ gắn với tài khoản.</p></header>
            <div className="address-list">{addresses.map((address) => <article key={address.id}><strong>{address.houseNumber} {address.street}</strong><p>{address.ward}, {address.district}, {address.city}, {address.country}</p><div><button type="button" onClick={() => editAddress(address.id)}>Chi tiết / Sửa</button><button className="is-danger" type="button" onClick={() => removeAddress(address.id)}>Xóa</button></div></article>)}{!addresses.length && <p className="workspace-empty">Chưa có địa chỉ.</p>}</div>
            <form className="workspace-form workspace-card" onSubmit={saveAddress}><h3>{editingAddressId ? 'Cập nhật địa chỉ' : 'Thêm địa chỉ'}</h3><div className="form-grid">
              {Object.entries({ houseNumber: 'Số nhà', street: 'Đường', ward: 'Phường/Xã', district: 'Quận/Huyện', city: 'Tỉnh/Thành phố', country: 'Quốc gia', postalCode: 'Mã bưu chính' }).map(([key, label]) => <label key={key}>{label}<input required={!['houseNumber', 'postalCode'].includes(key)} value={addressForm[key] || ''} onChange={(e) => setAddressForm({ ...addressForm, [key]: e.target.value })} /></label>)}
            </div><div className="button-row"><button className="workspace-button">{editingAddressId ? 'Lưu thay đổi' : 'Thêm địa chỉ'}</button>{editingAddressId && <button className="workspace-button is-secondary" type="button" onClick={() => { setEditingAddressId(null); setAddressForm(EMPTY_ADDRESS) }}>Hủy</button>}</div></form>
          </div>}

          {tab === 'contact' && <div className="workspace-section"><header><small>XÁC MINH</small><h2>Email & điện thoại</h2><p>Mỗi thay đổi đều cần mật khẩu hiện tại và mã OTP.</p></header><div className="split-cards">
            <form className="workspace-form workspace-card" onSubmit={emailFlow.pending ? verifyEmail : startEmailChange}><h3>Đổi email</h3><label>Email mới<input type="email" required value={emailFlow.newEmail} onChange={(e) => setEmailFlow({ ...emailFlow, newEmail: e.target.value })} disabled={emailFlow.pending} /></label>{!emailFlow.pending && <label>Mật khẩu hiện tại<input type="password" required value={emailFlow.currentPassword} onChange={(e) => setEmailFlow({ ...emailFlow, currentPassword: e.target.value })} /></label>}{emailFlow.pending && <label>Mã OTP<input inputMode="numeric" required value={emailFlow.otp} onChange={(e) => setEmailFlow({ ...emailFlow, otp: e.target.value.replace(/\D/g, '').slice(0, 6) })} /></label>}<button className="workspace-button">{emailFlow.pending ? 'Xác nhận email' : 'Gửi OTP'}</button>{emailFlow.pending && <button className="text-action" type="button" onClick={() => run('email-resend', accountApi.resendEmailVerification, 'Đã gửi lại OTP.')}>Gửi lại OTP</button>}</form>
            <form className="workspace-form workspace-card" onSubmit={phoneFlow.pending ? verifyPhone : startPhoneChange}><h3>Đổi số điện thoại</h3><label>Số mới<input required value={phoneFlow.newPhone} onChange={(e) => setPhoneFlow({ ...phoneFlow, newPhone: e.target.value })} disabled={phoneFlow.pending} /></label>{!phoneFlow.pending && <label>Mật khẩu hiện tại<input type="password" required value={phoneFlow.currentPassword} onChange={(e) => setPhoneFlow({ ...phoneFlow, currentPassword: e.target.value })} /></label>}{phoneFlow.pending && <label>Mã OTP<input inputMode="numeric" required value={phoneFlow.otp} onChange={(e) => setPhoneFlow({ ...phoneFlow, otp: e.target.value.replace(/\D/g, '').slice(0, 6) })} /></label>}<button className="workspace-button">{phoneFlow.pending ? 'Xác nhận số điện thoại' : 'Gửi OTP'}</button>{phoneFlow.pending && <button className="text-action" type="button" onClick={() => run('phone-resend', accountApi.resendPhoneVerification, 'Đã gửi lại OTP.')}>Gửi lại OTP</button>}</form>
          </div></div>}

          {tab === 'security' && <div className="workspace-section"><header><small>BẢO MẬT</small><h2>Mật khẩu & phiên đăng nhập</h2></header><form className="workspace-form workspace-card" onSubmit={changePassword}><h3>Đổi mật khẩu</h3><label>Mật khẩu hiện tại<input type="password" required value={passwordForm.currentPassword} onChange={(e) => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })} /></label><label>Mật khẩu mới<input type="password" minLength="8" required value={passwordForm.newPassword} onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })} /></label><label>Xác nhận mật khẩu<input type="password" minLength="8" required value={passwordForm.confirmPassword} onChange={(e) => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })} /></label><button className="workspace-button">Đổi mật khẩu</button></form>
            <div className="danger-zone"><h3>Phiên và tài khoản</h3><p>Đăng xuất mọi thiết bị sẽ vô hiệu hóa toàn bộ refresh token hiện tại.</p><div className="button-row"><button className="workspace-button is-secondary" type="button" onClick={endAllSessions}>Đăng xuất mọi thiết bị</button><button className="workspace-button is-danger" type="button" onClick={removeAccount}>Xóa tài khoản</button></div></div>
          </div>}

          {tab === 'reports' && (
            <div className="workspace-section">
              <header>
                <small>{t('reportsTabBadge') || 'BÁO CÁO'}</small>
                <h2>{t('myReports') || 'Báo cáo của tôi'}</h2>
                <p>{t('reportsTabSubtitle') || 'Xem lại các báo cáo vi phạm bạn đã gửi và trạng thái xử lý.'}</p>
              </header>

              {reportsLoading && (
                <p className="workspace-empty">{t('loadingReports') || 'Đang tải báo cáo...'}</p>
              )}

              {!reportsLoading && reportsError && (
                <div className="workspace-empty" data-testid="reports-error-state">
                  <p>{t('loadReportsFailed') || 'Không thể tải danh sách báo cáo.'}</p>
                  <button
                    className="workspace-button is-secondary is-small"
                    type="button"
                    style={{ marginTop: '10px' }}
                    onClick={() => loadReports(reportsPage.pageNo || 0)}
                  >
                    {t('retry') || 'Thử lại'}
                  </button>
                </div>
              )}

              {!reportsLoading && !reportsError && reports.length === 0 && (
                <p className="workspace-empty">{t('noReports') || 'Chưa có báo cáo nào'}</p>
              )}

              {!reportsLoading && !reportsError && reports.length > 0 && (
                <div className="reports-list">
                  {reports.map((report) => {
                    const targetUser = report.reportedUser
                    const targetDisplayName = targetUser?.displayName || [targetUser?.firstName, targetUser?.lastName].filter(Boolean).join(' ').trim() || targetUser?.username || '—'
                    const reasonMap = {
                      SPAM: t('reportSpam') || 'Spam hoặc lừa đảo',
                      HARASSMENT: t('reportHarassment') || 'Quấy rối',
                      INAPPROPRIATE: t('reportInappropriate') || 'Nội dung không phù hợp',
                      IMPERSONATION: t('reportImpersonation') || 'Mạo danh',
                      OTHER: t('reportOther') || 'Lý do khác',
                    }
                    const reasonKey = String(report.reason || '').toUpperCase()
                    const reasonLabel = reasonMap[reasonKey] || report.reason || '—'

                    const statusMap = {
                      PENDING: t('reportStatusPending') || 'Đang chờ xử lý',
                      RESOLVED: t('reportStatusResolved') || 'Đã xử lý',
                      DISMISSED: t('reportStatusDismissed') || 'Đã bác bỏ',
                    }
                    const statusKey = String(report.status || 'PENDING').toUpperCase()
                    const statusLabel = statusMap[statusKey] || report.status
                    const statusClass = statusKey.toLowerCase()

                    return (
                      <article key={report.id} className="report-card workspace-card" data-testid={`report-card-${report.id}`}>
                        <div className="report-card__header">
                          <div className="report-card__user">
                            <span className="cw-avatar cw-avatar--small">
                              {targetUser?.avatar ? (
                                <img src={targetUser.avatar} alt={targetDisplayName} />
                              ) : (
                                <span>{(targetDisplayName[0] || 'U').toUpperCase()}</span>
                              )}
                            </span>
                            <div>
                              <strong>{targetDisplayName}</strong>
                              {targetUser?.username && <small>@{targetUser.username}</small>}
                            </div>
                          </div>
                          <span className={`report-status-badge report-status-badge--${statusClass}`}>
                            <i />
                            {statusLabel}
                          </span>
                        </div>

                        <div className="report-card__body">
                          <div className="report-card__field">
                            <span className="report-card__label">{t('reportReason') || 'Lý do'}:</span>
                            <span className="report-card__value"><strong>{reasonLabel}</strong></span>
                          </div>

                          <div className="report-card__field">
                            <span className="report-card__label">{t('reportDetails') || 'Chi tiết'}:</span>
                            <span className="report-card__value">{report.details || t('noReportDetails') || 'Không có mô tả chi tiết'}</span>
                          </div>

                          <div className="report-card__field">
                            <span className="report-card__label">{t('reportDate') || 'Thời gian gửi'}:</span>
                            <span className="report-card__value">
                              {formatReportDate(report.createdAt, language)}
                            </span>
                          </div>

                          {report.resolutionNote && (
                            <div className="report-card__resolution">
                              <strong>{t('adminResolutionNote') || 'Ghi chú xử lý từ kiểm duyệt viên'}:</strong>
                              <p>{report.resolutionNote}</p>
                            </div>
                          )}
                        </div>

                        {statusKey === 'PENDING' && (
                          <div className="report-card__footer">
                            <button
                              className="workspace-button is-danger is-small"
                              type="button"
                              disabled={cancellingReportId !== null}
                              onClick={() => handleCancelReport(report.id)}
                            >
                              {String(cancellingReportId) === String(report.id)
                                ? (t('cancellingReport') || 'Đang hủy...')
                                : (t('cancelReport') || 'Hủy báo cáo')}
                            </button>
                          </div>
                        )}
                      </article>
                    )
                  })}
                </div>
              )}

              {!reportsLoading && !reportsError && reportsPage.totalPages > 1 && (
                <div className="reports-pagination" style={{ marginTop: '20px' }}>
                  <button
                    type="button"
                    disabled={reportsPage.pageNo <= 0 || reportsLoading}
                    onClick={() => loadReports(reportsPage.pageNo - 1)}
                  >
                    <ChatIcon name="arrowLeft" size={15} />
                    {t('prevPage') || 'Trước'}
                  </button>
                  <span>{t('pageOf') || 'Trang'} <strong>{reportsPage.pageNo + 1}</strong> / {reportsPage.totalPages}</span>
                  <button
                    type="button"
                    disabled={reportsPage.last || reportsLoading}
                    onClick={() => loadReports(reportsPage.pageNo + 1)}
                  >
                    {t('nextPage') || 'Sau'} <span aria-hidden="true">→</span>
                  </button>
                </div>
              )}
            </div>
          )}

        </section>
      </div>
      </section>
    </main>
  )
}

export default SettingsPage
