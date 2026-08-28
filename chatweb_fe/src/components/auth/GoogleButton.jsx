import { getGoogleAuthUrl } from '../../services/apiClient.js'

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M21.8 12.2c0-.7-.1-1.4-.2-2H12v3.9h5.5a4.8 4.8 0 0 1-2.1 3.1v2.6h3.4c2-1.9 3-4.5 3-7.6Z" fill="#4285F4" />
      <path d="M12 22c2.8 0 5.2-.9 6.9-2.5l-3.4-2.6c-.9.6-2.1 1-3.5 1a6 6 0 0 1-5.7-4.1H2.8v2.7A10.4 10.4 0 0 0 12 22Z" fill="#34A853" />
      <path d="M6.3 13.8A6 6 0 0 1 6 12c0-.6.1-1.2.3-1.8V7.5H2.8A10 10 0 0 0 1.8 12c0 1.6.4 3.1 1 4.5l3.5-2.7Z" fill="#FBBC05" />
      <path d="M12 6.1c1.6 0 2.9.5 4 1.6l3-3A10 10 0 0 0 2.8 7.5l3.5 2.7A6 6 0 0 1 12 6.1Z" fill="#EA4335" />
    </svg>
  )
}

function GoogleButton({ label = 'Tiếp tục với Google' }) {
  return (
    <button className="google-button" type="button" onClick={() => window.location.assign(getGoogleAuthUrl())}>
      <GoogleIcon />
      {label}
    </button>
  )
}

export default GoogleButton
