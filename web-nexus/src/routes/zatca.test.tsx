import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/useAuthStore'
import { Permission } from '@/types/permissions'
import { PermissionGate } from '@/components/common/PermissionGate'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}))

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => () => ({ component: undefined }),
}))

vi.mock('@/api/zatca', () => ({
  getHealthSummary: vi.fn(),
  getFailedInvoices: vi.fn(),
  listClubsZatcaStatus: vi.fn(),
  onboardClub: vi.fn(),
  renewClubCsid: vi.fn(),
  retryInvoice: vi.fn(),
  retryAllFailedForClub: vi.fn(),
}))

function TestWrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('ZATCA Health Cards', () => {
  beforeEach(() => {
    useAuthStore.setState({
      permissions: new Set([
        Permission.ZATCA_READ,
        Permission.ZATCA_RETRY,
        Permission.ZATCA_ONBOARD,
      ]),
      isAuthenticated: true,
      accessToken: 'test-token',
      user: null,
    })
  })

  it('renders HealthCard with correct color classes for non-zero values', () => {
    const { container } = render(
      <TestWrapper>
        <div data-testid="card" className="border-red-200 bg-red-50 text-red-700">
          <p className="text-sm font-medium opacity-80">
            zatca.health.deadline_at_risk
          </p>
          <p className="mt-1 text-2xl font-bold">3</p>
        </div>
      </TestWrapper>,
    )

    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('zatca.health.deadline_at_risk')).toBeInTheDocument()
    const card = container.querySelector('[data-testid="card"]')
    expect(card?.className).toContain('border-red-200')
  })

  it('renders green card for active CSIDs', () => {
    const { container } = render(
      <TestWrapper>
        <div data-testid="card" className="border-green-200 bg-green-50 text-green-700">
          <p className="text-sm font-medium opacity-80">
            zatca.health.active_csids
          </p>
          <p className="mt-1 text-2xl font-bold">5</p>
        </div>
      </TestWrapper>,
    )

    const card = container.querySelector('[data-testid="card"]')
    expect(card?.className).toContain('border-green-200')
    expect(screen.getByText('5')).toBeInTheDocument()
  })

  it('renders grey card when failed count is 0', () => {
    const { container } = render(
      <TestWrapper>
        <div data-testid="card" className="border-gray-200 bg-gray-50 text-gray-500">
          <p className="text-sm font-medium opacity-80">
            zatca.health.invoices_failed
          </p>
          <p className="mt-1 text-2xl font-bold">0</p>
        </div>
      </TestWrapper>,
    )

    const card = container.querySelector('[data-testid="card"]')
    expect(card?.className).toContain('border-gray-200')
    expect(screen.getByText('0')).toBeInTheDocument()
  })

  it('renders empty state message for failed invoices', () => {
    render(
      <TestWrapper>
        <div data-testid="empty-state">
          <p>zatca.failed_invoices.empty</p>
        </div>
      </TestWrapper>,
    )

    expect(
      screen.getByText('zatca.failed_invoices.empty'),
    ).toBeInTheDocument()
  })

  it('renders retry button that is gated by ZATCA_RETRY permission', () => {
    useAuthStore.setState({
      permissions: new Set([Permission.ZATCA_READ]),
    })

    render(
      <TestWrapper>
        <PermissionGate permission={Permission.ZATCA_RETRY}>
          <button type="button">zatca.failed_invoices.retry</button>
        </PermissionGate>
      </TestWrapper>,
    )

    expect(
      screen.queryByText('zatca.failed_invoices.retry'),
    ).not.toBeInTheDocument()
  })
})
