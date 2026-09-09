import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Brand from '../components/Brand.jsx'
import ChatIcon from '../components/chat/ChatIcon.jsx'
import { useAuth } from '../context/auth-context.js'
import { useLanguage } from '../context/language-context.js'
import { useChatSocket } from '../hooks/useChatSocket.js'
import { adminApi } from '../services/adminApi.js'
import { getErrorMessage } from '../services/apiClient.js'
import { hasPermission, isAdminUser } from '../services/authorization.js'
import '../styles/admin.css'

const EMPTY_USER = { username: '', password: '', firstName: '', lastName: '', email: '', phone: '', roleId: '' }
const EMPTY_ROLE = { name: '', description: '', permissionIds: [] }
const EMPTY_EMAIL = { to: '', subject: '', text: '' }
const EMPTY_ADVANCED = { username: '', firstName: '', lastName: '', city: '', country: '' }
const EMPTY_ANNOUNCEMENT = { content: '', survivalTime: '' }
const DEFAULT_FILTERS = { keyword: '', role: '', status: '', gender: '', authProvider: '', sorts: 'id:desc', page: 0, size: 20 }
const EMPTY_PAGE = { pageNo: 0, pageSize: 20, totalElements: 0, totalPages: 0, last: true }
const ADDRESS_FIELDS = ['houseNumber', 'street', 'ward', 'district', 'city', 'country', 'postalCode']

const NAV_ITEMS = [
  { id: 'dashboard', label: 'Tổng quan', icon: 'shield' },
  { id: 'users', label: 'Người dùng', icon: 'users', permission: 'ADMIN_VIEW_USERS' },
  { id: 'roles', label: 'Role & quyền', icon: 'settings', permission: 'ROLE_VIEW_ALL' },
  { id: 'search', label: 'Tìm kiếm nâng cao', icon: 'search' },
  { id: 'email', label: 'Email hệ thống', icon: 'send', permission: 'SEND_EMAIL' },
  { id: 'broadcast', label: 'Thông báo hệ thống', icon: 'globe', permission: 'ADMIN_SEND-MESSAGE' },
]

const SECTION_META = {
  dashboard: ['Trung tâm điều hành', 'Theo dõi và truy cập nhanh toàn bộ công cụ quản trị ChatWeb.'],
  users: ['Quản lý người dùng', 'Tìm kiếm, tạo mới, cập nhật, khóa và quản lý dữ liệu tài khoản.'],
  roles: ['Role & phân quyền', 'Thiết kế vai trò và kiểm soát chính xác quyền truy cập hệ thống.'],
  search: ['Tìm kiếm nâng cao', 'Kết hợp điều kiện tài khoản và địa chỉ để truy vấn người dùng.'],
  email: ['Email hệ thống', 'Gửi email văn bản trực tiếp từ hệ thống đến người dùng.'],
  broadcast: ['Thông báo toàn hệ thống', 'Phát và theo dõi thông báo realtime trên kênh thế giới.'],
}

function displayName(person) {
  return [person?.firstName, person?.lastName].filter(Boolean).join(' ').trim() || person?.username || 'Người dùng'
}

