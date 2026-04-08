import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import type { TimePeriodRevenue } from '@/types/domain'

interface RevenueLineChartProps {
  periods: TimePeriodRevenue[]
}

export function RevenueLineChart({ periods }: RevenueLineChartProps) {
  const { t } = useTranslation()

  const data = periods.map((p) => ({
    label: p.label,
    total: p.totalRevenue.halalas / 100,
    membership: p.membershipRevenue.halalas / 100,
    pt: p.ptRevenue.halalas / 100,
    other: p.otherRevenue.halalas / 100,
  }))

  return (
    <div className="h-80 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 12 }} />
          <Tooltip />
          <Legend />
          <Line type="monotone" dataKey="total" name={t('reports.revenue.total')} stroke="#2563eb" strokeWidth={2} />
          <Line type="monotone" dataKey="membership" name={t('reports.revenue.membership')} stroke="#10b981" />
          <Line type="monotone" dataKey="pt" name={t('reports.revenue.pt')} stroke="#f59e0b" />
          <Line type="monotone" dataKey="other" name={t('reports.revenue.other')} stroke="#6b7280" />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
