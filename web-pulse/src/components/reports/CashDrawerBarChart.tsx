import {
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Line,
  ComposedChart,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import type { TimePeriodCashDrawer } from '@/types/domain'

interface CashDrawerBarChartProps {
  periods: TimePeriodCashDrawer[]
}

export function CashDrawerBarChart({ periods }: CashDrawerBarChartProps) {
  const { t } = useTranslation()

  const data = periods.map((p) => ({
    label: p.label,
    cashIn: p.totalCashIn.halalas / 100,
    cashOut: p.totalCashOut.halalas / 100,
    netCash: p.netCash.halalas / 100,
  }))

  return (
    <div className="h-80 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 12 }} />
          <Tooltip />
          <Legend />
          <Bar dataKey="cashIn" name={t('reports.cashDrawer.cashIn')} fill="#10b981" />
          <Bar dataKey="cashOut" name={t('reports.cashDrawer.cashOut')} fill="#ef4444" />
          <Line type="monotone" dataKey="netCash" name={t('reports.cashDrawer.netCash')} stroke="#2563eb" strokeWidth={2} />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  )
}
