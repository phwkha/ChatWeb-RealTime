import { Link } from 'react-router-dom'

function Brand({ className = '', to = '/home', ariaLabel = 'ChatWeb - Trang chủ' }) {
  return (
    <Link className={`brand${className ? ` ${className}` : ''}`} to={to} aria-label={ariaLabel}>
      <img className="brand__image" src="/logo_chatweb.png" alt="" aria-hidden="true" />
      <span>ChatWeb</span>
    </Link>
  )
}

export default Brand
