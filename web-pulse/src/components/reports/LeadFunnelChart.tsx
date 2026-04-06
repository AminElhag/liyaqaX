import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts'

interface LeadFunnelChartProps {
  byStage: Record<string, number>
}

const STAGE_COLORS: Record<string, string> = {
  new: '#6b7280',
  contacted: '#3b82f6',
  interested: '#f59e0b',
  converted: '#10b981',
  lost: '#ef4444',
}

const STAGE_ORDER = ['new', 'contacted', 'interested', 'converted', 'lost']

export function LeadFunnelChart({ byStage }: LeadFunnelChartProps) {
  const data = STAGE_ORDER.map((stage) => ({
    stage,
    count: byStage[stage] ?? 0,
  }))

  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical">
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis type="number" tick={{ fontSize: 12 }} />
          <YAxis dataKey="stage" type="category" tick={{ fontSize: 12 }} width={80} />
          <Tooltip />
          <Bar dataKey="count">
            {data.map((entry) => (
              <Cell key={entry.stage} fill={STAGE_COLORS[entry.stage] ?? '#6b7280'} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
