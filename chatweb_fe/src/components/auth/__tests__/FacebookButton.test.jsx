import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import FacebookButton from '../FacebookButton.jsx'

describe('FacebookButton', () => {
  it('renders default label and navigates on click', () => {
    const originalLocation = window.location
    delete window.location
    window.location = { assign: vi.fn() }

    render(<FacebookButton />)

    const button = screen.getByRole('button', { name: /tiếp tục với facebook/i })
    expect(button).toBeInTheDocument()

    fireEvent.click(button)
    expect(window.location.assign).toHaveBeenCalledWith(expect.stringContaining('/oauth2/authorization/facebook'))

    window.location = originalLocation
  })

  it('renders custom label when provided', () => {
    render(<FacebookButton label="Đăng nhập với Facebook" />)
    expect(screen.getByRole('button', { name: /đăng nhập với facebook/i })).toBeInTheDocument()
  })
})
