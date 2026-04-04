import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { GXBookingList } from './GXBookingList'
import type { GXBooking } from '@/types/domain'
import i18n from '@/i18n'

const mockBookings: GXBooking[] = [
  {
    id: 'booking-1',
    instanceId: 'instance-1',
    member: {
      id: 'member-1',
      firstNameAr: 'أحمد',
      firstNameEn: 'Ahmed',
      lastNameAr: 'الرشيدي',
      lastNameEn: 'Al-Rashidi',
    },
    status: 'confirmed',
    waitlistPosition: null,
    bookedAt: '2026-04-07T07:00:00Z',
    cancelledAt: null,
  },
  {
    id: 'booking-2',
    instanceId: 'instance-1',
    member: {
      id: 'member-2',
      firstNameAr: 'سارة',
      firstNameEn: 'Sarah',
      lastNameAr: 'المنصوري',
      lastNameEn: 'Al-Mansouri',
    },
    status: 'waitlist',
    waitlistPosition: 1,
    bookedAt: '2026-04-07T07:05:00Z',
    cancelledAt: null,
  },
  {
    id: 'booking-3',
    instanceId: 'instance-1',
    member: {
      id: 'member-3',
      firstNameAr: 'خالد',
      firstNameEn: 'Khalid',
      lastNameAr: 'العتيبي',
      lastNameEn: 'Al-Otaibi',
    },
    status: 'cancelled',
    waitlistPosition: null,
    bookedAt: '2026-04-07T06:00:00Z',
    cancelledAt: '2026-04-07T06:30:00Z',
  },
]

describe('GXBookingList', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders member names for all bookings', () => {
    renderWithProviders(<GXBookingList bookings={mockBookings} />)

    expect(screen.getByText('Ahmed Al-Rashidi')).toBeInTheDocument()
    expect(screen.getByText('Sarah Al-Mansouri')).toBeInTheDocument()
    expect(screen.getByText('Khalid Al-Otaibi')).toBeInTheDocument()
  })

  it('shows waitlist position for waitlisted bookings', () => {
    renderWithProviders(<GXBookingList bookings={mockBookings} />)
    expect(screen.getByText('#1')).toBeInTheDocument()
  })

  it('shows empty state when no bookings', () => {
    renderWithProviders(<GXBookingList bookings={[]} />)
    expect(screen.getByText('gx.bookings.empty')).toBeInTheDocument()
  })

  it('shows cancel button when showCancelButton is true', () => {
    const onCancel = vi.fn()
    renderWithProviders(
      <GXBookingList
        bookings={mockBookings}
        showCancelButton={true}
        onCancel={onCancel}
      />,
    )
    // Cancel buttons should show for non-cancelled bookings (2 of 3)
    const cancelButtons = screen.getAllByRole('button')
    expect(cancelButtons).toHaveLength(2)
  })

  it('does not show cancel button for already cancelled bookings', () => {
    const cancelledBookings: GXBooking[] = [
      { ...mockBookings[2] }, // the cancelled one
    ]
    renderWithProviders(
      <GXBookingList
        bookings={cancelledBookings}
        showCancelButton={true}
        onCancel={vi.fn()}
      />,
    )
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('calls onCancel with booking id when cancel button is clicked', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    renderWithProviders(
      <GXBookingList
        bookings={[mockBookings[0]]}
        showCancelButton={true}
        onCancel={onCancel}
      />,
    )

    const cancelButton = screen.getByRole('button')
    await user.click(cancelButton)
    expect(onCancel).toHaveBeenCalledWith('booking-1')
  })

  it('renders Arabic names when language is ar', () => {
    i18n.changeLanguage('ar')
    renderWithProviders(<GXBookingList bookings={mockBookings} />)
    expect(screen.getByText('أحمد الرشيدي')).toBeInTheDocument()
    expect(screen.getByText('سارة المنصوري')).toBeInTheDocument()
  })
})
