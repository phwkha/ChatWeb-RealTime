import { render, screen, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { LanguageProvider } from '../LanguageProvider.jsx'
import { useLanguage } from '../language-context.js'
import { AuthContext } from '../auth-context.js'
import { accountApi } from '../../services/accountApi.js'

vi.mock('../../services/accountApi.js', () => ({
  accountApi: {
    updateLanguage: vi.fn(() => Promise.resolve()),
  },
}))

function TestLanguageConsumer() {
  const { language, setLanguage, t } = useLanguage()
  return (
    <div>
      <span data-testid="current-lang">{language}</span>
      <span data-testid="translated-text">{t('language')}</span>
      <button onClick={() => setLanguage('en')} data-testid="btn-en">Set EN</button>
      <button onClick={() => setLanguage('ja')} data-testid="btn-ja">Set JA</button>
      <button onClick={() => setLanguage('invalid')} data-testid="btn-invalid">Set Invalid</button>
    </div>
  )
}

describe('LanguageProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('defaults to vi when no localStorage and no user', () => {
    render(
      <LanguageProvider>
        <TestLanguageConsumer />
      </LanguageProvider>
    )

    expect(screen.getByTestId('current-lang').textContent).toBe('vi')
  })

  it('initializes from localStorage if valid', () => {
    localStorage.setItem('chatweb-language', 'ja')

    render(
      <LanguageProvider>
        <TestLanguageConsumer />
      </LanguageProvider>
    )

    expect(screen.getByTestId('current-lang').textContent).toBe('ja')
  })

  it('prioritizes user.language on initial render if authenticated', () => {
    localStorage.setItem('chatweb-language', 'vi')
    const mockAuth = { user: { username: 'alice', language: 'en' } }

    render(
      <AuthContext.Provider value={mockAuth}>
        <LanguageProvider>
          <TestLanguageConsumer />
        </LanguageProvider>
      </AuthContext.Provider>
    )

    expect(screen.getByTestId('current-lang').textContent).toBe('en')
  })

  it('updates language, localStorage and calls accountApi when user switches language', async () => {
    const mockAuth = { user: { username: 'alice', language: 'vi' } }

    render(
      <AuthContext.Provider value={mockAuth}>
        <LanguageProvider>
          <TestLanguageConsumer />
        </LanguageProvider>
      </AuthContext.Provider>
    )

    expect(screen.getByTestId('current-lang').textContent).toBe('vi')

    await act(async () => {
      screen.getByTestId('btn-en').click()
    })

    expect(screen.getByTestId('current-lang').textContent).toBe('en')
    expect(localStorage.getItem('chatweb-language')).toBe('en')
    expect(accountApi.updateLanguage).toHaveBeenCalledWith('en')
  })

  it('does not call accountApi when unauthenticated user switches language', async () => {
    render(
      <LanguageProvider>
        <TestLanguageConsumer />
      </LanguageProvider>
    )

    await act(async () => {
      screen.getByTestId('btn-ja').click()
    })

    expect(screen.getByTestId('current-lang').textContent).toBe('ja')
    expect(localStorage.getItem('chatweb-language')).toBe('ja')
    expect(accountApi.updateLanguage).not.toHaveBeenCalled()
  })

  it('normalizes invalid language to vi', async () => {
    render(
      <LanguageProvider>
        <TestLanguageConsumer />
      </LanguageProvider>
    )

    await act(async () => {
      screen.getByTestId('btn-invalid').click()
    })

    expect(screen.getByTestId('current-lang').textContent).toBe('vi')
    expect(localStorage.getItem('chatweb-language')).toBe('vi')
  })
})
