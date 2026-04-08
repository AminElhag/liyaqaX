import { cn } from '@/lib/cn'

interface LoadingSkeletonProps {
  rows?: number
  className?: string
}

export function LoadingSkeleton({ rows = 5, className }: LoadingSkeletonProps) {
  return (
    <div className={cn('space-y-3', className)}>
      {Array.from({ length: rows }, (_, i) => (
        <div
          key={i}
          className="h-10 animate-pulse rounded-md bg-gray-200"
        />
      ))}
    </div>
  )
}
