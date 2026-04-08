import { useState } from 'react'
import { useTranslation } from 'react-i18next'

interface LeadNoteFormProps {
  onSubmit: (body: string) => void
  isLoading?: boolean
}

export function LeadNoteForm({ onSubmit, isLoading }: LeadNoteFormProps) {
  const { t } = useTranslation()
  const [body, setBody] = useState('')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!body.trim()) return
    onSubmit(body.trim())
    setBody('')
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder={t('leads.notes.placeholder')}
        rows={3}
        className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
      />
      <div className="flex justify-end">
        <button
          type="submit"
          disabled={!body.trim() || isLoading}
          className="rounded-md bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {t('leads.notes.add')}
        </button>
      </div>
    </form>
  )
}
