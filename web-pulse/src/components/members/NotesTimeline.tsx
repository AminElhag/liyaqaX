import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  getTimeline,
  deleteNote,
  memberNoteKeys,
  type TimelineEvent,
} from '@/api/memberNotes'
import { AddNoteForm } from './AddNoteForm'
import { cn } from '@/lib/cn'

interface NotesTimelineProps {
  memberId: string
}

const NOTE_TYPE_COLORS: Record<string, string> = {
  NOTE_GENERAL: 'bg-gray-100 text-gray-600',
  NOTE_HEALTH: 'bg-red-100 text-red-600',
  NOTE_COMPLAINT: 'bg-amber-100 text-amber-600',
  NOTE_FOLLOW_UP: 'bg-blue-100 text-blue-600',
  NOTE_REJECTION: 'bg-red-200 text-red-700',
  MEMBERSHIP_JOINED: 'bg-green-100 text-green-600',
  MEMBERSHIP_RENEWED: 'bg-green-100 text-green-600',
  MEMBERSHIP_FROZEN: 'bg-blue-100 text-blue-600',
  MEMBERSHIP_TERMINATED: 'bg-red-100 text-red-600',
  PAYMENT_COLLECTED: 'bg-teal-100 text-teal-600',
}

function getEventIcon(eventType: string): string {
  if (eventType.startsWith('NOTE_')) return '📝'
  if (eventType.startsWith('MEMBERSHIP_')) return '🏷'
  if (eventType.startsWith('PAYMENT_')) return '💳'
  return '•'
}

function formatRelativeTime(dateStr: string, t: (key: string) => string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHr = Math.floor(diffMs / 3600000)
  const diffDay = Math.floor(diffMs / 86400000)

  if (diffMin < 1) return t('notes.just_now')
  if (diffMin < 60) return `${diffMin}m`
  if (diffHr < 24) return `${diffHr}h`
  if (diffDay < 7) return `${diffDay}d`
  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: 'numeric',
    timeZone: 'Asia/Riyadh',
  }).format(date)
}

export function NotesTimeline({ memberId }: NotesTimelineProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: memberNoteKeys.timeline(memberId),
    queryFn: () => getTimeline(memberId),
    staleTime: 30_000,
  })

  const deleteMutation = useMutation({
    mutationFn: (noteId: string) => deleteNote(memberId, noteId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: memberNoteKeys.timeline(memberId) })
    },
  })

  const handleDelete = (noteId: string) => {
    if (window.confirm(t('notes.delete_confirm'))) {
      deleteMutation.mutate(noteId)
    }
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
        <AddNoteForm
          memberId={memberId}
          onClose={() => setShowForm(false)}
          scope="pulse"
        />
      )}

      {isLoading && (
        <div className="py-8 text-center text-sm text-gray-500">
          {t('common.loading')}
        </div>
      )}

      {data && data.events.length === 0 && (
        <div className="py-8 text-center text-sm text-gray-500">
          {t('notes.empty')}
        </div>
      )}

      {data && data.events.length > 0 && (
        <div className="space-y-3">
          {data.events.map((event, idx) => (
            <TimelineItem
              key={`${event.eventType}-${idx}`}
              event={event}
              memberId={memberId}
              onDelete={handleDelete}
              t={t}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function TimelineItem({
  event,
  memberId,
  onDelete,
  t,
}: {
  event: TimelineEvent
  memberId: string
  onDelete: (noteId: string) => void
  t: (key: string) => string
}) {
  const isNote = event.eventType.startsWith('NOTE_')
  const isMembership = event.eventType.startsWith('MEMBERSHIP_')
  const isPayment = event.eventType.startsWith('PAYMENT_')

  const typeLabel = event.eventType.startsWith('NOTE_')
    ? event.noteType?.toLowerCase() ?? ''
    : event.eventType.toLowerCase().replace('_', ' ')

  return (
    <div className="flex gap-3 rounded-md border border-gray-100 bg-white p-3">
      <span className="mt-0.5 text-lg">{getEventIcon(event.eventType)}</span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span
            className={cn(
              'inline-flex rounded px-1.5 py-0.5 text-xs font-medium',
              NOTE_TYPE_COLORS[event.eventType] ?? 'bg-gray-100 text-gray-600',
            )}
          >
            {isNote && event.noteType
              ? t(`notes.type.${event.noteType.toLowerCase()}`)
              : t(`timeline.${event.eventType.toLowerCase()}`)}
          </span>
          <span className="text-xs text-gray-400">
            {formatRelativeTime(event.eventAt, t)}
          </span>
          {isNote && event.createdByName && (
            <span className="text-xs text-gray-400">
              — {event.createdByName}
            </span>
          )}
        </div>

        {isNote && event.content && (
          <p className="mt-1 text-sm text-gray-700">{event.content}</p>
        )}

        {isNote && event.followUpAt && (
          <p className="mt-1 text-xs text-blue-600">
            {t('notes.follow_up_date')}:{' '}
            {new Intl.DateTimeFormat('en', {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
              timeZone: 'Asia/Riyadh',
            }).format(new Date(event.followUpAt))}
          </p>
        )}

        {isMembership && event.detail && (
          <p className="mt-1 text-sm text-gray-700">{event.detail}</p>
        )}

        {isPayment && (
          <p className="mt-1 text-sm text-gray-700">
            {event.amountSar} SAR — {event.method}
          </p>
        )}
      </div>

      {isNote && event.canDelete && event.noteId && (
        <button
          type="button"
          onClick={() => onDelete(event.noteId!)}
          className="self-start text-sm text-red-400 hover:text-red-600"
          title={t('common.delete')}
        >
          ✕
        </button>
      )}
    </div>
  )
}
