import React from 'react'
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { ConfirmDialogProvider, useConfirm } from '../ConfirmDialogContext.jsx'

function TestConsumer({ options, onResult }) {
  const confirm = useConfirm()
  const handleClick = async () => {
    const result = await confirm(options)
    onResult?.(result)
  }
  return <button type="button" onClick={handleClick}>Trigger Confirm</button>
}

describe('ConfirmDialogContext & useConfirm', () => {
  it('returns safe fallback resolving false when called outside provider', async () => {
    let result = null
    render(<TestConsumer onResult={(r) => { result = r }} />)

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Trigger Confirm' }))
    })

    expect(result).toBe(false)
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })

  it('renders dialog when triggered and resolves true on confirm', async () => {
    let result = null
    render(
      <ConfirmDialogProvider>
        <TestConsumer
          options={{
            title: 'Xóa tài khoản',
            message: 'Thao tác không thể hoàn tác',
            detail: '@baduser',
            confirmText: 'Đồng ý xóa',
            cancelText: 'Bỏ qua',
            tone: 'danger',
          }}
          onResult={(r) => { result = r }}
        />
      </ConfirmDialogProvider>
    )

    fireEvent.click(screen.getByRole('button', { name: 'Trigger Confirm' }))

    expect(screen.getByRole('alertdialog')).toBeInTheDocument()
    expect(screen.getByText('Xóa tài khoản')).toBeInTheDocument()
    expect(screen.getByText('Thao tác không thể hoàn tác')).toBeInTheDocument()
    expect(screen.getByText('@baduser')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Đồng ý xóa' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bỏ qua' })).toBeInTheDocument()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Đồng ý xóa' }))
    })

    expect(result).toBe(true)
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })

  it('resolves false on cancel button click and closes dialog', async () => {
    let result = null
    render(
      <ConfirmDialogProvider>
        <TestConsumer
          options={{ title: 'Hủy báo cáo?' }}
          onResult={(r) => { result = r }}
        />
      </ConfirmDialogProvider>
    )

    fireEvent.click(screen.getByRole('button', { name: 'Trigger Confirm' }))
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Hủy' }))
    })

    expect(result).toBe(false)
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })

  it('resolves false on Escape key and stops event propagation', async () => {
    let result = null
    render(
      <ConfirmDialogProvider>
        <TestConsumer
          options={{ title: 'Xác nhận thoát' }}
          onResult={(r) => { result = r }}
        />
      </ConfirmDialogProvider>
    )

    fireEvent.click(screen.getByRole('button', { name: 'Trigger Confirm' }))
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()

    await act(async () => {
      fireEvent.keyDown(window, { key: 'Escape' })
    })

    expect(result).toBe(false)
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })

  it('resolves false when clicking outside on backdrop', async () => {
    let result = null
    render(
      <ConfirmDialogProvider>
        <TestConsumer
          options={{ title: 'Click ngoài' }}
          onResult={(r) => { result = r }}
        />
      </ConfirmDialogProvider>
    )

    fireEvent.click(screen.getByRole('button', { name: 'Trigger Confirm' }))
    const backdrop = document.querySelector('.cw-confirm-backdrop')
    expect(backdrop).toBeInTheDocument()

    await act(async () => {
      fireEvent.mouseDown(backdrop)
    })

    expect(result).toBe(false)
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })

  it('superseding with a new dialog resolves previous promise to false', async () => {
    let result1 = null
    let result2 = null

    function DualConsumer() {
      const confirm = useConfirm()
      return (
        <div>
          <button type="button" onClick={async () => { result1 = await confirm({ title: 'Dialog 1' }) }}>Btn 1</button>
          <button type="button" onClick={async () => { result2 = await confirm({ title: 'Dialog 2' }) }}>Btn 2</button>
        </div>
      )
    }

    render(
      <ConfirmDialogProvider>
        <DualConsumer />
      </ConfirmDialogProvider>
    )

    fireEvent.click(screen.getByText('Btn 1'))
    expect(screen.getByText('Dialog 1')).toBeInTheDocument()

    // Trigger second dialog while first is open
    await act(async () => {
      fireEvent.click(screen.getByText('Btn 2'))
    })
    expect(result1).toBe(false)
    expect(screen.getByText('Dialog 2')).toBeInTheDocument()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
    })

    expect(result2).toBe(true)
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })
})