function initials(person) {
  return displayName(person).split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

function addressPayload(address) {
  return Object.fromEntries(ADDRESS_FIELDS.map((key) => [key, address?.[key] || '']))
}

function AdminAvatar({ person, size = 'medium' }) {
  return <span className={`admin-avatar admin-avatar--${size}`}>
    {person?.avatar ? <img src={person.avatar} alt="" /> : initials(person)}
    {person?.online || person?.isOnline ? <i /> : null}
  </span>
}

function StatusBadge({ status }) {
  const normalized = String(status || 'UNKNOWN').toLowerCase()
  return <span className={`admin-status admin-status--${normalized}`}><i />{status || 'Không rõ'}</span>
}

function EmptyState({ icon = 'search', children }) {
  return <div className="admin-empty"><ChatIcon name={icon} size={24} /><p>{children}</p></div>
}

function Pagination({ page, onChange }) {
  if (!page.totalPages || page.totalPages <= 1) return null
  return <div className="admin-pagination">
    <button type="button" disabled={page.pageNo <= 0} onClick={() => onChange(page.pageNo - 1)}><ChatIcon name="arrowLeft" size={15} />Trước</button>
    <span>Trang <strong>{page.pageNo + 1}</strong> / {page.totalPages}</span>
    <button type="button" disabled={page.last} onClick={() => onChange(page.pageNo + 1)}>Sau <span aria-hidden="true">→</span></button>
  </div>
}

function AdminPage() {
  const { user, logout } = useAuth()
  const { language } = useLanguage()
  const navigate = useNavigate()
  const [tab, setTab] = useState('dashboard')
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const [users, setUsers] = useState([])
  const [userPage, setUserPage] = useState(EMPTY_PAGE)
  const [onlineCount, setOnlineCount] = useState(0)
  const [roles, setRoles] = useState([])
  const [permissions, setPermissions] = useState([])
  const [selectedUser, setSelectedUser] = useState(null)
  const [selectedAddresses, setSelectedAddresses] = useState([])
  const [editingAddress, setEditingAddress] = useState(null)
  const [userEditorOpen, setUserEditorOpen] = useState(false)
  const [userForm, setUserForm] = useState(EMPTY_USER)
  const [roleForm, setRoleForm] = useState(EMPTY_ROLE)
  const [editingRoleId, setEditingRoleId] = useState(null)
  const [filters, setFilters] = useState(DEFAULT_FILTERS)
  const [onlineOnly, setOnlineOnly] = useState(false)
  const [emailForm, setEmailForm] = useState(EMPTY_EMAIL)
  const [advancedForm, setAdvancedForm] = useState(EMPTY_ADVANCED)
  const [advancedResults, setAdvancedResults] = useState([])
  const [advancedPage, setAdvancedPage] = useState(EMPTY_PAGE)
  const [announcements, setAnnouncements] = useState([])
  const [announcementCursor, setAnnouncementCursor] = useState(null)
  const [announcementHasMore, setAnnouncementHasMore] = useState(false)
  const [announcementForm, setAnnouncementForm] = useState(EMPTY_ANNOUNCEMENT)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState('')
  const initializedRef = useRef(false)

  const isAdmin = isAdminUser(user)
  const can = useCallback((permission) => !permission || hasPermission(user, permission), [user])
  const notify = useCallback((message, tone = 'success') => setNotice({ message, tone, id: Date.now() }), [])
  const visibleNavItems = useMemo(() => NAV_ITEMS.filter((item) => can(item.permission)), [can])
  const [sectionTitle, sectionDescription] = SECTION_META[tab] || SECTION_META.dashboard

  const run = async (name, action, successMessage = '') => {
    setBusy(name)
    setNotice(null)
    try {
      const response = await action()
      if (successMessage || response?.message) notify(response?.message || successMessage)
      return response
    } catch (error) {
      notify(getErrorMessage(error, 'Thao tác quản trị không thành công.'), 'error')
      return null
    } finally {
      setBusy('')
    }
  }

  const loadUsers = useCallback(async (nextFilters = DEFAULT_FILTERS, onlyOnline = false) => {
    setBusy('users-load')
    try {
      const response = onlyOnline
        ? await adminApi.getOnlineUsers(nextFilters.page, nextFilters.size)
        : await adminApi.getUsers(nextFilters)
      const data = response?.data || EMPTY_PAGE
      setUsers(data.content || [])
      setUserPage({ ...EMPTY_PAGE, ...data })
    } catch (error) {
      notify(getErrorMessage(error, 'Không thể tải danh sách người dùng.'), 'error')
    } finally {
      setBusy('')
    }
  }, [notify])

  const loadRoles = useCallback(async () => {
    const requests = []
    if (can('ROLE_VIEW_ALL')) requests.push(adminApi.getRoles().then((response) => setRoles(response?.data || [])))
    if (can('ROLE_VIEW_ALL_PERMISSION')) requests.push(adminApi.getPermissions().then((response) => setPermissions(response?.data || [])))
    await Promise.allSettled(requests)
  }, [can])

  const loadOnlineCount = useCallback(async () => {
    if (!can('ADMIN_VIEW_ONLINE_USERS')) return
    try {
      const response = await adminApi.getOnlineUsers(0, 1)
      setOnlineCount(Number(response?.data?.totalElements || 0))
    } catch {
      setOnlineCount(0)
    }
  }, [can])

  useEffect(() => {
    if (!isAdmin || initializedRef.current) return
    initializedRef.current = true
    // oxlint-disable-next-line react/set-state-in-effect -- hydrate protected admin resources once after authorization.
    if (can('ADMIN_VIEW_USERS')) void loadUsers(DEFAULT_FILTERS, false)
    void loadRoles()
    void loadOnlineCount()
  }, [can, isAdmin, loadOnlineCount, loadRoles, loadUsers])

  const handleWorldMessage = useCallback((message) => {
    if (!String(message?.content || '').trim()) return
    setAnnouncements((current) => [...current, message]
      .filter((item, index, all) => all.findIndex((candidate) => `${candidate.sender}-${candidate.timestamp}-${candidate.content}` === `${item.sender}-${item.timestamp}-${item.content}`) === index)
      .sort((left, right) => new Date(right.timestamp) - new Date(left.timestamp)))
  }, [])

  const handleSocketError = useCallback((error) => {
    notify(getErrorMessage(error, 'Kết nối thông báo realtime gặp sự cố.'), 'error')
  }, [notify])

  const { connectionState, sendWorldMessage } = useChatSocket({
    enabled: Boolean(user) && can('ADMIN_SEND-MESSAGE'), language, subscribeToWorld: true,
    onWorldMessage: handleWorldMessage, onError: handleSocketError,
  })

  const loadAnnouncements = useCallback(async (cursor = null, append = false) => {
    setBusy('announcements-load')
    try {
      const response = await adminApi.getSystemMessages(cursor, 20)
      const content = response?.data?.content || []
      setAnnouncements((current) => {
        const combined = append ? [...current, ...content] : content
        const unique = new Map(combined.map((item) => [`${item.sender}-${item.timestamp}-${item.content}`, item]))
        return [...unique.values()].sort((left, right) => new Date(right.timestamp) - new Date(left.timestamp))
      })
      setAnnouncementCursor(response?.data?.nextCursor || null)
      setAnnouncementHasMore(Boolean(response?.data?.hasMore && response?.data?.nextCursor))
    } catch (error) {
      notify(getErrorMessage(error, 'Không thể tải lịch sử thông báo.'), 'error')
    } finally {
      setBusy('')
    }
  }, [notify])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect -- opening the broadcast section hydrates its remote history.
    if (tab === 'broadcast' && announcements.length === 0) void loadAnnouncements()
  }, [announcements.length, loadAnnouncements, tab])

  const selectTab = (nextTab) => { setTab(nextTab); setMobileNavOpen(false); setNotice(null) }
  const applyFilters = (event) => { event.preventDefault(); const next = { ...filters, page: 0 }; setFilters(next); void loadUsers(next, onlineOnly) }
  const changeUserPage = (page) => { const next = { ...filters, page }; setFilters(next); void loadUsers(next, onlineOnly) }
  const toggleOnlineOnly = (checked) => { const next = { ...filters, page: 0 }; setOnlineOnly(checked); setFilters(next); void loadUsers(next, checked) }

  const startCreateUser = () => {
    setSelectedUser(null); setSelectedAddresses([]); setEditingAddress(null); setUserForm(EMPTY_USER); setUserEditorOpen(true)
  }

  const openUser = async (username) => {
    if (!can('ADMIN_VIEW_USER_DETAIL')) return
    setBusy('user-detail'); setNotice(null)
    try {
      const detail = await adminApi.getUser(username)
      let addresses = []
      if (can('ADMIN_VIEW_USER_ADDRESSES')) addresses = (await adminApi.getAddresses(username))?.data || []
      if (!detail?.data) return
      setSelectedUser(detail.data); setSelectedAddresses(addresses || detail.data.addresses || []); setEditingAddress(null)
      setUserForm({ username: detail.data.username, password: '', firstName: detail.data.firstName || '', lastName: detail.data.lastName || '', email: detail.data.email || '', phone: detail.data.phone || '', roleId: roles.find((role) => role.name === detail.data.role)?.id || '' })
      setUserEditorOpen(true)
    } catch (error) {
      notify(getErrorMessage(error, 'Không thể tải chi tiết người dùng.'), 'error')
    } finally { setBusy('') }
  }

  const closeUserEditor = () => { setUserEditorOpen(false); setSelectedUser(null); setEditingAddress(null); setUserForm(EMPTY_USER) }

  const saveUser = async (event) => {
    event.preventDefault()
    const roleId = userForm.roleId ? Number(userForm.roleId) : null
    const response = selectedUser
      ? await run('user-save', () => adminApi.updateUser(selectedUser.username, { firstName: userForm.firstName || null, lastName: userForm.lastName || null, email: userForm.email || null, phone: userForm.phone || null, roleId }), 'Đã cập nhật người dùng.')
      : await run('user-save', () => adminApi.createUser({ ...userForm, phone: userForm.phone || null, roleId }), 'Đã tạo người dùng.')
    if (response) { closeUserEditor(); await loadUsers(filters, onlineOnly) }
  }

  const userAction = async (action) => {
    if (!selectedUser) return
    const username = selectedUser.username
    const operations = { lock: () => adminApi.lockUser(username), unlock: () => adminApi.unlockUser(username), avatar: () => adminApi.deleteAvatar(username), delete: () => adminApi.deleteUser(username) }
    if (['delete', 'avatar'].includes(action) && !window.confirm(action === 'delete' ? `Xóa tài khoản @${username}?` : `Xóa avatar của @${username}?`)) return
    const response = await run(`user-${action}`, operations[action], 'Thao tác thành công.')
    if (!response) return
    if (action === 'delete') closeUserEditor(); else await openUser(username)
    await loadUsers(filters, onlineOnly); await loadOnlineCount()
  }

  const beginAddressEdit = async (id) => { if (!selectedUser) return; const response = await run('address-detail', () => adminApi.getAddress(selectedUser.username, id)); if (response?.data) setEditingAddress(response.data) }
  const saveAddress = async (event) => { event.preventDefault(); if (!selectedUser || !editingAddress) return; const response = await run('address-save', () => adminApi.updateAddress(selectedUser.username, editingAddress.id, addressPayload(editingAddress)), 'Đã cập nhật địa chỉ.'); if (response) await openUser(selectedUser.username) }
  const deleteAddress = async (id) => { if (!selectedUser || !window.confirm('Xóa địa chỉ của người dùng này?')) return; const response = await run('address-delete', () => adminApi.deleteAddress(selectedUser.username, id), 'Đã xóa địa chỉ.'); if (response) await openUser(selectedUser.username) }

  const saveRole = async (event) => {
    event.preventDefault(); const body = { ...roleForm, permissionIds: roleForm.permissionIds.map(Number) }
    const response = editingRoleId ? await run('role-save', () => adminApi.updateRole(editingRoleId, body), 'Đã cập nhật role.') : await run('role-save', () => adminApi.createRole(body), 'Đã tạo role.')
    if (response) { setRoleForm(EMPTY_ROLE); setEditingRoleId(null); await loadRoles() }
  }
  const editRole = (role) => { setEditingRoleId(role.id); setRoleForm({ name: role.name, description: role.description || '', permissionIds: (role.permissions || []).map((permission) => permission.id) }) }
  const deleteRole = async (id) => { if (!window.confirm('Xóa role này?')) return; const response = await run('role-delete', () => adminApi.deleteRole(id), 'Đã xóa role.'); if (response) await loadRoles() }

  const advancedSearch = async (event, page = 0) => {
    event?.preventDefault()
    const userCriteria = [advancedForm.username && `username~*${advancedForm.username}*`, advancedForm.firstName && `firstName~*${advancedForm.firstName}*`, advancedForm.lastName && `lastName~*${advancedForm.lastName}*`]
    const addressCriteria = [advancedForm.city && `city~*${advancedForm.city}*`, advancedForm.country && `country~*${advancedForm.country}*`]
    const response = await run('advanced-search', () => adminApi.advancedSearch({ user: userCriteria, address: addressCriteria, page, size: 20 }))
    if (response) { setAdvancedResults(response?.data?.content || []); setAdvancedPage({ ...EMPTY_PAGE, ...response?.data }) }
  }

  const sendEmail = async (event) => { event.preventDefault(); const response = await run('email-send', () => adminApi.sendEmail(emailForm), 'Email đã được đưa vào hàng đợi gửi.'); if (response) setEmailForm(EMPTY_EMAIL) }
  const sendAnnouncement = (event) => { event.preventDefault(); const content = announcementForm.content.trim(); if (!content) return; const survivalTime = announcementForm.survivalTime ? Number(announcementForm.survivalTime) : null; if (sendWorldMessage({ content, survivalTime })) { setAnnouncementForm(EMPTY_ANNOUNCEMENT); notify('Đã phát thông báo toàn hệ thống.') } else notify('Kết nối realtime chưa sẵn sàng. Vui lòng thử lại.', 'error') }
  const handleLogout = async () => { await logout().catch(() => {}); navigate('/login', { replace: true }) }

  if (!isAdmin) return <main className="admin-denied"><Brand /><section><ChatIcon name="shield" size={34} /><h1>Không có quyền truy cập</h1><p>Tài khoản này không có quyền mở Admin Console.</p><Link to="/chat">Quay lại trò chuyện</Link></section></main>

  return <main className="admin-shell">
    {mobileNavOpen && <button className="admin-nav-backdrop" type="button" aria-label="Đóng menu" onClick={() => setMobileNavOpen(false)} />}
    <aside className={`admin-sidebar${mobileNavOpen ? ' is-open' : ''}`}>
      <div className="admin-brand"><Brand to="/admin" ariaLabel="ChatWeb Admin Console" /><span>CONTROL CENTER</span></div>
      <div className="admin-profile"><AdminAvatar person={user} /><div><strong>{displayName(user)}</strong><small>@{user.username}</small></div><span>ADMIN</span></div>
      <nav><small>ĐIỀU HƯỚNG</small>{visibleNavItems.map((item) => <button key={item.id} className={tab === item.id ? 'is-active' : ''} type="button" onClick={() => selectTab(item.id)}><ChatIcon name={item.icon} size={18} /><span>{item.label}</span></button>)}</nav>
      <footer><Link to="/chat"><ChatIcon name="chat" size={17} />Giao diện trò chuyện</Link><Link to="/settings"><ChatIcon name="settings" size={17} />Cài đặt tài khoản</Link><button type="button" onClick={handleLogout}><ChatIcon name="logout" size={17} />Đăng xuất</button></footer>
    </aside>

    <section className="admin-main">
      <header className="admin-topbar"><button className="admin-mobile-menu" type="button" aria-label="Mở menu" onClick={() => setMobileNavOpen(true)}><ChatIcon name="more" /></button><div><small>ADMIN CONSOLE / {tab.toUpperCase()}</small><h1>{sectionTitle}</h1><p>{sectionDescription}</p></div><div className="admin-topbar__status"><i className={connectionState === 'connected' ? 'is-online' : ''} /><span>Realtime</span><strong>{connectionState === 'connected' ? 'Hoạt động' : 'Đang kết nối'}</strong></div></header>
      <div className="admin-content">
        {notice && <div className={`admin-notice admin-notice--${notice.tone}`} role="status"><span>{notice.tone === 'error' ? '!' : '✓'}</span><p>{notice.message}</p><button type="button" onClick={() => setNotice(null)}><ChatIcon name="close" size={14} /></button></div>}
        {tab === 'dashboard' && <Dashboard user={user} userPage={userPage} onlineCount={onlineCount} roles={roles} permissions={permissions} connectionState={connectionState} navItems={visibleNavItems} onSelect={selectTab} />}
        {tab === 'users' && <UsersSection users={users} roles={roles} filters={filters} setFilters={setFilters} onlineOnly={onlineOnly} onToggleOnline={toggleOnlineOnly} onFilter={applyFilters} onCreate={startCreateUser} onOpen={openUser} can={can} busy={busy} page={userPage} onPage={changeUserPage} onLoad={loadUsers} />}
        {tab === 'roles' && <RolesSection roles={roles} permissions={permissions} form={roleForm} setForm={setRoleForm} editingId={editingRoleId} busy={busy} can={can} onEdit={editRole} onDelete={deleteRole} onSave={saveRole} onCancel={() => { setEditingRoleId(null); setRoleForm(EMPTY_ROLE) }} />}
        {tab === 'search' && <SearchSection form={advancedForm} setForm={setAdvancedForm} results={advancedResults} page={advancedPage} busy={busy} onSearch={advancedSearch} onReset={() => { setAdvancedForm(EMPTY_ADVANCED); setAdvancedResults([]); setAdvancedPage(EMPTY_PAGE) }} onManage={(username) => { selectTab('users'); void openUser(username) }} />}
        {tab === 'email' && <EmailSection form={emailForm} setForm={setEmailForm} busy={busy} onSubmit={sendEmail} />}
        {tab === 'broadcast' && <BroadcastSection form={announcementForm} setForm={setAnnouncementForm} messages={announcements} connectionState={connectionState} busy={busy} hasMore={announcementHasMore} onSubmit={sendAnnouncement} onRefresh={() => loadAnnouncements()} onMore={() => loadAnnouncements(announcementCursor, true)} />}
      </div>
    </section>
    {userEditorOpen && <UserDrawer selectedUser={selectedUser} addresses={selectedAddresses} editingAddress={editingAddress} setEditingAddress={setEditingAddress} form={userForm} setForm={setUserForm} roles={roles} busy={busy} can={can} onClose={closeUserEditor} onSave={saveUser} onAction={userAction} onAddressEdit={beginAddressEdit} onAddressSave={saveAddress} onAddressDelete={deleteAddress} />}
  </main>
}

