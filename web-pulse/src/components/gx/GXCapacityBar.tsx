import { cn } from '@/lib/cn'

interface GXCapacityBarProps {
  bookingsCount: number
  capacity: number
}

export function GXCapacityBar({ bookingsCount, capacity }: GXCapacityBarProps) {
  const percentage = capacity > 0 ? Math.min((bookingsCount / capacity) * 100, 100) : 0
  const isFull = bookingsCount >= capacity
  const isNearFull = percentage >= 80

  return (
    <div className="flex items-center gap-2">
      <div className="h-2 w-20 overflow-hidden rounded-full bg-gray-200">
        <div
          className={cn(
            'h-full rounded-full transition-all',
            isFull
              ? 'bg-red-500'
              : isNearFull
                ? 'bg-amber-500'
                : 'bg-green-500',
          )}
          style={{ width: `${percentage}%` }}
        />
      </div>
      <span className="text-xs text-gray-600">
        {bookingsCount}/{capacity}
      </span>
    </div>
  )
}
