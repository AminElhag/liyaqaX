import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  createCoachNote,
  listCoachNotes,
  coachNoteKeys,
} from '@/api/memberNotes'

interface MemberNotesProps {
  memberId: string
}

const NOTE_TYPES = ['general', 'health'] as const

export function MemberNotes({ memberId }: MemberNotesProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [noteType, setNoteType] = useState<string>('general')
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data: notes, isLoading } = useQuery({
    queryKey: coachNoteKeys.list(memberId),
    queryFn: () => listCoachNotes(memberId),
    staleTime: 30_000,
  })

  const mutation = useMutation({
    mutationFn: () => createCoachNote(memberId, { noteType, content }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: coachNoteKeys.list(memberId) })
      setContent('')
      setShowForm(false)
    },
    onError: (err: { detail?: string }) => {
      setError(err.detail ?? t('common.error'))
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!content.trim()) return
    mutation.mutate()
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-medium text-gray-900">
          {t('notes.tab_title')}
        </h3>
        <button
          type="button"
          onClick={() => setShowForm(!showForm)}
          className="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
        >
          {t('notes.add_button')}
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleSubmit} className="mb-4 rounded-md border border-gray-200 bg-gray-50 p-4">
          <div className="mb-3">
            <div className="flex gap-2">
              {NOTE_TYPES.map((type) => (
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
            />
            <div className="mt-1 text-end text-xs text-gray-400">{content.length}/1000</div>
          </div>

          {error && (
            <div className="mb-3 rounded-md bg-red-50 p-2 text-sm text-red-600">{error}</div>
          )}

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
            >
              {t('common.cancel')}
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {t('common.save')}
            </button>
          </div>
        </form>
      )}

      {isLoading && (
        <div className="py-8 text-center text-sm text-gray-500">{t('common.loading')}</div>
      )}

      {notes && notes.length === 0 && (
        <div className="py-8 text-center text-sm text-gray-500">{t('notes.empty')}</div>
      )}

      {notes && notes.length > 0 && (
        <div className="space-y-3">
          {notes.map((note) => (
            <div key={note.noteId} className="rounded-md border border-gray-100 bg-white p-3">
              <div className="flex items-center gap-2">
                <span className="inline-flex rounded bg-gray-100 px-1.5 py-0.5 text-xs font-medium text-gray-600">
                  {t(`notes.type.${note.noteType.toLowerCase()}`)}
                </span>
                <span className="text-xs text-gray-400">
                  {new Intl.DateTimeFormat('en', {
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                    timeZone: 'Asia/Riyadh',
                  }).format(new Date(note.createdAt))}
                </span>
                <span className="text-xs text-gray-400">— {note.createdByName}</span>
              </div>
              <p className="mt-1 text-sm text-gray-700">{note.content}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
