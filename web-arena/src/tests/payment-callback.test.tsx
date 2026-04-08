/**
 * @vitest-environment happy-dom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { useQuery, QueryClient, QueryClientProvider } from '@tanstack/react-query'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => () => ({ component: null }),
  useNavigate: () => vi.fn(),
}))

vi.mock('@/api/payments', () => ({
  getPaymentStatus: vi.fn(),
  paymentKeys: {
    all: ['online-payments'],
    status: (id: string) => ['online-payments', 'status', id],
  },
}))

import { getPaymentStatus } from '@/api/payments'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

function TestPaymentCallback({ moyasarId }: { moyasarId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ['online-payments', 'status', moyasarId],
    queryFn: () => getPaymentStatus(moyasarId),
    enabled: !!moyasarId,
  })

  if (isLoading) return <div>Loading...</div>
  if (!data) return <div>Something went wrong</div>
  if (data.status === 'PAID') return <div>Payment successful!</div>
  return <div>Payment failed</div>
}

describe('payment-callback', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders loading state while polling status', () => {
    vi.mocked(getPaymentStatus).mockReturnValue(new Promise(() => {}))

    render(<TestPaymentCallback moyasarId="pay_123" />, {
      wrapper: createWrapper(),
    })

    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  it('renders success message on PAID status', async () => {
    vi.mocked(getPaymentStatus).mockResolvedValue({
      moyasarId: 'pay_123',
      status: 'PAID',
      paymentMethod: 'mada',
      amountSar: '150.00',
      paidAt: '2026-04-08T09:00:00Z',
    })

    render(<TestPaymentCallback moyasarId="pay_123" />, {
      wrapper: createWrapper(),
    })

    expect(await screen.findByText('Payment successful!')).toBeInTheDocument()
  })

  it('renders failure message on failed status', async () => {
    vi.mocked(getPaymentStatus).mockResolvedValue({
      moyasarId: 'pay_123',
      status: 'FAILED',
      paymentMethod: null,
      amountSar: '150.00',
      paidAt: null,
    })

    render(<TestPaymentCallback moyasarId="pay_123" />, {
      wrapper: createWrapper(),
    })

    expect(await screen.findByText('Payment failed')).toBeInTheDocument()
  })
})
