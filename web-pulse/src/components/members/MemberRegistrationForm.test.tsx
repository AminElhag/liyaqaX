import { describe, it, expect, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { MemberRegistrationForm } from './MemberRegistrationForm'
import i18n from '@/i18n'

const branches = [
  { id: '00000000-0000-0000-0000-000000000001', nameAr: 'فرع الرياض', nameEn: 'Riyadh Branch' },
  { id: '00000000-0000-0000-0000-000000000002', nameAr: 'فرع جدة', nameEn: 'Jeddah Branch' },
]

function renderForm(onSubmit = vi.fn(), isSubmitting = false) {
  return {
    onSubmit,
    ...renderWithProviders(
      <MemberRegistrationForm
        branches={branches}
        onSubmit={onSubmit}
        isSubmitting={isSubmitting}
      />,
    ),
  }
}

function getInput(name: string): HTMLInputElement {
  return document.querySelector(`input[name="${name}"]`) as HTMLInputElement
}

function getSelect(name: string): HTMLSelectElement {
  return document.querySelector(`select[name="${name}"]`) as HTMLSelectElement
}

describe('MemberRegistrationForm', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders step 1 fields initially', () => {
    renderForm()

    expect(screen.getByText('Personal information')).toBeInTheDocument()
    expect(getInput('email')).toBeInTheDocument()
    expect(getInput('phone')).toBeInTheDocument()
    expect(getInput('firstNameAr')).toBeInTheDocument()
    expect(getInput('firstNameEn')).toBeInTheDocument()
    expect(getSelect('branchId')).toBeInTheDocument()
    expect(screen.getByText('Next')).toBeInTheDocument()
  })

  it('does not advance to step 2 without required fields', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.click(screen.getByText('Next'))

    await waitFor(() => {
      expect(screen.getByText('Personal information')).toBeInTheDocument()
    })
  })

  it('advances to step 2 when step 1 is valid', async () => {
    const user = userEvent.setup()
    renderForm()

    await fillStep1(user)
    await user.click(screen.getByText('Next'))

    await waitFor(() => {
      expect(screen.getByText('Emergency contact')).toBeInTheDocument()
    })
  })

  it('can go back to step 1 from step 2', async () => {
    const user = userEvent.setup()
    renderForm()

    await fillStep1(user)
    await user.click(screen.getByText('Next'))

    await waitFor(() => {
      expect(screen.getByText('Emergency contact')).toBeInTheDocument()
    })

    await user.click(screen.getByText('Previous'))

    await waitFor(() => {
      expect(screen.getByText('Personal information')).toBeInTheDocument()
    })
  })

  it('calls onSubmit with CreateMemberRequest and generated password', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    renderForm(onSubmit)

    await fillStep1(user)
    await user.click(screen.getByText('Next'))

    await waitFor(() => {
      expect(getInput('emergencyNameAr')).toBeInTheDocument()
    })

    await fillStep2(user)

    const submitBtn = screen.getByText('Register member')
    await user.click(submitBtn)

    await waitFor(
      () => {
        expect(onSubmit).toHaveBeenCalledTimes(1)
      },
      { timeout: 3000 },
    )

    const [request, password] = onSubmit.mock.calls[0]
    expect(request.email).toBe('test@example.com')
    expect(request.firstNameAr).toBe('أحمد')
    expect(request.firstNameEn).toBe('Ahmed')
    expect(request.phone).toBe('+966501234567')
    expect(request.branchId).toBe('00000000-0000-0000-0000-000000000001')
    expect(request.emergencyContact.nameAr).toBe('محمد')
    expect(request.emergencyContact.nameEn).toBe('Mohammed')
    expect(request.emergencyContact.phone).toBe('+966507654321')
    expect(typeof password).toBe('string')
    expect(password.length).toBeGreaterThanOrEqual(8)
  })

  it('disables submit button when isSubmitting is true', async () => {
    const user = userEvent.setup()
    renderForm(vi.fn(), true)

    await fillStep1(user)
    await user.click(screen.getByText('Next'))

    await waitFor(() => {
      expect(screen.getByText('Emergency contact')).toBeInTheDocument()
    })

    expect(screen.getByText('Register member')).toBeDisabled()
  })
})

async function fillStep1(user: ReturnType<typeof userEvent.setup>) {
  await user.type(getInput('firstNameAr'), 'أحمد')
  await user.type(getInput('firstNameEn'), 'Ahmed')
  await user.type(getInput('lastNameAr'), 'الرشيدي')
  await user.type(getInput('lastNameEn'), 'Al-Rashidi')
  await user.type(getInput('email'), 'test@example.com')
  await user.type(getInput('phone'), '+966501234567')
  await user.selectOptions(
    getSelect('branchId'),
    '00000000-0000-0000-0000-000000000001',
  )
}

async function fillStep2(user: ReturnType<typeof userEvent.setup>) {
  await user.type(getInput('emergencyNameAr'), 'محمد')
  await user.type(getInput('emergencyNameEn'), 'Mohammed')
  await user.type(getInput('emergencyPhone'), '+966507654321')
}
