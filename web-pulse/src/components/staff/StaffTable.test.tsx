import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { StaffTable } from './StaffTable'
import type { StaffMemberSummary } from '@/types/domain'
import type { PaginationMeta } from '@/types/api'
import i18n from '@/i18n'

const mockStaff: StaffMemberSummary[] = [
  {
    id: 'staff-1',
    firstNameAr: 'أحمد',
    firstNameEn: 'Ahmed',
    lastNameAr: 'محمد',
    lastNameEn: 'Mohammed',
    email: 'ahmed@example.com',
    role: { id: 'role-1', nameAr: 'مالك النادي', nameEn: 'Club Owner' },
    isActive: true,
  },
  {
    id: 'staff-2',
    firstNameAr: 'سارة',
    firstNameEn: 'Sarah',
    lastNameAr: 'علي',
    lastNameEn: 'Ali',
    email: 'sarah@example.com',
    role: { id: 'role-2', nameAr: 'موظفة ا��تقبال', nameEn: 'Receptionist' },
    isActive: false,
  },
]

const singlePagePagination: PaginationMeta = {
  page: 0,
  size: 20,
  totalElements: 2,
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

describe('StaffTable', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders staff rows with name, email, role', () => {
    renderWithProviders(
      <StaffTable
        staff={mockStaff}
        pagination={singlePagePagination}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('Ahmed Mohammed')).toBeInTheDocument()
    expect(screen.getByText('ahmed@example.com')).toBeInTheDocument()
    expect(screen.getByText('Club Owner')).toBeInTheDocument()

    expect(screen.getByText('Sarah Ali')).toBeInTheDocument()
    expect(screen.getByText('sarah@example.com')).toBeInTheDocument()
    expect(screen.getByText('Receptionist')).toBeInTheDocument()
  })

  it('shows active and inactive status badges', () => {
    renderWithProviders(
      <StaffTable
        staff={mockStaff}
        pagination={singlePagePagination}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByText('Inactive')).toBeInTheDocument()
  })

  it('shows empty state when no staff', () => {
    renderWithProviders(
      <StaffTable
        staff={[]}
        pagination={{ ...singlePagePagination, totalElements: 0 }}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('No staff members found')).toBeInTheDocument()
  })

  it('shows pagination controls when multiple pages exist', () => {
    renderWithProviders(
      <StaffTable
        staff={mockStaff}
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
      <StaffTable
        staff={mockStaff}
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
      <StaffTable
        staff={mockStaff}
        pagination={singlePagePagination}
        onPageChange={vi.fn()}
      />,
    )

    expect(screen.getByText('أحمد محمد')).toBeInTheDocument()
    expect(screen.getByText('سارة علي')).toBeInTheDocument()
  })
})
