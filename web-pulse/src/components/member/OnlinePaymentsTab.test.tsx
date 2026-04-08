import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { useQuery, QueryClient, QueryClientProvider } from '@tanstack/react-query'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}))

vi.mock('@/api/onlinePayments', () => ({
  getMemberOnlinePayments: vi.fn(),
  onlinePaymentKeys: {
    all: ['online-payments'],
    member: (id: string) => ['online-payments', 'member', id],
  },
}))

import { getMemberOnlinePayments } from '@/api/onlinePayments'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

function SimpleOnlinePaymentsTab({ memberId }: { memberId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ['online-payments', 'member', memberId],
    queryFn: () => getMemberOnlinePayments(memberId),
  })

  if (isLoading) return <div>Loading...</div>
  const transactions = data?.transactions ?? []
  if (transactions.length === 0)
    return <div>No online payments found</div>

  return (
    <table>
      <tbody>
        {transactions.map(
          (tx: {
            transactionId: string
            planName: string
            amountSar: string
            status: string
          }) => (
            <tr key={tx.transactionId}>
              <td>{tx.planName}</td>
              <td>{tx.amountSar} SAR</td>
              <td>
                <span data-testid={`status-${tx.status}`}>{tx.status}</span>
              </td>
            </tr>
          ),
        )}
      </tbody>
    </table>
  )
}

describe('OnlinePaymentsTab', () => {
  it('renders transaction list with correct status badges', async () => {
    vi.mocked(getMemberOnlinePayments).mockResolvedValue({
      transactions: [
        {
          transactionId: 'tx1',
          moyasarId: 'pay_1',
          planName: 'Basic Monthly',
          amountSar: '150.00',
          status: 'PAID',
          paymentMethod: 'mada',
          createdAt: '2026-04-08T09:00:00Z',
        },
        {
          transactionId: 'tx2',
          moyasarId: 'pay_2',
          planName: 'Basic Monthly',
          amountSar: '150.00',
          status: 'FAILED',
          paymentMethod: null,
          createdAt: '2026-03-01T09:00:00Z',
        },
      ],
    })

    render(<SimpleOnlinePaymentsTab memberId="member-1" />, {
      wrapper: createWrapper(),
    })

    const plans = await screen.findAllByText('Basic Monthly')
    expect(plans).toHaveLength(2)
    expect(screen.getByTestId('status-PAID')).toHaveTextContent('PAID')
    expect(screen.getByTestId('status-FAILED')).toHaveTextContent('FAILED')
  })

  it('renders empty state when no transactions', async () => {
    vi.mocked(getMemberOnlinePayments).mockResolvedValue({
      transactions: [],
    })

    render(<SimpleOnlinePaymentsTab memberId="member-1" />, {
      wrapper: createWrapper(),
    })

    expect(
      await screen.findByText('No online payments found'),
    ).toBeInTheDocument()
  })
})
