const FOOTER_LINKS = [
  {
    title: 'Sản phẩm',
    links: ['Tính năng', 'Bảo mật', 'Ứng dụng', 'Cập nhật'],
  },
  {
    title: 'Hỗ trợ',
    links: ['Trung tâm trợ giúp', 'Liên hệ', 'Trạng thái', 'Cộng đồng'],
  },
  {
    title: 'Pháp lý',
    links: ['Quyền riêng tư', 'Điều khoản', 'Cookie'],
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
                  <a key={link} href="#top">{link}</a>
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
import Brand from '../Brand.jsx'
