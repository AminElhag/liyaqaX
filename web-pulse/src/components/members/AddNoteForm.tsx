import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createNote, memberNoteKeys } from '@/api/memberNotes'

interface AddNoteFormProps {
  memberId: string
  onClose: () => void
  scope: 'pulse' | 'coach'
}

const PULSE_NOTE_TYPES = ['general', 'health', 'complaint', 'follow_up'] as const
const COACH_NOTE_TYPES = ['general', 'health'] as const

export function AddNoteForm({ memberId, onClose, scope }: AddNoteFormProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const noteTypes = scope === 'coach' ? COACH_NOTE_TYPES : PULSE_NOTE_TYPES

  const [noteType, setNoteType] = useState<string>('general')
  const [content, setContent] = useState('')
  const [followUpAt, setFollowUpAt] = useState('')
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      createNote(memberId, {
        noteType,
        content,
        followUpAt: noteType === 'follow_up' && followUpAt ? followUpAt : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: memberNoteKeys.timeline(memberId) })
      queryClient.invalidateQueries({ queryKey: memberNoteKeys.followUps() })
      onClose()
    },
    onError: (err: { detail?: string }) => {
      setError(err.detail ?? t('common.error'))
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!content.trim()) {
      setError(t('notes.content_required'))
      return
    }
    mutation.mutate()
  }

  return (
    <form onSubmit={handleSubmit} className="mb-4 rounded-md border border-gray-200 bg-gray-50 p-4">
      <div className="mb-3">
        <label className="mb-1 block text-sm font-medium text-gray-700">
          {t('notes.type_label')}
        </label>
        <div className="flex gap-2">
          {noteTypes.map((type) => (
            <button
              key={type}
              type="button"
              onClick={() => setNoteType(type)}
              className={`rounded-md px-3 py-1 text-sm font-medium ${
                noteType === type
                  ? 'bg-blue-600 text-white'
                  : 'bg-white text-gray-600 hover:bg-gray-100'
              }`}
            >
              {t(`notes.type.${type}`)}
            </button>
          ))}
        </div>
      </div>

      <div className="mb-3">
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          maxLength={1000}
          rows={3}
          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
          placeholder={t('notes.content_placeholder')}
        />
        <div className="mt-1 text-end text-xs text-gray-400">{content.length}/1000</div>
      </div>

      {noteType === 'follow_up' && (
        <div className="mb-3">
          <label className="mb-1 block text-sm font-medium text-gray-700">
            {t('notes.follow_up_date')}
          </label>
          <input
            type="date"
            value={followUpAt}
            onChange={(e) => setFollowUpAt(e.target.value)}
            min={new Date().toISOString().split('T')[0]}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-blue-500 focus:outline-none"
          />
        </div>
      )}

      {error && (
        <div className="mb-3 rounded-md bg-red-50 p-2 text-sm text-red-600">{error}</div>
      )}

      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onClose}
          className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          disabled={mutation.isPending}
          className="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {mutation.isPending ? t('common.saving') : t('common.save')}
        </button>
      </div>
    </form>
  )
}
