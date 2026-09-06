import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Brand from '../Brand.jsx'
import { useAuth } from '../../context/auth-context.js'
import { useLanguage } from '../../context/language-context.js'
import ChatIcon from './ChatIcon.jsx'

const SETTINGS_ITEMS = [
  ['profile', 'edit', 'profile'],
  ['addresses', 'globe', 'addressSettings'],
  ['contact', 'users', 'contactSettings'],
  ['security', 'shield', 'securitySettings'],
  ['keys', 'settings', 'encryptionSettings'],
]

function initials(person) {
  const source = [person?.firstName, person?.lastName].filter(Boolean).join(' ') || person?.username || 'CW'
  return source.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

function displayName(person) {
  return [person?.firstName, person?.lastName].filter(Boolean).join(' ').trim() || person?.username || ''
}

function RailAvatar({ person, online = false }) {
  const [failed, setFailed] = useState(false)
  return <span className="cw-avatar cw-avatar--small">
    {person?.avatar && !failed ? <img src={person.avatar} alt="" onError={() => setFailed(true)} /> : <span>{initials(person)}</span>}
    <i className={online ? 'is-online' : ''} />
  </span>
}

export default function AppRail({
  activeSection = '',
  online = false,
  totalUnreadMessages = 0,
  friendRequestCount = 0,
  worldNotificationCount = 0,
  onSelectSection,
  onOpenWorld,
  onSelectSettings,
}) {
  const { user, logout } = useAuth()
  const { language, setLanguage, t } = useLanguage()
  const navigate = useNavigate()
  const menuRef = useRef(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const permissions = useMemo(() => new Set(user?.permissions || []), [user?.permissions])
  const isAdmin = String(user?.role || '').toUpperCase().includes('ADMIN') || permissions.has('ADMIN_SEND-MESSAGE')

  useEffect(() => {
    if (!menuOpen) return undefined
    const closeMenu = (event) => {
      if (!menuRef.current?.contains(event.target)) setMenuOpen(false)
    }
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('pointerdown', closeMenu)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeMenu)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [menuOpen])

  const selectSection = (section) => {
    setMenuOpen(false)
    if (onSelectSection) onSelectSection(section)
    else navigate(section === 'chat' ? '/chat' : `/chat?section=${section}`)
  }

  const selectSettings = (tab) => {
    setMenuOpen(false)
    if (onSelectSettings) onSelectSettings(tab)
    else navigate(`/settings?tab=${tab}`)
  }

  const openWorld = () => {
    if (onOpenWorld) onOpenWorld()
    else navigate('/chat?world=1')
  }

  const handleLogout = async () => {
    await logout().catch(() => {})
    navigate('/login', { replace: true })
  }

  return <aside className="chat-rail">
    <Brand className="chat-brand" />
    <nav aria-label="Chat navigation">
      <button className={`${activeSection === 'chat' ? 'is-active ' : ''}rail-badge`} type="button" title={t('conversations')} onClick={() => selectSection('chat')}><ChatIcon name="chat" />{totalUnreadMessages > 0 && <span>{totalUnreadMessages > 99 ? '99+' : totalUnreadMessages}</span>}</button>
      <button className={`${activeSection === 'friends' ? 'is-active ' : ''}rail-badge`} type="button" title={t('friends')} onClick={() => selectSection('friends')}><ChatIcon name="users" />{friendRequestCount > 0 && <span>{friendRequestCount > 99 ? '99+' : friendRequestCount}</span>}</button>
      <button className={`${activeSection === 'notifications' ? 'is-active ' : ''}rail-badge`} type="button" title={t('notifications')} onClick={() => selectSection('notifications')}><ChatIcon name="bell" />{friendRequestCount + worldNotificationCount > 0 && <span>{friendRequestCount + worldNotificationCount}</span>}</button>
      {isAdmin && <button type="button" title={t('worldShort')} onClick={openWorld}><ChatIcon name="globe" /></button>}
      {isAdmin && <button type="button" title={t('adminConsole')} onClick={() => navigate('/admin')}><ChatIcon name="shield" /></button>}
    </nav>
    <div className="chat-rail__bottom">
      <button className="language-button" type="button" onClick={() => setLanguage(language === 'vi' ? 'en' : 'vi')} title={t('language')}>{language.toUpperCase()}</button>
      <button type="button" title={t('logout')} onClick={handleLogout}><ChatIcon name="logout" /></button>
      <div className="account-menu-anchor" ref={menuRef}>
        {menuOpen && <div className="account-menu" role="menu">
          <header><RailAvatar person={user} online={online} /><span><strong>{displayName(user)}</strong><small>@{user.username}</small></span></header>
          {SETTINGS_ITEMS.map(([tab, icon, label]) => <button key={tab} type="button" role="menuitem" onClick={() => selectSettings(tab)}><ChatIcon name={icon} size={16} /><span>{t(label)}</span></button>)}
          {isAdmin && <><i /><button type="button" role="menuitem" onClick={() => navigate('/admin')}><ChatIcon name="shield" size={16} /><span>{t('adminConsole')}</span></button></>}
        </div>}
        <button className={`account-avatar-button${activeSection === 'settings' ? ' is-active' : ''}`} type="button" aria-label={t('accountMenu')} aria-expanded={menuOpen} onClick={() => setMenuOpen((current) => !current)}><RailAvatar person={user} online={online} /></button>
      </div>
    </div>
  </aside>
}
