import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import i18n from '@/i18n'

import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'

const loginSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Invalid email'),
  password: z.string().min(1, 'Password is required'),
})

type LoginForm = z.infer<typeof loginSchema>

function TestLoginForm({ onSubmit }: { onSubmit: (data: LoginForm) => void }) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  })

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <label htmlFor="email">Email</label>
      <input id="email" {...register('email')} />
      {errors.email && <p>{errors.email.message}</p>}

      <label htmlFor="password">Password</label>
      <input id="password" type="password" {...register('password')} />
      {errors.password && <p>{errors.password.message}</p>}

      <button type="submit">Sign in</button>
    </form>
  )
}

describe('LoginForm', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders email and password fields with submit button', () => {
    renderWithProviders(<TestLoginForm onSubmit={() => {}} />)

    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('shows validation errors when submitting empty form', async () => {
    const user = userEvent.setup()

    renderWithProviders(<TestLoginForm onSubmit={() => {}} />)

    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Email is required')).toBeInTheDocument()
    expect(await screen.findByText('Password is required')).toBeInTheDocument()
  })

  it('shows email format error for invalid email', async () => {
    const user = userEvent.setup()

    renderWithProviders(<TestLoginForm onSubmit={() => {}} />)

    await user.type(screen.getByLabelText('Email'), 'not-an-email')
    await user.type(screen.getByLabelText('Password'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Invalid email')).toBeInTheDocument()
  })

  it('calls onSubmit with form data when valid', async () => {
    const user = userEvent.setup()
    const handleSubmit = vi.fn()

    renderWithProviders(<TestLoginForm onSubmit={handleSubmit} />)

    await user.type(screen.getByLabelText('Email'), 'owner@elixir.com')
    await user.type(screen.getByLabelText('Password'), 'Owner1234!')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    await vi.waitFor(() => {
      expect(handleSubmit).toHaveBeenCalledWith(
        { email: 'owner@elixir.com', password: 'Owner1234!' },
        expect.anything(),
      )
    })
  })
})
