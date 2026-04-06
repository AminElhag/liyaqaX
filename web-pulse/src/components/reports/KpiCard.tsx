import { cn } from '@/lib/cn'

interface KpiCardProps {
  label: string
  value: string | number
  subtitle?: string
  trend?: number | null
}

export function KpiCard({ label, value, subtitle, trend }: KpiCardProps) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <p className="text-sm font-medium text-gray-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-gray-900">{value}</p>
      {subtitle && <p className="mt-1 text-xs text-gray-400">{subtitle}</p>}
      {trend != null && (
        <p
          className={cn(
            'mt-1 text-sm font-medium',
            trend > 0 ? 'text-green-600' : trend < 0 ? 'text-red-600' : 'text-gray-500',
          )}
        >
          {trend > 0 ? '+' : ''}
          {trend.toFixed(1)}%
        </p>
      )}
    </div>
  )
}
