import { cn } from '@/lib/cn'
import { useState } from 'react'

interface KpiCardProps {
  label: string
  value: string | number
  tooltip?: string
  className?: string
}

export function KpiCard({ label, value, tooltip, className }: KpiCardProps) {
  const [isTooltipVisible, setIsTooltipVisible] = useState(false)

  return (
    <div
      className={cn(
        'relative rounded-lg border border-gray-200 bg-white p-5 shadow-sm',
        className,
      )}
    >
      <div className="flex items-start justify-between">
        <p className="text-sm font-medium text-gray-500">{label}</p>
        {tooltip && (
          <button
            type="button"
            className="text-gray-400 hover:text-gray-600"
            onMouseEnter={() => setIsTooltipVisible(true)}
            onMouseLeave={() => setIsTooltipVisible(false)}
            aria-label="Info"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 20 20"
              fill="currentColor"
              className="h-4 w-4"
            >
              <path
                fillRule="evenodd"
                d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A1.75 1.75 0 0010.747 15H11a.75.75 0 000-1.5h-.253a.25.25 0 01-.244-.304l.459-2.066A1.75 1.75 0 009.253 9H9z"
                clipRule="evenodd"
              />
            </svg>
          </button>
        )}
      </div>
      <p className="mt-2 text-2xl font-bold text-gray-900">{value}</p>
      {tooltip && isTooltipVisible && (
        <div className="absolute end-2 top-12 z-10 max-w-xs rounded-md bg-gray-800 px-3 py-2 text-xs text-white shadow-lg">
          {tooltip}
        </div>
      )}
    </div>
  )
}