function Dashboard({ user, userPage, onlineCount, roles, permissions, connectionState, navItems, onSelect }) {
  return <section className="admin-dashboard"><div className="admin-stat-grid"><article><span><ChatIcon name="users" /></span><div><small>TỔNG NGƯỜI DÙNG</small><strong>{userPage.totalElements}</strong><p>Dữ liệu từ hệ thống</p></div></article><article><span><ChatIcon name="chat" /></span><div><small>ĐANG ONLINE</small><strong>{onlineCount}</strong><p>Cập nhật realtime</p></div></article><article><span><ChatIcon name="shield" /></span><div><small>ROLE HỆ THỐNG</small><strong>{roles.length}</strong><p>{permissions.length} quyền truy cập</p></div></article><article><span><ChatIcon name="globe" /></span><div><small>KẾT NỐI SOCKET</small><strong className="admin-stat-word">{connectionState === 'connected' ? 'Ổn định' : 'Chờ'}</strong><p>Kênh thông báo hệ thống</p></div></article></div><div className="admin-dashboard-grid"><article className="admin-panel admin-welcome-panel"><small>CHATWEB ADMINISTRATION</small><h2>Chào {user.firstName || user.username}, hệ thống đã sẵn sàng.</h2><p>Quản lý tài khoản, phân quyền và giao tiếp hệ thống từ một không gian độc lập với ứng dụng chat.</p><div>{navItems.filter((item) => item.id !== 'dashboard').slice(0, 3).map((item) => <button key={item.id} type="button" onClick={() => onSelect(item.id)}><ChatIcon name={item.icon} size={17} />{item.label}<span>→</span></button>)}</div></article><article className="admin-panel admin-capabilities"><header><div><small>PHẠM VI QUẢN TRỊ</small><h3>Quyền đang được cấp</h3></div><b>{user.permissions?.length || permissions.length}</b></header><div>{(user.permissions || []).slice(0, 12).map((permission) => <span key={permission}><i />{permission}</span>)}</div></article></div></section>
}

