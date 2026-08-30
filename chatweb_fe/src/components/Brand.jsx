import { Link } from 'react-router-dom'

function Brand({ className = '' }) {
  return (
    <Link className={`brand${className ? ` ${className}` : ''}`} to="/" aria-label="ChatWeb - Trang chủ">
      <img className="brand__image" src="/logo_chatweb.png" alt="" aria-hidden="true" />
      <span>ChatWeb</span>
    </Link>
  )
}

export default Brand
