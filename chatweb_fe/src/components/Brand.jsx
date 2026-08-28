import { Link } from 'react-router-dom'

function Brand({ className = '' }) {
  return (
    <Link className={`brand${className ? ` ${className}` : ''}`} to="/" aria-label="ChatWeb - Trang chủ">
      <span className="brand__mark" aria-hidden="true">
        <span />
        <span />
      </span>
      <span>ChatWeb</span>
    </Link>
  )
}

export default Brand