function UsersSection({ users, roles, filters, setFilters, onlineOnly, onToggleOnline, onFilter, onCreate, onOpen, can, busy, page, onPage, onLoad }) {
  return <section className="admin-section"><div className="admin-section-toolbar"><form className="admin-filter-form" onSubmit={onFilter}><label className="admin-search"><ChatIcon name="search" size={17} /><input value={filters.keyword} onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} placeholder="Username, email hoặc tên..." /></label><select value={filters.role} onChange={(event) => setFilters({ ...filters, role: event.target.value })}><option value="">Mọi role</option>{roles.map((role) => <option key={role.id} value={role.name}>{role.name}</option>)}</select><select value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}><option value="">Mọi trạng thái</option>{['ACTIVE', 'LOCKED', 'UNVERIFIED', 'INACTIVE'].map((status) => <option key={status}>{status}</option>)}</select><select value={filters.gender} onChange={(event) => setFilters({ ...filters, gender: event.target.value })}><option value="">Mọi giới tính</option><option value="MAN">Nam</option><option value="WOMAN">Nữ</option></select><select value={filters.authProvider} onChange={(event) => setFilters({ ...filters, authProvider: event.target.value })}><option value="">Mọi đăng nhập</option>{['LOCAL', 'GOOGLE', 'FACEBOOK', 'GITHUB'].map((provider) => <option key={provider}>{provider}</option>)}</select><select value={filters.sorts} onChange={(event) => setFilters({ ...filters, sorts: event.target.value })}><option value="id:desc">Mới nhất</option><option value="id:asc">Cũ nhất</option><option value="username:asc">Username A–Z</option><option value="username:desc">Username Z–A</option><option value="updateAt:desc">Cập nhật gần nhất</option></select><button type="submit"><ChatIcon name="search" size={15} />Lọc</button></form>{can('ADMIN_CREATE') && <button className="admin-primary-button" type="button" onClick={onCreate}><ChatIcon name="plus" size={16} />Tạo người dùng</button>}</div><label className="admin-online-toggle"><input type="checkbox" checked={onlineOnly} onChange={(event) => onToggleOnline(event.target.checked)} /><span />Chỉ hiển thị người dùng online</label><div className="admin-table-card"><div className="admin-table-summary"><span><strong>{page.totalElements}</strong> người dùng</span><select value={filters.size} onChange={(event) => { const next = { ...filters, size: Number(event.target.value), page: 0 }; setFilters(next); void onLoad(next, onlineOnly) }}><option value="10">10 / trang</option><option value="20">20 / trang</option><option value="50">50 / trang</option></select></div><div className="admin-user-table"><div className="admin-user-table__head"><span>Người dùng</span><span>Liên hệ</span><span>Role</span><span>Trạng thái</span><span /></div>{users.map((item) => <article key={item.username}><div className="admin-user-cell"><AdminAvatar person={item} size="small" /><div><strong>{displayName(item)}</strong><small>@{item.username}</small></div></div><div><strong>{item.email || '—'}</strong><small>{item.phone || item.authProvider || 'Chưa có thông tin'}</small></div><span className="admin-role-badge">{item.role || '—'}</span><StatusBadge status={item.userStatus} /><button type="button" disabled={!can('ADMIN_VIEW_USER_DETAIL')} onClick={() => void onOpen(item.username)}>Chi tiết <span>→</span></button></article>)}{!users.length && <EmptyState icon="users">{busy === 'users-load' ? 'Đang tải người dùng...' : 'Không tìm thấy người dùng phù hợp.'}</EmptyState>}</div><Pagination page={page} onChange={onPage} /></div></section>
}

