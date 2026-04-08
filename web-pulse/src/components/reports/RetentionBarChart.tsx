import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import type { TimePeriodRetention } from '@/types/domain'

interface RetentionBarChartProps {
  periods: TimePeriodRetention[]
}

export function RetentionBarChart({ periods }: RetentionBarChartProps) {
  const { t } = useTranslation()

  const data = periods.map((p) => ({
    label: p.label,
    newMembers: p.newMembers,
    renewals: p.renewals,
    expired: p.expired,
  }))

  return (
    <div className="h-80 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 12 }} />
          <Tooltip />
          <Legend />
          <Bar dataKey="newMembers" name={t('reports.retention.newMembers')} fill="#10b981" />
          <Bar dataKey="renewals" name={t('reports.retention.renewals')} fill="#2563eb" />
          <Bar dataKey="expired" name={t('reports.retention.expired')} fill="#f59e0b" />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
