import { cn } from '@/lib/cn'

const statusStyles: Record<string, string> = {
  active: 'bg-green-100 text-green-800',
  frozen: 'bg-blue-100 text-blue-800',
  expired: 'bg-amber-100 text-amber-800',
  terminated: 'bg-red-100 text-red-800',
  prospect: 'bg-gray-100 text-gray-800',
  lead: 'bg-gray-100 text-gray-600',
}

export function StatusBadge({ status }: { status: string }) {
  const style = statusStyles[status.toLowerCase()] ?? 'bg-gray-100 text-gray-700'

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium capitalize',
        style,
      )}
    >
      {status}
    </span>
  )
}
