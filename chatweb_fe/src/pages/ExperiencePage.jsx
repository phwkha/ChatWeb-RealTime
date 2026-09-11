import { Link } from 'react-router-dom'
import Footer from '../components/layout/Footer.jsx'
import Header from '../components/layout/Header.jsx'
import '../styles/marketing.css'

const JOURNEY = [
  ['01', 'Bắt đầu ở bất kỳ đâu', 'Mở ChatWeb trên màn hình bạn đang dùng và tiếp tục ngay từ nơi câu chuyện dừng lại.'],
  ['02', 'Giữ nhịp tự nhiên', 'Tin nhắn, trạng thái online và thông báo realtime giúp cuộc trò chuyện luôn liền mạch.'],
  ['03', 'Chia sẻ trọn vẹn', 'Gửi ảnh, video, phản hồi và những khoảnh khắc nhỏ mà không làm gián đoạn mạch nói chuyện.'],
]

function ExperiencePage() {
  return (
    <div className="marketing-page marketing-page--experience">
      <Header />
      <main>
        <section className="marketing-hero marketing-hero--experience">
          <div className="marketing-hero__grid" aria-hidden="true" />
          <div className="container marketing-hero__inner">
            <div className="marketing-hero__copy">
              <span className="section-kicker">TRẢI NGHIỆM CHATWEB</span>
              <h1>Một cuộc trò chuyện tốt nên <em>cảm thấy tự nhiên.</em></h1>
              <p>Từ lời chào đầu tiên đến những cuộc nói chuyện kéo dài cả tối, ChatWeb giữ mọi tương tác nhanh, rõ ràng và gần gũi.</p>
              <div className="marketing-actions">
                <Link className="button button--primary button--large" to="/register">Bắt đầu trải nghiệm <span>↗</span></Link>
                <Link className="text-link marketing-text-link" to="/home#features">Xem tính năng <span>→</span></Link>
              </div>
            </div>
            <div className="experience-board" aria-label="Bảng mô phỏng trải nghiệm ChatWeb">
              <div className="experience-board__top"><span>CHATWEB / LIVE</span><i /><i /><i /></div>
              <div className="experience-board__body">
                <aside><small>HỘI THOẠI</small><strong>Gần đây</strong><div className="board-contact is-active"><b>LN</b><span><strong>Linh Nguyễn</strong><small>Chắc chắn rồi ✨</small></span><i /></div><div className="board-contact"><b className="is-orange">TD</b><span><strong>Team Thiết kế</strong><small>Đã gửi một hình ảnh</small></span></div><div className="board-contact"><b className="is-green">GĐ</b><span><strong>Gia đình</strong><small>Hẹn cuối tuần này ❤️</small></span></div></aside>
                <div className="experience-board__chat"><div className="board-chat-head"><b>LN</b><span><strong>Linh Nguyễn</strong><small>Đang hoạt động</small></span><strong>•••</strong></div><div className="board-bubbles"><small>HÔM NAY, 10:24</small><span>Cuối tuần này cậu có rảnh không?</span><span className="is-mine">Có chứ! Mình đang định rủ mọi người đi picnic 🌿</span><span>Nhất định rồi. Gửi địa điểm cho mình nhé!</span></div><div className="board-composer"><span>Nhập tin nhắn...</span><b>↗</b></div></div>
              </div>
              <div className="experience-board__badge"><span>✓</span><div><strong>Đã đồng bộ</strong><small>Vừa xong trên mọi thiết bị</small></div></div>
            </div>
          </div>
        </section>

        <section className="journey-section section">
          <div className="container">
            <div className="section-heading marketing-heading"><div><span className="section-kicker">MỘT NHỊP KẾT NỐI</span><h2>Thiết kế để bạn<br /><em>ở lại trong câu chuyện.</em></h2></div><p>Không cần học cách dùng một công cụ phức tạp. Mọi chi tiết được đặt đúng chỗ để bạn tập trung vào người đang ở phía bên kia màn hình.</p></div>
            <div className="journey-grid">{JOURNEY.map(([number, title, description]) => <article className="journey-card" key={number}><span>{number}</span><div className="journey-card__line" /><h3>{title}</h3><p>{description}</p></article>)}</div>
          </div>
        </section>

        <section className="experience-quote section"><div className="container experience-quote__inner"><span className="quote-mark">“</span><blockquote>Khoảng cách nhỏ lại khi trải nghiệm đủ nhẹ nhàng để mọi người muốn nói chuyện thường xuyên hơn.</blockquote><span className="quote-caption">ChatWeb · meaningful conversations</span></div></section>

        <section className="marketing-cta section"><div className="container marketing-cta__card"><span className="section-kicker">SẴN SÀNG KẾT NỐI?</span><h2>Để câu chuyện<br /><em>bắt đầu hôm nay.</em></h2><Link className="button button--dark button--large" to="/register">Tạo tài khoản miễn phí <span>↗</span></Link></div></section>
      </main>
      <Footer />
    </div>
  )
}

export default ExperiencePage
