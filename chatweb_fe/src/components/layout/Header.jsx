import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../context/auth-context.js'
import Brand from '../Brand.jsx'

const NAV_ITEMS = [
  { label: 'Tính năng', href: '/#features' },
  { label: 'Trải nghiệm', href: '/#experience' },
  { label: 'Bảo mật', href: '/#security' },
]

function getInitials(user) {
  const fullName = [user?.firstName, user?.lastName].filter(Boolean).join(' ').trim()
  const source = fullName || user?.username || 'U'
  return source.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()
}

function UserAvatar({ user }) {
  const [imageFailed, setImageFailed] = useState(false)

  if (user?.avatar && !imageFailed) {
    return <img src={user.avatar} alt={`Ảnh đại diện của ${user.username}`} onError={() => setImageFailed(true)} />
  }

  return <span>{getInitials(user)}</span>
}

function Header() {
  const { user, isInitializing, logout } = useAuth()
  const [isOpen, setIsOpen] = useState(false)
  const [isScrolled, setIsScrolled] = useState(() => window.scrollY > 18)
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false)
  const userMenuRef = useRef(null)

  useEffect(() => {
    const handleScroll = () => setIsScrolled(window.scrollY > 18)
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  useEffect(() => {
    document.body.classList.toggle('menu-open', isOpen)
    return () => document.body.classList.remove('menu-open')
  }, [isOpen])

  useEffect(() => {
    const handlePointerDown = (event) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target)) setIsUserMenuOpen(false)
    }
    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [])

  const closeMenu = () => {
    setIsOpen(false)
    setIsUserMenuOpen(false)
  }

  const handleLogout = async () => {
    closeMenu()
    try {
      await logout()
    } catch {
      // AuthProvider still clears the local user when the server is unavailable.
    }
  }

  return (
    <header className={`site-header${isScrolled ? ' site-header--scrolled' : ''}`}>
      <div className="container site-header__inner">
        <Brand />

        <button
          className={`menu-toggle${isOpen ? ' menu-toggle--open' : ''}`}
          type="button"
          aria-label={isOpen ? 'Đóng menu' : 'Mở menu'}
          aria-expanded={isOpen}
          aria-controls="main-navigation"
          onClick={() => setIsOpen((current) => !current)}
        >
          <span />
          <span />
        </button>

        <nav
          id="main-navigation"
          className={`main-nav${isOpen ? ' main-nav--open' : ''}`}
          aria-label="Điều hướng chính"
        >
          <div className="main-nav__links">
            {NAV_ITEMS.map((item) => (
              <a key={item.href} href={item.href} onClick={closeMenu}>
                {item.label}
              </a>
            ))}
          </div>
          <div className="main-nav__actions">
            {isInitializing && <span className="header-user-skeleton" aria-label="Đang kiểm tra đăng nhập" />}
            {!isInitializing && !user && (
              <>
                <Link className="text-link" to="/login" onClick={closeMenu}>Đăng nhập</Link>
                <Link className="button button--small button--primary" to="/register" onClick={closeMenu}>
                  Bắt đầu ngay <span aria-hidden="true">↗</span>
                </Link>
              </>
            )}
            {!isInitializing && user && (
              <div className="header-user" ref={userMenuRef}>
                <button
                  className="header-user__trigger"
                  type="button"
                  aria-label="Mở menu tài khoản"
                  aria-expanded={isUserMenuOpen}
                  onClick={() => setIsUserMenuOpen((current) => !current)}
                >
                  <span className="header-user__avatar"><UserAvatar key={user.avatar || 'avatar-fallback'} user={user} /></span>
                  <span className="header-user__identity">
                    <strong>{user.firstName || user.username}</strong>
                    <small>Đang hoạt động</small>
                  </span>
                  <span className="header-user__chevron" aria-hidden="true">⌄</span>
                </button>
                <div className={`user-menu${isUserMenuOpen ? ' user-menu--open' : ''}`}>
                  <div className="user-menu__summary">
                    <span className="header-user__avatar"><UserAvatar key={user.avatar || 'menu-avatar-fallback'} user={user} /></span>
                    <div><strong>{[user.firstName, user.lastName].filter(Boolean).join(' ') || user.username}</strong><small>{user.email}</small></div>
                  </div>
                  <a href="#experience" onClick={closeMenu}><span>◌</span> Mở trò chuyện</a>
                  <button type="button" onClick={handleLogout}><span>↪</span> Đăng xuất</button>
                </div>
              </div>
            )}
          </div>
        </nav>
      </div>
    </header>
  )
}

export default Header