function RolesSection({ roles, permissions, form, setForm, editingId, busy, can, onEdit, onDelete, onSave, onCancel }) {
  return <section className="admin-section admin-role-layout"><div className="admin-role-list">{roles.map((role) => <article className="admin-panel" key={role.id}><header><span><ChatIcon name="shield" size={18} /></span><div><h3>{role.name}</h3><small>{(role.permissions || []).length} quyền</small></div></header><p>{role.description || 'Chưa có mô tả.'}</p><div className="admin-role-permissions">{(role.permissions || []).slice(0, 5).map((permission) => <span key={permission.id}>{permission.name}</span>)}{(role.permissions || []).length > 5 && <span>+{role.permissions.length - 5}</span>}</div><footer>{can('ROLE_UPDATE') && <button type="button" onClick={() => onEdit(role)}><ChatIcon name="edit" size={14} />Sửa</button>}{can('ROLE_DELETE') && <button className="is-danger" type="button" onClick={() => void onDelete(role.id)}><ChatIcon name="trash" size={14} />Xóa</button>}</footer></article>)}</div>{(can('ROLE_ADD') || (editingId && can('ROLE_UPDATE'))) && <form className="admin-panel admin-form admin-role-form" onSubmit={onSave}><header><small>ROLE EDITOR</small><h2>{editingId ? 'Cập nhật role' : 'Tạo role mới'}</h2></header><label>Tên role<input required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="VD: MODERATOR" /></label><label>Mô tả<textarea rows="3" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label><fieldset><legend>Danh sách permissions</legend><div className="admin-permission-grid">{permissions.map((permission) => <label key={permission.id}><input type="checkbox" checked={form.permissionIds.includes(permission.id)} onChange={(event) => setForm({ ...form, permissionIds: event.target.checked ? [...form.permissionIds, permission.id] : form.permissionIds.filter((id) => id !== permission.id) })} /><span><strong>{permission.name}</strong><small>{permission.description}</small></span></label>)}</div></fieldset><div className="admin-form-actions"><button className="admin-primary-button" disabled={busy === 'role-save'}>{editingId ? 'Lưu thay đổi' : 'Tạo role'}</button>{editingId && <button type="button" onClick={onCancel}>Hủy</button>}</div></form>}</section>
}

