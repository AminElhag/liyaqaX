import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { PlanCard } from './PlanCard'
import { useAuthStore } from '@/stores/useAuthStore'
import { Permission } from '@/types/permissions'
import type { MembershipPlanSummary } from '@/types/domain'
import i18n from '@/i18n'

const mockUser = {
  id: 'user-1',
  email: 'owner@test.com',
  scope: 'club',
  roleId: 'role-1',
  roleName: 'Owner',
  organizationId: 'org-1',
  clubId: 'club-1',
  branchIds: [],
}

const activePlan: MembershipPlanSummary = {
  id: 'plan-1',
  nameAr: 'شهري أساسي',
  nameEn: 'Basic Monthly',
  priceHalalas: 15000,
  priceSar: '150.00',
  durationDays: 30,
  isActive: true,
}

const inactivePlan: MembershipPlanSummary = {
  ...activePlan,
  id: 'plan-2',
  nameEn: 'Old Plan',
  isActive: false,
}

describe('PlanCard', () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
    i18n.changeLanguage('en')
  })

  it('renders plan name and price', () => {
    useAuthStore.getState().setAuth('token', mockUser, [Permission.MEMBERSHIP_PLAN_READ])

    renderWithProviders(
      <PlanCard plan={activePlan} onEdit={vi.fn()} />,
    )

    expect(screen.getByText('Basic Monthly')).toBeInTheDocument()
    expect(screen.getByText(/150/)).toBeInTheDocument()
    expect(screen.getByText(/30/)).toBeInTheDocument()
  })

  it('shows Active badge for active plan', () => {
    useAuthStore.getState().setAuth('token', mockUser, [Permission.MEMBERSHIP_PLAN_READ])

    renderWithProviders(
      <PlanCard plan={activePlan} onEdit={vi.fn()} />,
    )

    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('shows Inactive badge for inactive plan', () => {
    useAuthStore.getState().setAuth('token', mockUser, [Permission.MEMBERSHIP_PLAN_READ])

    renderWithProviders(
      <PlanCard plan={inactivePlan} onEdit={vi.fn()} />,
    )

    expect(screen.getByText('Inactive')).toBeInTheDocument()
  })

  it('shows edit button when user has update permission', () => {
    useAuthStore.getState().setAuth('token', mockUser, [Permission.MEMBERSHIP_PLAN_UPDATE])

    renderWithProviders(
      <PlanCard plan={activePlan} onEdit={vi.fn()} />,
    )

    expect(screen.getByRole('button', { name: 'Edit plan' })).toBeInTheDocument()
  })

  it('hides edit button when user lacks update permission', () => {
    useAuthStore.getState().setAuth('token', mockUser, [Permission.MEMBERSHIP_PLAN_READ])

    renderWithProviders(
      <PlanCard plan={activePlan} onEdit={vi.fn()} />,
    )

    expect(screen.queryByRole('button', { name: 'Edit plan' })).not.toBeInTheDocument()
  })

  it('calls onEdit with plan when edit button is clicked', async () => {
    useAuthStore.getState().setAuth('token', mockUser, [Permission.MEMBERSHIP_PLAN_UPDATE])
    const onEdit = vi.fn()

    renderWithProviders(
      <PlanCard plan={activePlan} onEdit={onEdit} />,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Edit plan' }))
    expect(onEdit).toHaveBeenCalledWith(activePlan)
  })

  it('renders Arabic name when language is ar', () => {
    i18n.changeLanguage('ar')
    useAuthStore.getState().setAuth('token', mockUser, [Permission.MEMBERSHIP_PLAN_READ])

    renderWithProviders(
      <PlanCard plan={activePlan} onEdit={vi.fn()} />,
    )

    expect(screen.getByText('شهري أساسي')).toBeInTheDocument()
  })
})
