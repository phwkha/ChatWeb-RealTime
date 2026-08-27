import Brand from '../Brand.jsx'
import '../../styles/auth.css'

function AuthShell({ children, eyebrow, title, description, variant = 'login' }) {
  return (
    <main className={`auth-page auth-page--${variant}`}>
      <div className="auth-page__glow auth-page__glow--one" />
      <div className="auth-page__glow auth-page__glow--two" />

      <header className="auth-topbar">
        <Brand />
        <a href="/" className="auth-topbar__back"><span>←</span> Về trang chủ</a>
      </header>

      <div className="auth-layout">
        <section className="auth-story" aria-label="Giới thiệu ChatWeb">
          <div className="auth-story__content">
            <span className="auth-story__eyebrow"><i /> {eyebrow}</span>
            <h1>{title}</h1>
            <p>{description}</p>

            <div className="auth-story__scene" aria-hidden="true">
              <div className="story-message story-message--one">
                <span className="story-avatar story-avatar--purple">AN</span>
                <div><strong>Anh đã gửi một tin nhắn</strong><small>Cậu đến chưa?</small></div>
              </div>
              <div className="story-message story-message--two">
                <span className="story-avatar story-avatar--coral">MI</span>
                <div><strong>Minh</strong><small>Mình đang ở đây 👋</small></div>
              </div>
              <div className="story-phone">
                <div className="story-phone__top"><i /><span>ChatWeb</span><b /></div>
                <div className="story-phone__profile"><span className="story-avatar story-avatar--purple">LN</span><div><strong>Linh Nguyễn</strong><small>Đang hoạt động</small></div></div>
                <div className="story-phone__chat"><span>Chào cậu! ✨</span><span>Tối nay mình gọi nhé?</span><span className="is-mine">Nhất định rồi!</span></div>
                <div className="story-phone__input">Nhập tin nhắn... <span>↗</span></div>
              </div>
              <div className="story-secure"><span>✓</span><div><strong>Kết nối an toàn</strong><small>Riêng tư luôn là ưu tiên</small></div></div>
            </div>
          </div>
          <div className="auth-story__foot"><span>●</span> Tin nhắn thời gian thực <i /> Bảo mật & riêng tư</div>
        </section>

        <section className="auth-panel">
          <div className="auth-panel__inner">{children}</div>
        </section>
      </div>
    </main>
  )
}

export default AuthShell
