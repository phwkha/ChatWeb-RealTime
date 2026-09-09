import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import AppRail from '../components/chat/AppRail.jsx'
import { useAuth } from '../context/auth-context.js'
import { accountApi } from '../services/accountApi.js'
import { getErrorMessage } from '../services/apiClient.js'
import '../styles/workspace.css'
import '../styles/chat.css'

const EMPTY_ADDRESS = {
  houseNumber: '', street: '', ward: '', district: '', city: '', country: 'Việt Nam', postalCode: '',
}
const SETTINGS_TABS = new Set(['profile', 'addresses', 'contact', 'security'])

function addressPayload(address) {
  return Object.fromEntries(Object.keys(EMPTY_ADDRESS).map((key) => [key, address[key] || '']))
}

function SettingsPage() {
  const { user, refreshUser, logoutEverywhere, deleteAccount } = useAuth()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [tab, setTab] = useState(() => {
    const requestedTab = searchParams.get('tab')
    return SETTINGS_TABS.has(requestedTab) ? requestedTab : 'profile'
  })
  const [profile, setProfile] = useState(null)
  const [profileForm, setProfileForm] = useState({ firstName: '', lastName: '', phone: '', birthday: '', gender: '' })
  const [addresses, setAddresses] = useState([])
  const [addressForm, setAddressForm] = useState(EMPTY_ADDRESS)
  const [editingAddressId, setEditingAddressId] = useState(null)
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [emailFlow, setEmailFlow] = useState({ newEmail: '', currentPassword: '', otp: '', pending: false })
  const [phoneFlow, setPhoneFlow] = useState({ newPhone: '', currentPassword: '', otp: '', pending: false })
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState(null)

  const notify = useCallback((message, tone = 'success') => setNotice({ message, tone }), [])

  const selectTab = (nextTab) => {
    setTab(nextTab)
    setSearchParams({ tab: nextTab }, { replace: true })
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
          {[['profile', 'Hồ sơ'], ['addresses', 'Địa chỉ'], ['contact', 'Email & điện thoại'], ['security', 'Bảo mật']].map(([value, label]) => (
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

        </section>
      </div>
      </section>
    </main>
  )
}

export default SettingsPage
