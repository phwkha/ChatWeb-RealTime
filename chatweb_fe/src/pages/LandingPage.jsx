import { useEffect } from 'react'
import Footer from '../components/layout/Footer.jsx'
import Header from '../components/layout/Header.jsx'
import '../styles/landing.css'

const FEATURES = [
  {
    number: '01',
    tone: 'violet',
    title: 'Tin nhắn tức thì',
    description: 'Kết nối theo thời gian thực, đồng bộ mượt mà trên mọi thiết bị của bạn.',
    icon: 'spark',
  },
  {
    number: '02',
    tone: 'coral',
    title: 'Không gian của riêng bạn',
    description: 'Từ nhóm bạn thân đến cộng đồng lớn — sắp xếp mọi cuộc trò chuyện thật dễ dàng.',
    icon: 'people',
  },
  {
    number: '03',
    tone: 'lime',
    title: 'Chia sẻ không giới hạn',
    description: 'Gửi ảnh, video và khoảnh khắc đáng nhớ với chất lượng nguyên vẹn.',
    icon: 'image',
  },
]

const MINI_MESSAGES = [
  { name: 'Linh', initials: 'LN', text: 'Tối nay mình call nhé?', time: '2 phút', tone: 'purple' },
  { name: 'Team Thiết kế', initials: 'TD', text: 'Minh đã gửi một hình ảnh', time: '8 phút', tone: 'orange' },
  { name: 'Gia đình', initials: 'GĐ', text: 'Hẹn cả nhà cuối tuần này ❤️', time: '1 giờ', tone: 'green' },
]

function FeatureIcon({ name }) {
  if (name === 'people') {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M16 20v-1.5a4.5 4.5 0 0 0-4.5-4.5h-4A4.5 4.5 0 0 0 3 18.5V20" />
        <circle cx="9.5" cy="7" r="3.5" />
        <path d="M16 4.2a3.5 3.5 0 0 1 0 6.6M18 14a4 4 0 0 1 3 3.9V20" />
      </svg>
    )
  }

  if (name === 'image') {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <rect x="3" y="3" width="18" height="18" rx="4" />
        <circle cx="9" cy="9" r="2" />
        <path d="m5 18 4.5-4.5 3.2 3.2 2.2-2.2L19 18.6" />
      </svg>
    )
  }

  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m13 2-8 12h7l-1 8 8-12h-7l1-8Z" />
    </svg>
  )
}

function ChatPreview() {
  return (
    <div className="chat-stage" aria-label="Mô phỏng giao diện ChatWeb">
      <div className="orb orb--one" />
      <div className="orb orb--two" />

      <div className="floating-note floating-note--top">
        <span className="floating-note__icon">✓</span>
        <div><strong>Đã gửi</strong><small>Vừa xong</small></div>
      </div>

      <div className="chat-window">
        <aside className="chat-sidebar">
          <div className="window-dots"><span /><span /><span /></div>
          <div className="chat-sidebar__title"><strong>Tin nhắn</strong><span>+</span></div>
          <div className="search-pill"><span>⌕</span><i>Tìm kiếm</i></div>
          <div className="chat-list">
            {MINI_MESSAGES.map((message, index) => (
              <div key={message.name} className={`chat-list__item${index === 0 ? ' is-active' : ''}`}>
                <div className={`avatar avatar--${message.tone}`}>{message.initials}</div>
                <div><strong>{message.name}</strong><small>{message.text}</small></div>
                <time>{message.time}</time>
              </div>
            ))}
          </div>
        </aside>

        <div className="conversation">
          <div className="conversation__header">
            <div className="avatar avatar--purple">LN<span className="online-dot" /></div>
            <div><strong>Linh Nguyễn</strong><small>Đang hoạt động</small></div>
            <div className="conversation__actions"><button aria-label="Gọi thoại">⌕</button><button aria-label="Tùy chọn">•••</button></div>
          </div>

          <div className="conversation__body">
            <div className="day-label">Hôm nay, 10:24</div>
            <div className="bubble-row">
              <div className="avatar avatar--purple avatar--small">LN</div>
              <div className="bubble bubble--received">Cuối tuần này cậu có rảnh không?</div>
            </div>
            <div className="bubble-row bubble-row--sent">
              <div className="bubble bubble--sent">Có chứ! Mình đang định rủ mọi người đi picnic 🌿</div>
            </div>
            <div className="bubble-row">
              <div className="avatar avatar--purple avatar--small">LN</div>
              <div className="bubble bubble--received bubble--image">
                <div className="image-placeholder"><span>☀</span><i /><b /></div>
                <span>Chỗ này thì sao?</span>
              </div>
            </div>
            <div className="typing"><span /><span /><span /></div>
          </div>

          <div className="message-composer">
            <button aria-label="Đính kèm">＋</button>
            <span>Nhập tin nhắn...</span>
            <button className="send-button" aria-label="Gửi tin nhắn">↗</button>
          </div>
        </div>
      </div>

      <div className="floating-note floating-note--bottom">
        <div className="avatar avatar--orange avatar--mini">M</div>
        <div><strong>Minh đã thả tim</strong><small>“Chỗ này thì sao?”</small></div>
        <span className="heart">♥</span>
      </div>
    </div>
  )
}

