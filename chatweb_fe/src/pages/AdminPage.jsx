import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Brand from '../components/Brand.jsx'
import { useAuth } from '../context/auth-context.js'
import { adminApi } from '../services/adminApi.js'
import { getErrorMessage } from '../services/apiClient.js'
import '../styles/workspace.css'

const EMPTY_USER = { username: '', password: '', firstName: '', lastName: '', email: '', phone: '', roleId: '' }
const EMPTY_ROLE = { name: '', description: '', permissionIds: [] }
const ADDRESS_FIELDS = ['houseNumber', 'street', 'ward', 'district', 'city', 'country', 'postalCode']

function addressPayload(address) {
  return Object.fromEntries(ADDRESS_FIELDS.map((key) => [key, address?.[key] || '']))
}

function AdminPage() {
  const { user } = useAuth()
  const [tab, setTab] = useState('users')
  const [users, setUsers] = useState([])
  const [roles, setRoles] = useState([])
  const [permissions, setPermissions] = useState([])
  const [selectedUser, setSelectedUser] = useState(null)
  const [selectedAddresses, setSelectedAddresses] = useState([])
  const [editingAddress, setEditingAddress] = useState(null)
  const [userForm, setUserForm] = useState(EMPTY_USER)
  const [roleForm, setRoleForm] = useState(EMPTY_ROLE)
  const [editingRoleId, setEditingRoleId] = useState(null)
  const [filters, setFilters] = useState({ keyword: '', role: '', status: '', gender: '', authProvider: '', page: 0, size: 20 })
  const [onlineOnly, setOnlineOnly] = useState(false)
  const [emailForm, setEmailForm] = useState({ to: '', subject: '', text: '' })
  const [advancedForm, setAdvancedForm] = useState({ username: '', firstName: '', lastName: '', city: '', country: '' })
  const [advancedResults, setAdvancedResults] = useState([])
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState('')

  const isAdmin = String(user?.role || '').toUpperCase().includes('ADMIN')
    || (user?.permissions || []).some((permission) => /^(ADMIN_|ROLE_|SEND_EMAIL)/.test(permission))
  const notify = useCallback((message, tone = 'success') => setNotice({ message, tone }), [])

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

  const loadUsers = useCallback(async () => {
    setBusy('users')
    try {
      const response = onlineOnly
        ? await adminApi.getOnlineUsers(filters.page, filters.size)
        : await adminApi.getUsers(filters)
      setUsers(response?.data?.content || [])
    } catch (error) {
      notify(getErrorMessage(error, 'Không thể tải danh sách người dùng.'), 'error')
    } finally {
      setBusy('')
    }
  }, [filters, notify, onlineOnly])

  const loadRoles = useCallback(async () => {
    const [roleResponse, permissionResponse] = await Promise.allSettled([adminApi.getRoles(), adminApi.getPermissions()])
    if (roleResponse.status === 'fulfilled') setRoles(roleResponse.value?.data || [])
    if (permissionResponse.status === 'fulfilled') setPermissions(permissionResponse.value?.data || [])
  }, [])

  // oxlint-disable-next-line react/set-state-in-effect -- the console hydrates protected remote resources.
  useEffect(() => { if (isAdmin) { void loadUsers(); void loadRoles() } }, [isAdmin, loadRoles, loadUsers])

  const openUser = async (username) => {
    const [detail, addressList] = await Promise.all([
      run('user-detail', () => adminApi.getUser(username)),
      run('user-addresses', () => adminApi.getAddresses(username)),
    ])
    if (!detail?.data) return
    setSelectedUser(detail.data)
    setSelectedAddresses(addressList?.data || detail.data.addresses || [])
    setUserForm({
      username: detail.data.username, password: '', firstName: detail.data.firstName || '',
      lastName: detail.data.lastName || '', email: detail.data.email || '', phone: detail.data.phone || '',
      roleId: roles.find((role) => role.name === detail.data.role)?.id || '',
    })
  }

  const saveUser = async (event) => {
    event.preventDefault()
    const body = { ...userForm, roleId: userForm.roleId ? Number(userForm.roleId) : null, phone: userForm.phone || null }
    const response = selectedUser
      ? await run('user-save', () => adminApi.updateUser(selectedUser.username, {
        firstName: body.firstName, lastName: body.lastName, email: body.email, phone: body.phone, roleId: body.roleId,
      }), 'Đã cập nhật người dùng.')
      : await run('user-save', () => adminApi.createUser(body), 'Đã tạo người dùng.')
    if (response) { setSelectedUser(null); setUserForm(EMPTY_USER); await loadUsers() }
  }

  const userAction = async (action) => {
    if (!selectedUser) return
    const username = selectedUser.username
    const operations = {
      lock: () => adminApi.lockUser(username), unlock: () => adminApi.unlockUser(username),
      avatar: () => adminApi.deleteAvatar(username), delete: () => adminApi.deleteUser(username),
    }
    if (['delete', 'avatar'].includes(action) && !window.confirm(action === 'delete' ? `Xóa tài khoản @${username}?` : `Xóa avatar của @${username}?`)) return
    const response = await run(`user-${action}`, operations[action], 'Thao tác thành công.')
    if (response) {
      if (action === 'delete') { setSelectedUser(null); setUserForm(EMPTY_USER) }
      else await openUser(username)
      await loadUsers()
    }
  }

  const beginAddressEdit = async (id) => {
    const response = await run('admin-address-detail', () => adminApi.getAddress(selectedUser.username, id))
    if (response?.data) setEditingAddress(response.data)
  }

  const saveAddress = async (event) => {
    event.preventDefault()
    const response = await run('admin-address-save', () => adminApi.updateAddress(selectedUser.username, editingAddress.id, addressPayload(editingAddress)), 'Đã cập nhật địa chỉ.')
    if (response) { setEditingAddress(null); await openUser(selectedUser.username) }
  }

  const deleteAddress = async (id) => {
    if (!window.confirm('Xóa địa chỉ của người dùng này?')) return
    const response = await run('admin-address-delete', () => adminApi.deleteAddress(selectedUser.username, id), 'Đã xóa địa chỉ.')
    if (response) await openUser(selectedUser.username)
  }

  const saveRole = async (event) => {
    event.preventDefault()
    const body = { ...roleForm, permissionIds: roleForm.permissionIds.map(Number) }
    const response = editingRoleId
      ? await run('role-save', () => adminApi.updateRole(editingRoleId, body), 'Đã cập nhật role.')
      : await run('role-save', () => adminApi.createRole(body), 'Đã tạo role.')
    if (response) { setRoleForm(EMPTY_ROLE); setEditingRoleId(null); await loadRoles() }
  }

  const editRole = (role) => {
    setEditingRoleId(role.id)
    setRoleForm({ name: role.name, description: role.description || '', permissionIds: (role.permissions || []).map((permission) => permission.id) })
  }

  const deleteRole = async (id) => {
    if (!window.confirm('Xóa role này?')) return
    const response = await run('role-delete', () => adminApi.deleteRole(id), 'Đã xóa role.')
    if (response) await loadRoles()
  }

  const sendEmail = async (event) => {
    event.preventDefault()
    const response = await run('email', () => adminApi.sendEmail(emailForm), 'Email đang được gửi.')
    if (response) setEmailForm({ to: '', subject: '', text: '' })
  }

  const advancedSearch = async (event) => {
    event.preventDefault()
    const userCriteria = [advancedForm.username && `username~*${advancedForm.username}*`, advancedForm.firstName && `firstName~*${advancedForm.firstName}*`, advancedForm.lastName && `lastName~*${advancedForm.lastName}*`]
    const addressCriteria = [advancedForm.city && `city~*${advancedForm.city}*`, advancedForm.country && `country~*${advancedForm.country}*`]
    const response = await run('advanced', () => adminApi.advancedSearch({ user: userCriteria, address: addressCriteria }), 'Đã tìm kiếm.')
    if (response) setAdvancedResults(response?.data?.content || [])
  }

  if (!isAdmin) return <main className="workspace-page"><header className="workspace-topbar"><Brand /><Link to="/chat">← Trò chuyện</Link></header><section className="access-denied"><h1>Không có quyền truy cập</h1><p>Tài khoản của bạn không có vai trò quản trị.</p></section></main>

  return <main className="workspace-page">
    <header className="workspace-topbar"><Brand /><nav><Link to="/chat">← Trò chuyện</Link><Link to="/settings">Cài đặt</Link></nav></header>
    <div className="workspace-layout">
      <aside className="workspace-sidebar"><div className="workspace-user"><span>A</span><div><strong>Admin Console</strong><small>@{user.username}</small></div></div><h1>Quản trị</h1>{[['users', 'Người dùng'], ['roles', 'Role & permission'], ['search', 'Tìm kiếm nâng cao'], ['email', 'Gửi email']].map(([value, label]) => <button key={value} className={tab === value ? 'is-active' : ''} type="button" onClick={() => setTab(value)}>{label}</button>)}</aside>
      <section className="workspace-content">
        {notice && <div className={`workspace-notice is-${notice.tone}`}>{notice.message}<button type="button" onClick={() => setNotice(null)}>×</button></div>}

        {tab === 'users' && <div className="workspace-section"><header><small>ADMIN USERS</small><h2>Quản lý người dùng</h2><p>Tìm kiếm, tạo mới, khóa và chỉnh sửa tài khoản.</p></header>
          <form className="admin-filter" onSubmit={(event) => { event.preventDefault(); void loadUsers() }}><input placeholder="Username, email hoặc tên..." value={filters.keyword} onChange={(e) => setFilters({ ...filters, keyword: e.target.value })} /><select value={filters.role} onChange={(e) => setFilters({ ...filters, role: e.target.value })}><option value="">Mọi role</option>{roles.map((role) => <option key={role.id} value={role.name}>{role.name}</option>)}</select><select value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}><option value="">Mọi trạng thái</option>{['ACTIVE', 'LOCKED', 'UNVERIFIED', 'INACTIVE'].map((status) => <option key={status}>{status}</option>)}</select><select value={filters.gender} onChange={(e) => setFilters({ ...filters, gender: e.target.value })}><option value="">Mọi giới tính</option><option value="MAN">Nam</option><option value="WOMAN">Nữ</option></select><select value={filters.authProvider} onChange={(e) => setFilters({ ...filters, authProvider: e.target.value })}><option value="">Mọi đăng nhập</option>{['LOCAL', 'GOOGLE', 'FACEBOOK', 'GITHUB'].map((provider) => <option key={provider}>{provider}</option>)}</select><label className="inline-check"><input type="checkbox" checked={onlineOnly} onChange={(e) => setOnlineOnly(e.target.checked)} /> Chỉ online</label><button className="workspace-button">Lọc</button></form>
          <div className="data-table"><div className="data-table__head"><span>Người dùng</span><span>Role</span><span>Trạng thái</span><span /></div>{users.map((item) => <div key={item.username}><span><strong>{item.firstName} {item.lastName}</strong><small>@{item.username} · {item.email}</small></span><span>{item.role}</span><span><i className={`status-dot is-${String(item.userStatus).toLowerCase()}`} />{item.userStatus}</span><button type="button" onClick={() => openUser(item.username)}>Quản lý</button></div>)}{!users.length && <p className="workspace-empty">{busy === 'users' ? 'Đang tải...' : 'Không có người dùng phù hợp.'}</p>}</div>
          <button className="workspace-button" type="button" onClick={() => { setSelectedUser(null); setUserForm(EMPTY_USER) }}>+ Tạo người dùng</button>
          <form className="workspace-card workspace-form" onSubmit={saveUser}><h3>{selectedUser ? `Chỉnh sửa @${selectedUser.username}` : 'Tạo người dùng mới'}</h3><div className="form-grid">{!selectedUser && <><label>Username<input required value={userForm.username} onChange={(e) => setUserForm({ ...userForm, username: e.target.value })} /></label><label>Mật khẩu<input type="password" minLength="8" required value={userForm.password} onChange={(e) => setUserForm({ ...userForm, password: e.target.value })} /></label></>}<label>Họ<input required={!selectedUser} value={userForm.firstName} onChange={(e) => setUserForm({ ...userForm, firstName: e.target.value })} /></label><label>Tên<input value={userForm.lastName} onChange={(e) => setUserForm({ ...userForm, lastName: e.target.value })} /></label><label>Email<input type="email" required value={userForm.email} onChange={(e) => setUserForm({ ...userForm, email: e.target.value })} /></label><label>Số điện thoại<input value={userForm.phone} onChange={(e) => setUserForm({ ...userForm, phone: e.target.value })} /></label><label>Role<select value={userForm.roleId} onChange={(e) => setUserForm({ ...userForm, roleId: e.target.value })}><option value="">Mặc định</option>{roles.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}</select></label></div><div className="button-row"><button className="workspace-button">{selectedUser ? 'Lưu người dùng' : 'Tạo tài khoản'}</button>{selectedUser && <><button className="workspace-button is-secondary" type="button" onClick={() => userAction(selectedUser.userStatus === 'LOCKED' ? 'unlock' : 'lock')}>{selectedUser.userStatus === 'LOCKED' ? 'Mở khóa' : 'Khóa'}</button><button className="workspace-button is-secondary" type="button" onClick={() => userAction('avatar')}>Xóa avatar</button><button className="workspace-button is-danger" type="button" onClick={() => userAction('delete')}>Xóa user</button></>}</div></form>
          {selectedUser && <div className="workspace-card"><h3>Địa chỉ của @{selectedUser.username}</h3><div className="address-list">{selectedAddresses.map((address) => <article key={address.id}><strong>{address.houseNumber} {address.street}</strong><p>{address.ward}, {address.district}, {address.city}</p><div><button type="button" onClick={() => beginAddressEdit(address.id)}>Chi tiết / Sửa</button><button className="is-danger" type="button" onClick={() => deleteAddress(address.id)}>Xóa</button></div></article>)}</div>{editingAddress && <form className="workspace-form" onSubmit={saveAddress}><div className="form-grid">{ADDRESS_FIELDS.map((key) => <label key={key}>{key}<input value={editingAddress[key] || ''} onChange={(e) => setEditingAddress({ ...editingAddress, [key]: e.target.value })} /></label>)}</div><button className="workspace-button">Lưu địa chỉ</button></form>}</div>}
        </div>}

        {tab === 'roles' && <div className="workspace-section"><header><small>RBAC</small><h2>Role & permission</h2><p>Tạo vai trò và cấu hình quyền truy cập chi tiết.</p></header><div className="role-grid">{roles.map((role) => <article className="workspace-card" key={role.id}><h3>{role.name}</h3><p>{role.description}</p><small>{(role.permissions || []).length} quyền</small><div className="button-row"><button type="button" className="workspace-button is-secondary" onClick={() => editRole(role)}>Sửa</button><button type="button" className="workspace-button is-danger" onClick={() => deleteRole(role.id)}>Xóa</button></div></article>)}</div><form className="workspace-card workspace-form" onSubmit={saveRole}><h3>{editingRoleId ? 'Cập nhật role' : 'Tạo role mới'}</h3><label>Tên role<input required value={roleForm.name} onChange={(e) => setRoleForm({ ...roleForm, name: e.target.value })} /></label><label>Mô tả<textarea rows="3" value={roleForm.description} onChange={(e) => setRoleForm({ ...roleForm, description: e.target.value })} /></label><fieldset className="permission-grid"><legend>Permissions</legend>{permissions.map((permission) => <label key={permission.id}><input type="checkbox" checked={roleForm.permissionIds.includes(permission.id)} onChange={(e) => setRoleForm({ ...roleForm, permissionIds: e.target.checked ? [...roleForm.permissionIds, permission.id] : roleForm.permissionIds.filter((id) => id !== permission.id) })} /><span><strong>{permission.name}</strong><small>{permission.description}</small></span></label>)}</fieldset><div className="button-row"><button className="workspace-button">{editingRoleId ? 'Lưu role' : 'Tạo role'}</button>{editingRoleId && <button className="workspace-button is-secondary" type="button" onClick={() => { setEditingRoleId(null); setRoleForm(EMPTY_ROLE) }}>Hủy</button>}</div></form></div>}

        {tab === 'search' && <div className="workspace-section"><header><small>SPECIFICATIONS</small><h2>Tìm kiếm nâng cao</h2><p>Kết hợp điều kiện user và địa chỉ từ API specifications.</p></header><form className="workspace-card workspace-form" onSubmit={advancedSearch}><div className="form-grid">{Object.entries({ username: 'Username chứa', firstName: 'Họ chứa', lastName: 'Tên chứa', city: 'Thành phố chứa', country: 'Quốc gia chứa' }).map(([key, label]) => <label key={key}>{label}<input value={advancedForm[key]} onChange={(e) => setAdvancedForm({ ...advancedForm, [key]: e.target.value })} /></label>)}</div><button className="workspace-button">Tìm kiếm</button></form><div className="data-table">{advancedResults.map((item) => <div key={item.username}><span><strong>{item.firstName} {item.lastName}</strong><small>@{item.username}</small></span><span /><span /><button type="button" onClick={() => { setTab('users'); void openUser(item.username) }}>Quản lý</button></div>)}</div></div>}

        {tab === 'email' && <div className="workspace-section"><header><small>EMAIL</small><h2>Gửi email hệ thống</h2><p>Gửi email văn bản bằng quyền SEND_EMAIL.</p></header><form className="workspace-card workspace-form" onSubmit={sendEmail}><label>Người nhận<input type="email" required value={emailForm.to} onChange={(e) => setEmailForm({ ...emailForm, to: e.target.value })} /></label><label>Tiêu đề<input required value={emailForm.subject} onChange={(e) => setEmailForm({ ...emailForm, subject: e.target.value })} /></label><label>Nội dung<textarea rows="10" required value={emailForm.text} onChange={(e) => setEmailForm({ ...emailForm, text: e.target.value })} /></label><button className="workspace-button" disabled={busy === 'email'}>{busy === 'email' ? 'Đang gửi...' : 'Gửi email'}</button></form></div>}
      </section>
    </div>
  </main>
}

export default AdminPage
