import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { MemberTable } from './MemberTable'
import type { MemberSummary } from '@/types/domain'
import type { PaginationMeta } from '@/types/api'
import i18n from '@/i18n'

const lapsedMember: MemberSummary = {
  id: 'member-lapsed',
  firstNameAr: 'سارة',
  firstNameEn: 'Sarah',
  lastNameAr: 'الزهراني',
  lastNameEn: 'Al-Zahrani',
  email: 'sarah@example.com',
  phone: '+966501234567',
  membershipStatus: 'lapsed',
  branch: { id: 'branch-1', nameAr: 'فرع الرياض', nameEn: 'Riyadh Branch' },
  joinedAt: '2026-03-01',
}

const pagination: PaginationMeta = {
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
}

describe('Lapsed badge on member table', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders Lapsed badge for lapsed members', () => {
    renderWithProviders(
      <MemberTable
        members={[lapsedMember]}
        pagination={pagination}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('Lapsed')).toBeInTheDocument()
  })

  it('renders Arabic lapsed badge when language is ar', () => {
    i18n.changeLanguage('ar')

    renderWithProviders(
      <MemberTable
        members={[lapsedMember]}
        pagination={pagination}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('منتهية الصلاحية')).toBeInTheDocument()
  })
})
