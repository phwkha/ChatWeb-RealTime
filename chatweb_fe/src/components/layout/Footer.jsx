import { Link } from 'react-router-dom'
import Brand from '../Brand.jsx'

const FOOTER_LINKS = [
  {
    title: 'Sản phẩm',
    links: [{ label: 'Tính năng', href: '/home#features' }, { label: 'Trải nghiệm', href: '/experience' }, { label: 'Bảo mật', href: '/security' }, { label: 'Cập nhật', href: '/home#start' }],
  },
  {
    title: 'Hỗ trợ',
    links: [{ label: 'Trung tâm trợ giúp', href: '/home#start' }, { label: 'Liên hệ', href: '/home#start' }, { label: 'Trạng thái', href: '/home#start' }, { label: 'Cộng đồng', href: '/home#start' }],
  },
  {
    title: 'Pháp lý',
    links: [{ label: 'Quyền riêng tư', href: '/security' }, { label: 'Điều khoản', href: '/home#start' }, { label: 'Cookie', href: '/security' }],
  },
]

function Footer() {
  return (
    <footer className="site-footer">
      <div className="container">
        <div className="site-footer__main">
          <div className="site-footer__intro">
            <Brand className="brand--footer" />
            <p>Nơi mọi câu chuyện được bắt đầu và mọi khoảng cách trở nên gần hơn.</p>
            <div className="social-links" aria-label="Mạng xã hội">
              <a href="#facebook" aria-label="Facebook">f</a>
              <a href="#instagram" aria-label="Instagram">◎</a>
              <a href="#twitter" aria-label="X">𝕏</a>
            </div>
          </div>

          <div className="site-footer__links">
            {FOOTER_LINKS.map((group) => (
              <div key={group.title} className="footer-column">
                <h3>{group.title}</h3>
                {group.links.map((link) => (
                  <Link key={link.label} to={link.href}>{link.label}</Link>
                ))}
              </div>
            ))}
          </div>
        </div>

        <div className="site-footer__bottom">
          <p>© {new Date().getFullYear()} ChatWeb. Made for meaningful conversations.</p>
          <div className="footer-status">
            <span aria-hidden="true" />
            Tất cả hệ thống hoạt động bình thường
          </div>
        </div>
      </div>
    </footer>
  )
}

export default Footer