function SearchSection({ form, setForm, results, page, busy, onSearch, onReset, onManage }) {
  return <section className="admin-section"><form className="admin-panel admin-form admin-advanced-form" onSubmit={(event) => void onSearch(event, 0)}><header><small>SPECIFICATION SEARCH</small><h2>Kết hợp nhiều điều kiện</h2><p>Để trống các trường không cần dùng.</p></header><div className="admin-form-grid">{Object.entries({ username: 'Username chứa', firstName: 'Họ chứa', lastName: 'Tên chứa', city: 'Thành phố chứa', country: 'Quốc gia chứa' }).map(([key, label]) => <label key={key}>{label}<input value={form[key]} onChange={(event) => setForm({ ...form, [key]: event.target.value })} /></label>)}</div><div className="admin-form-actions"><button className="admin-primary-button" disabled={busy === 'advanced-search'}><ChatIcon name="search" size={15} />Tìm kiếm</button><button type="button" onClick={onReset}>Đặt lại</button></div></form><div className="admin-table-card admin-search-results"><div className="admin-table-summary"><span>Kết quả tìm kiếm</span><strong>{page.totalElements || results.length}</strong></div>{results.map((item) => <article className="admin-search-result" key={item.username}><AdminAvatar person={item} size="small" /><div><strong>{displayName(item)}</strong><small>@{item.username}</small></div><StatusBadge status={item.userStatus} /><button type="button" onClick={() => onManage(item.username)}>Quản lý <span>→</span></button></article>)}{!results.length && <EmptyState>Nhập điều kiện để tìm kiếm người dùng.</EmptyState>}<Pagination page={page} onChange={(nextPage) => void onSearch(null, nextPage)} /></div></section>
}

