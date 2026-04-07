import { useTranslation } from 'react-i18next'

interface CompatibilityWarningProps {
  messages: string[]
}

export function CompatibilityWarning({ messages }: CompatibilityWarningProps) {
  const { t } = useTranslation()

  if (messages.length === 0) return null

  return (
    <div className="rounded-md border border-amber-200 bg-amber-50 p-3">
      <p className="text-sm font-medium text-amber-800">{t('reports.builder.incompatible')}</p>
      <ul className="mt-1 list-inside list-disc text-xs text-amber-700">
        {messages.map((msg, i) => (
          <li key={i}>{msg}</li>
        ))}
      </ul>
    </div>
  )
}
