import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { MemberTable } from './MemberTable'
import type { MemberSummary } from '@/types/domain'
import type { PaginationMeta } from '@/types/api'
import i18n from '@/i18n'

const mockMembers: MemberSummary[] = [
  {
    id: 'member-1',
    firstNameAr: 'أحمد',
    firstNameEn: 'Ahmed',
    lastNameAr: 'الرشيدي',
    lastNameEn: 'Al-Rashidi',
    email: 'ahmed@example.com',
    phone: '+966501234567',
    membershipStatus: 'pending',
    branch: { id: 'branch-1', nameAr: 'فرع الرياض', nameEn: 'Riyadh Branch' },
    joinedAt: '2026-04-01',
  },
  {
    id: 'member-2',
    firstNameAr: 'سارة',
    firstNameEn: 'Sarah',
    lastNameAr: 'المنصوري',
    lastNameEn: 'Al-Mansouri',
    email: 'sarah@example.com',
    phone: '+966509876543',
    membershipStatus: 'active',
    branch: { id: 'branch-1', nameAr: 'فرع الرياض', nameEn: 'Riyadh Branch' },
    joinedAt: '2026-03-15',
  },
  {
    id: 'member-3',
    firstNameAr: 'خالد',
    firstNameEn: 'Khalid',
    lastNameAr: 'العتيبي',
    lastNameEn: 'Al-Otaibi',
    email: 'khalid@example.com',
    phone: '+966505555555',
    membershipStatus: 'frozen',
    branch: { id: 'branch-2', nameAr: 'فرع جدة', nameEn: 'Jeddah Branch' },
    joinedAt: '2026-02-01',
  },
]

const singlePagePagination: PaginationMeta = {
  page: 0,
  size: 20,
  totalElements: 3,
  totalPages: 1,
  hasNext: false,
}

const multiPagePagination: PaginationMeta = {
  page: 0,
  size: 20,
  totalElements: 45,
  totalPages: 3,
  hasNext: true,
}

describe('MemberTable', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders member rows with name, email, phone, branch', () => {
    renderWithProviders(
      <MemberTable
        members={mockMembers}
        pagination={singlePagePagination}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('Ahmed Al-Rashidi')).toBeInTheDocument()
    expect(screen.getByText('ahmed@example.com')).toBeInTheDocument()
    expect(screen.getByText('+966501234567')).toBeInTheDocument()

    expect(screen.getByText('Sarah Al-Mansouri')).toBeInTheDocument()
    expect(screen.getByText('Khalid Al-Otaibi')).toBeInTheDocument()
    expect(screen.getAllByText('Riyadh Branch')).toHaveLength(2)
    expect(screen.getByText('Jeddah Branch')).toBeInTheDocument()
  })

  it('renders correct status badges with correct text', () => {
    renderWithProviders(
      <MemberTable
        members={mockMembers}
        pagination={singlePagePagination}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('Pending')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByText('Frozen')).toBeInTheDocument()
  })

  it('shows empty state when no members', () => {
    renderWithProviders(
      <MemberTable
        members={[]}
        pagination={{ ...singlePagePagination, totalElements: 0 }}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('No members found')).toBeInTheDocument()
  })

  it('shows pagination controls when multiple pages exist', () => {
    renderWithProviders(
      <MemberTable
        members={mockMembers}
        pagination={multiPagePagination}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('Previous')).toBeInTheDocument()
    expect(screen.getByText('Next')).toBeInTheDocument()
    expect(screen.getByText(/Page 1/)).toBeInTheDocument()
  })

  it('hides pagination controls when only one page', () => {
    renderWithProviders(
      <MemberTable
        members={mockMembers}
        pagination={singlePagePagination}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.queryByText('Previous')).not.toBeInTheDocument()
    expect(screen.queryByText('Next')).not.toBeInTheDocument()
  })

  it('renders Arabic names when language is ar', () => {
    i18n.changeLanguage('ar')

    renderWithProviders(
      <MemberTable
        members={mockMembers}
        pagination={singlePagePagination}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('أحمد الرشيدي')).toBeInTheDocument()
    expect(screen.getByText('سارة المنصوري')).toBeInTheDocument()
    expect(screen.getByText('خالد العتيبي')).toBeInTheDocument()
  })
})