function LandingPage() {
  useEffect(() => {
    const elements = document.querySelectorAll('[data-reveal]')
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible')
            observer.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.14 },
    )

    elements.forEach((element) => observer.observe(element))
    return () => observer.disconnect()
  }, [])

  return (
    <div className="landing-page" id="top">
      <Header />

      <main>
        <section className="hero-section">
          <div className="hero-grid" aria-hidden="true" />
          <div className="container hero-section__inner">
            <div className="hero-copy">
              <div className="eyebrow hero-entry hero-entry--one">
                <span className="eyebrow__pulse" />
                Trò chuyện theo cách của bạn
              </div>
              <h1 className="hero-entry hero-entry--two">
                Mọi cuộc trò chuyện,
                <span> gần nhau hơn.</span>
              </h1>
              <p className="hero-subtitle hero-entry hero-entry--three">
                Nhắn tin, gọi điện và chia sẻ từng khoảnh khắc — nhanh chóng, riêng tư và trọn vẹn trên mọi thiết bị.
              </p>
              <div className="hero-actions hero-entry hero-entry--four">
                <a className="button button--primary button--large" href="/register">
                  Bắt đầu trò chuyện
                  <span aria-hidden="true">↗</span>
                </a>
                <a className="button button--ghost button--large" href="#experience">
                  <span className="play-icon" aria-hidden="true">▶</span>
                  Xem trải nghiệm
                </a>
              </div>
              <div className="hero-proof hero-entry hero-entry--five">
                <div className="avatar-stack" aria-hidden="true">
                  <span className="avatar avatar--purple">AN</span>
                  <span className="avatar avatar--orange">MK</span>
                  <span className="avatar avatar--green">TH</span>
                  <span className="avatar avatar--blue">+2k</span>
                </div>
                <div><strong>2.000+ cuộc trò chuyện</strong><span>được kết nối mỗi ngày</span></div>
              </div>
            </div>

            <div className="hero-visual hero-entry hero-entry--visual">
              <ChatPreview />
            </div>
          </div>
          <a className="scroll-cue" href="#features" aria-label="Cuộn đến phần tính năng">
            <span />
            Khám phá
          </a>
        </section>

        <section className="trust-strip" aria-label="Giá trị nổi bật">
          <div className="container trust-strip__inner">
            <span>Kết nối không giới hạn</span>
            <i />
            <span>Riêng tư là ưu tiên</span>
            <i />
            <span>Đồng bộ thời gian thực</span>
            <i />
            <span>Trải nghiệm liền mạch</span>
          </div>
        </section>

        <section className="features-section section" id="features">
          <div className="container">
            <div className="section-heading" data-reveal>
              <div>
                <span className="section-kicker">Tại sao là ChatWeb?</span>
                <h2>Đơn giản để bắt đầu.<br />Mạnh mẽ để kết nối.</h2>
              </div>
              <p>Mọi thứ bạn cần để giữ liên lạc với những người quan trọng, trong một không gian được thiết kế đầy cảm hứng.</p>
            </div>

            <div className="feature-grid">
              {FEATURES.map((feature, index) => (
                <article
                  key={feature.number}
                  className={`feature-card feature-card--${feature.tone}`}
                  data-reveal
                  style={{ '--delay': `${index * 120}ms` }}
                >
                  <span className="feature-card__number">{feature.number}</span>
                  <div className="feature-card__icon"><FeatureIcon name={feature.icon} /></div>
                  <h3>{feature.title}</h3>
                  <p>{feature.description}</p>
                  <a href="#experience" aria-label={`Tìm hiểu ${feature.title}`}>Khám phá <span>→</span></a>
                  <div className="feature-card__glow" />
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="experience-section section" id="experience">
          <div className="container experience-grid">
            <div className="experience-visual" data-reveal>
              <div className="phone-card phone-card--back">
                <div className="phone-card__top"><span>9:41</span><i /></div>
                <p>Cuộc trò chuyện</p>
                {MINI_MESSAGES.map((message) => (
                  <div className="phone-contact" key={message.name}>
                    <div className={`avatar avatar--${message.tone}`}>{message.initials}</div>
                    <div><strong>{message.name}</strong><small>{message.text}</small></div>
                  </div>
                ))}
              </div>
              <div className="phone-card phone-card--front">
                <div className="phone-card__top"><span>9:41</span><i /></div>
                <div className="phone-profile">
                  <div className="avatar avatar--purple">LN</div>
                  <strong>Linh Nguyễn</strong><small>Đang hoạt động</small>
                </div>
                <div className="phone-chat">
                  <span>Chào buổi sáng! ☀️</span>
                  <span className="is-mine">Chúc cậu một ngày thật vui nhé!</span>
                  <span>Nhất định rồi ✨</span>
                </div>
                <div className="phone-input">Aa <span>➤</span></div>
              </div>
              <div className="experience-badge experience-badge--secure"><span>✓</span> Mã hóa an toàn</div>
              <div className="experience-badge experience-badge--online"><i /> 12 bạn đang online</div>
            </div>

            <div className="experience-copy" data-reveal>
              <span className="section-kicker">Luôn bên bạn</span>
              <h2>Một nhịp kết nối,<br />trên mọi thiết bị.</h2>
              <p>Tiếp tục câu chuyện ở bất cứ đâu. Tin nhắn của bạn được đồng bộ tức thì và luôn sẵn sàng khi bạn quay lại.</p>
              <ul className="check-list">
                <li><span>✓</span><div><strong>Đồng bộ tức thì</strong><small>Không bỏ lỡ bất kỳ tin nhắn nào.</small></div></li>
                <li><span>✓</span><div><strong>Thông báo thông minh</strong><small>Tập trung vào điều thực sự quan trọng.</small></div></li>
                <li><span>✓</span><div><strong>Hiện diện theo thời gian thực</strong><small>Biết khi bạn bè sẵn sàng trò chuyện.</small></div></li>
              </ul>
              <a className="inline-link" href="#start">Khám phá ChatWeb <span>→</span></a>
            </div>
          </div>
        </section>

        <section className="security-section section" id="security">
          <div className="container security-card" data-reveal>
            <div className="security-orbit" aria-hidden="true">
              <span className="security-orbit__ring" />
              <span className="security-orbit__core">
                <svg viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="11" rx="3" /><path d="M8.5 10V7.5a3.5 3.5 0 0 1 7 0V10" /><circle cx="12" cy="15" r="1" /></svg>
              </span>
              <i className="orbit-dot orbit-dot--one" />
              <i className="orbit-dot orbit-dot--two" />
            </div>
            <div className="security-copy">
              <span className="section-kicker section-kicker--light">Riêng tư từ thiết kế</span>
              <h2>Câu chuyện của bạn.<br />Chỉ thuộc về bạn.</h2>
              <p>ChatWeb đặt quyền riêng tư ở trung tâm trải nghiệm với các lớp bảo vệ hiện đại và quyền kiểm soát rõ ràng.</p>
              <div className="security-stats">
                <div><strong>24/7</strong><span>Bảo vệ liên tục</span></div>
                <div><strong>100%</strong><span>Bạn kiểm soát</span></div>
                <div><strong>0</strong><span>Dữ liệu quảng cáo</span></div>
              </div>
            </div>
          </div>
        </section>

        <section className="cta-section section" id="start">
          <div className="container cta-card" data-reveal>
            <div className="cta-decoration cta-decoration--left" aria-hidden="true"><span>Hey!</span><i>♥</i></div>
            <div className="cta-content">
              <span className="section-kicker">Sẵn sàng kết nối?</span>
              <h2>Một lời chào có thể<br />bắt đầu mọi câu chuyện.</h2>
              <p>Tham gia ChatWeb và gần hơn với những người bạn quan tâm.</p>
              <a className="button button--dark button--large" href="/register">Tạo tài khoản miễn phí <span>↗</span></a>
              <small>Không cần thẻ tín dụng · Chỉ mất 30 giây</small>
            </div>
            <div className="cta-decoration cta-decoration--right" aria-hidden="true"><span>Hi!</span><i>✦</i></div>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  )
}

export default LandingPage
