import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { MembershipCard } from './MembershipCard'
import i18n from '@/i18n'
import type { Membership } from '@/types/domain'

const activeMembership: Membership = {
  id: 'ms-1',
  memberId: 'member-1',
  plan: {
    id: 'plan-1',
    nameAr: 'شهري أساسي',
    nameEn: 'Basic Monthly',
    priceHalalas: 15000,
    priceSar: '150.00',
    durationDays: 30,
  },
  status: 'active',
  startDate: new Date().toISOString().split('T')[0],
  endDate: new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0],
  graceEndDate: new Date(Date.now() + 33 * 86400000).toISOString().split('T')[0],
  freezeDaysUsed: 0,
  payment: {
    id: 'pay-1',
    amountHalalas: 15000,
    amountSar: '150.00',
    paymentMethod: 'cash',
    paidAt: new Date().toISOString(),
  },
  invoice: {
    id: 'inv-1',
    invoiceNumber: 'INV-2026-ELI-00001',
    totalHalalas: 17250,
    totalSar: '172.50',
    issuedAt: new Date().toISOString(),
  },
  createdAt: new Date().toISOString(),
}

describe('MembershipCard', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders plan name and status', () => {
    renderWithProviders(<MembershipCard membership={activeMembership} />)
    expect(screen.getByText('Basic Monthly')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('displays payment info', () => {
    renderWithProviders(<MembershipCard membership={activeMembership} />)
    expect(screen.getByText('Cash')).toBeInTheDocument()
  })

  it('displays invoice number', () => {
    renderWithProviders(<MembershipCard membership={activeMembership} />)
    expect(screen.getByText('INV-2026-ELI-00001')).toBeInTheDocument()
  })

  it('renders Arabic plan name when language is ar', () => {
    i18n.changeLanguage('ar')
    renderWithProviders(<MembershipCard membership={activeMembership} />)
    expect(screen.getByText('شهري أساسي')).toBeInTheDocument()
  })

  it('shows days remaining for active membership', () => {
    renderWithProviders(<MembershipCard membership={activeMembership} />)
    expect(screen.getByText(/days remaining/)).toBeInTheDocument()
  })

  it('shows disabled renew button', () => {
    renderWithProviders(<MembershipCard membership={activeMembership} />)
    const btn = screen.getByRole('button')
    expect(btn).toBeDisabled()
  })
})
