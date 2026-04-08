interface EmptyStateProps {
  message: string
}

export function EmptyState({ message }: EmptyStateProps) {
  return (
    <div className="rounded-lg border border-dashed border-gray-300 px-6 py-12 text-center">
      <p className="text-sm text-gray-500">{message}</p>
    </div>
  )
}