function EmailSection({ form, setForm, busy, onSubmit }) {
  return <section className="admin-section admin-email-section"><form className="admin-panel admin-form" onSubmit={onSubmit}><header><span className="admin-form-icon"><ChatIcon name="send" /></span><small>EMAIL HỆ THỐNG</small><h2>Soạn email</h2><p>Nhập thông tin người nhận và nội dung cần gửi.</p></header><label>Người nhận<input type="email" required value={form.to} onChange={(event) => setForm({ ...form, to: event.target.value })} placeholder="user@example.com" /></label><label>Tiêu đề<input required value={form.subject} onChange={(event) => setForm({ ...form, subject: event.target.value })} /></label><label>Nội dung<textarea rows="11" required value={form.text} onChange={(event) => setForm({ ...form, text: event.target.value })} /></label><div className="admin-form-actions"><button className="admin-primary-button" disabled={busy === 'email-send'}><ChatIcon name="send" size={15} />{busy === 'email-send' ? 'Đang gửi...' : 'Gửi email'}</button></div></form></section>
}

function BroadcastSection({ form, setForm, messages, connectionState, busy, hasMore, onSubmit, onRefresh, onMore }) {
  return <section className="admin-section admin-communication-layout"><form className="admin-panel admin-form" onSubmit={onSubmit}><header><span className="admin-form-icon"><ChatIcon name="globe" /></span><small>REALTIME BROADCAST</small><h2>Phát thông báo mới</h2><p>Thông báo sẽ xuất hiện ngay trên kênh Thế giới của mọi người dùng.</p></header><label>Nội dung<textarea rows="7" required maxLength="10000" value={form.content} onChange={(event) => setForm({ ...form, content: event.target.value })} placeholder="Nhập nội dung thông báo..." /></label><label>Thời gian tồn tại (giây, không bắt buộc)<input type="number" min="1" value={form.survivalTime} onChange={(event) => setForm({ ...form, survivalTime: event.target.value })} /></label><div className="admin-form-actions"><button className="admin-primary-button" disabled={connectionState !== 'connected'}><ChatIcon name="send" size={15} />Phát thông báo</button><span className={`admin-socket-state admin-socket-state--${connectionState}`}><i />{connectionState === 'connected' ? 'Realtime sẵn sàng' : 'Đang kết nối...'}</span></div></form><div className="admin-panel admin-announcement-list"><header><div><small>LỊCH SỬ KÊNH THẾ GIỚI</small><h3>Thông báo gần đây</h3></div><button type="button" onClick={() => void onRefresh()}><ChatIcon name="history" size={15} />Làm mới</button></header>{messages.map((message, index) => <article key={`${message.timestamp}-${index}`}><span><ChatIcon name="globe" size={16} /></span><div><strong>{message.sender || 'Admin'}</strong><p>{message.content}</p><time>{message.timestamp ? new Date(message.timestamp).toLocaleString('vi-VN') : ''}</time></div></article>)}{!messages.length && <EmptyState icon="globe">Chưa có thông báo hệ thống.</EmptyState>}{hasMore && <button className="admin-load-more" type="button" disabled={busy === 'announcements-load'} onClick={() => void onMore()}>Tải thông báo cũ hơn</button>}</div></section>
}

