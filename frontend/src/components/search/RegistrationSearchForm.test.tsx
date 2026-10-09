import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { RegistrationSearchForm } from './RegistrationSearchForm'

describe('RegistrationSearchForm', () => {
  it('labels the field and marks it required for assistive technology', () => {
    render(<RegistrationSearchForm onSubmit={vi.fn()} />)

    const input = screen.getByLabelText(/registration number/i)
    expect(input).toBeInTheDocument()
    expect(input).toHaveAttribute('type', 'search')
  })

  it('refuses to submit an empty search and says why', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<RegistrationSearchForm onSubmit={onSubmit} />)

    await user.click(screen.getByRole('button', { name: /find student/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/at least 3 characters/i)
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('refuses a one character search', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<RegistrationSearchForm onSubmit={onSubmit} />)

    await user.type(screen.getByLabelText(/registration number/i), 'U')
    await user.click(screen.getByRole('button', { name: /find student/i }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits the trimmed registration number exactly as typed', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<RegistrationSearchForm onSubmit={onSubmit} />)

    const input = screen.getByLabelText(/registration number/i)
    await user.type(input, '  UG/02/BTCSEAIML/2023/024  ')
    await user.click(screen.getByRole('button', { name: /find student/i }))

    expect(onSubmit).toHaveBeenCalledWith('UG/02/BTCSEAIML/2023/024')
  })

  it('can be seeded with a value, for example from the URL', () => {
    render(<RegistrationSearchForm onSubmit={vi.fn()} defaultValue="UG/02/BTCSEAIML/2023/024" />)

    expect(screen.getByLabelText(/registration number/i)).toHaveValue('UG/02/BTCSEAIML/2023/024')
  })

  it('shows a busy state while a lookup is running', () => {
    render(<RegistrationSearchForm onSubmit={vi.fn()} loading />)

    const button = screen.getByRole('button', { name: /find student/i })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
  })
})