import { Link } from 'react-router-dom'
import Footer from '../components/layout/Footer.jsx'
import Header from '../components/layout/Header.jsx'
import '../styles/marketing.css'

const LAYERS = [
  ['01', 'Kiểm soát tài khoản', 'Bạn chủ động với phiên đăng nhập, thông tin cá nhân và những thiết bị đang truy cập.'],
  ['02', 'Bảo vệ trong giao tiếp', 'Các lớp xác thực và phân quyền giúp nội dung của bạn chỉ đến đúng nơi cần đến.'],
  ['03', 'Minh bạch theo mặc định', 'Trạng thái, quyền hạn và những lựa chọn quan trọng luôn được trình bày rõ ràng.'],
]

function SecurityPage() {
  return (
    <div className="marketing-page marketing-page--security">
      <Header />
      <main>
        <section className="marketing-hero security-hero">
          <div className="security-hero__wash" aria-hidden="true" />
          <div className="container security-hero__inner">
            <div className="security-hero__copy"><span className="section-kicker section-kicker--light">BẢO MẬT CHATWEB</span><h1>Không gian riêng tư để bạn <em>nói điều thật lòng.</em></h1><p>Quyền riêng tư không nên là một cài đặt khó tìm. ChatWeb đặt sự rõ ràng và quyền kiểm soát vào chính trải nghiệm hằng ngày.</p><Link className="button button--light button--large" to="/register">Bắt đầu an tâm <span>↗</span></Link></div>
            <div className="security-visual" aria-label="Minh họa các lớp bảo vệ"><div className="security-visual__halo security-visual__halo--outer" /><div className="security-visual__halo security-visual__halo--inner" /><div className="security-visual__core"><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="3" /><path d="M8.5 10V7.5a3.5 3.5 0 0 1 7 0V10" /><circle cx="12" cy="15" r="1" /></svg><strong>Riêng tư<br />từ thiết kế</strong></div><span className="security-chip security-chip--top">✓ Phiên an toàn</span><span className="security-chip security-chip--right">◌ Bạn kiểm soát</span><span className="security-chip security-chip--bottom">✦ Minh bạch</span></div>
          </div>
        </section>

        <section className="security-principles section"><div className="container"><div className="section-heading marketing-heading"><div><span className="section-kicker">BẢO VỆ CÓ CHỦ ĐÍCH</span><h2>Ba lớp rõ ràng.<br /><em>Một cảm giác an tâm.</em></h2></div><p>ChatWeb xây dựng trải nghiệm bảo mật quanh những điều quan trọng nhất: ai được truy cập, dữ liệu đi đâu và bạn có thể kiểm soát điều gì.</p></div><div className="security-layer-grid">{LAYERS.map(([number, title, description]) => <article className="security-layer" key={number}><span className="security-layer__number">{number}</span><div className="security-layer__icon">{number === '01' ? '◌' : number === '02' ? '⌁' : '✓'}</div><h3>{title}</h3><p>{description}</p><span className="security-layer__arrow">↗</span></article>)}</div></div></section>

        <section className="security-promise section"><div className="container security-promise__inner"><div className="promise-panel"><span className="section-kicker section-kicker--light">CAM KẾT CỦA CHATWEB</span><h2>Công nghệ nên<br />làm bạn <em>tự tin hơn.</em></h2><p>Chúng mình liên tục rà soát các lớp bảo vệ để ChatWeb luôn là nơi bạn có thể kết nối thoải mái, không phải đánh đổi sự riêng tư.</p><div className="promise-metrics"><div><strong>24/7</strong><span>Theo dõi liên tục</span></div><div><strong>100%</strong><span>Quyền kiểm soát</span></div><div><strong>0</strong><span>Quảng cáo chen ngang</span></div></div></div><div className="promise-list"><div><span>01</span><p><strong>Quyền riêng tư dễ hiểu</strong><small>Ngôn ngữ rõ ràng, lựa chọn dễ tìm.</small></p></div><div><span>02</span><p><strong>Phiên đăng nhập minh bạch</strong><small>Biết tài khoản của bạn đang hoạt động ở đâu.</small></p></div><div><span>03</span><p><strong>Trải nghiệm không bị làm phiền</strong><small>Tập trung vào cuộc trò chuyện, không phải quảng cáo.</small></p></div></div></div></section>

        <section className="marketing-cta section"><div className="container marketing-cta__card"><span className="section-kicker">KẾT NỐI VỚI SỰ AN TÂM</span><h2>Câu chuyện của bạn.<br /><em>Chỉ thuộc về bạn.</em></h2><Link className="button button--dark button--large" to="/register">Tham gia ChatWeb <span>↗</span></Link></div></section>
      </main>
      <Footer />
    </div>
  )
}

export default SecurityPage