function UserDrawer({ selectedUser, addresses, editingAddress, setEditingAddress, form, setForm, roles, busy, can, onClose, onSave, onAction, onAddressEdit, onAddressSave, onAddressDelete }) {
  return <div className="admin-drawer-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><aside className="admin-user-drawer" role="dialog" aria-modal="true"><header><div>{selectedUser ? <AdminAvatar person={selectedUser} /> : <span className="admin-form-icon"><ChatIcon name="plus" /></span>}<span><small>{selectedUser ? 'USER MANAGEMENT' : 'CREATE ACCOUNT'}</small><h2>{selectedUser ? displayName(selectedUser) : 'Tạo người dùng'}</h2>{selectedUser && <p>@{selectedUser.username}</p>}</span></div><button type="button" onClick={onClose}><ChatIcon name="close" /></button></header><div className="admin-user-drawer__body">{selectedUser && <div className="admin-user-overview"><StatusBadge status={selectedUser.userStatus} /><span className="admin-role-badge">{selectedUser.role}</span><small>{selectedUser.authProvider || 'LOCAL'}</small></div>}<form className="admin-form" onSubmit={onSave}><div className="admin-form-grid">{!selectedUser && <><label>Username<input required value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} /></label><label>Mật khẩu<input type="password" minLength="8" required value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} /></label></>}<label>Họ<input required={!selectedUser} value={form.firstName} onChange={(event) => setForm({ ...form, firstName: event.target.value })} /></label><label>Tên<input value={form.lastName} onChange={(event) => setForm({ ...form, lastName: event.target.value })} /></label><label>Email<input type="email" required value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label><label>Số điện thoại<input value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} placeholder="0xxxxxxxxx" /></label><label>Role<select value={form.roleId} onChange={(event) => setForm({ ...form, roleId: event.target.value })}><option value="">Role mặc định</option>{roles.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}</select></label></div><div className="admin-form-actions"><button className="admin-primary-button" disabled={busy === 'user-save'}>{selectedUser ? 'Lưu thay đổi' : 'Tạo tài khoản'}</button></div></form>{selectedUser && <><section className="admin-danger-actions"><h3>Thao tác tài khoản</h3><div>{selectedUser.userStatus === 'LOCKED' ? can('ADMIN_UNLOCK') && <button type="button" onClick={() => void onAction('unlock')}>Mở khóa</button> : can('ADMIN_LOCK') && <button type="button" onClick={() => void onAction('lock')}>Khóa tài khoản</button>}{can('ADMIN_DELETE_AVATAR') && <button type="button" onClick={() => void onAction('avatar')}>Xóa avatar</button>}{can('ADMIN_DELETE_USER') && <button className="is-danger" type="button" onClick={() => void onAction('delete')}>Xóa người dùng</button>}</div></section>{can('ADMIN_VIEW_USER_ADDRESSES') && <section className="admin-address-section"><header><h3>Địa chỉ người dùng</h3><span>{addresses.length}</span></header>{addresses.map((address) => <article key={address.id}><div><strong>{address.houseNumber} {address.street}</strong><p>{[address.ward, address.district, address.city, address.country].filter(Boolean).join(', ')}</p></div><span>{can('ADMIN_UPDATE_USER_ADDRESS') && <button type="button" onClick={() => void onAddressEdit(address.id)}>Sửa</button>}{can('ADMIN_DELETE_USER_ADDRESS') && <button className="is-danger" type="button" onClick={() => void onAddressDelete(address.id)}>Xóa</button>}</span></article>)}{!addresses.length && <EmptyState icon="globe">Người dùng chưa có địa chỉ.</EmptyState>}{editingAddress && <form className="admin-form admin-address-form" onSubmit={onAddressSave}><h3>Cập nhật địa chỉ</h3><div className="admin-form-grid">{ADDRESS_FIELDS.map((key) => <label key={key}>{key}<input value={editingAddress[key] || ''} onChange={(event) => setEditingAddress({ ...editingAddress, [key]: event.target.value })} /></label>)}</div><div className="admin-form-actions"><button className="admin-primary-button">Lưu địa chỉ</button><button type="button" onClick={() => setEditingAddress(null)}>Hủy</button></div></form>}</section>}</>}</div></aside></div>
}

export default AdminPage
