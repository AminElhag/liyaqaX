import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { getPtSessions, markPtAttendance } from '@/api/pt'
import { cn } from '@/lib/cn'

export const Route = createFileRoute('/pt')({
  component: PtSessionsPage,
})

function PtSessionsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<'upcoming' | 'past'>('upcoming')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const { data: sessions = [], isLoading } = useQuery({
    queryKey: ['pt-sessions', tab],
    queryFn: () => getPtSessions(tab),
  })

  const mutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'attended' | 'missed' }) =>
      markPtAttendance(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pt-sessions'] })
      queryClient.invalidateQueries({ queryKey: ['schedule'] })
      setSelectedId(null)
    },
  })

  const selectedSession = sessions.find((s) => s.id === selectedId)

  return (
    <div>
      <h1 className="mb-4 text-xl font-bold text-gray-900">{t('pt.title')}</h1>

      <div className="mb-4 flex gap-2">
        {(['upcoming', 'past'] as const).map((status) => (
          <button
            key={status}
            onClick={() => { setTab(status); setSelectedId(null) }}
            className={cn(
              'rounded-md px-4 py-1.5 text-sm font-medium',
              tab === status ? 'bg-teal-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200',
            )}
          >
            {t(`pt.${status}`)}
          </button>
        ))}
      </div>

      {isLoading && <p className="text-sm text-gray-500">{t('common.loading')}</p>}

      {!isLoading && sessions.length === 0 && (
        <p className="py-12 text-center text-sm text-gray-500">{t('pt.empty')}</p>
      )}

      <div className="space-y-3">
        {sessions.map((session) => (
          <button
            key={session.id}
            onClick={() => setSelectedId(session.id)}
            className={cn(
              'w-full rounded-lg border bg-white p-4 text-start',
              selectedId === session.id ? 'border-teal-500 ring-1 ring-teal-500' : 'border-gray-200',
            )}
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-900">{session.memberName}</p>
                <p className="text-xs text-gray-500">{session.packageName}</p>
              </div>
              <div className="text-end">
                <p className="text-sm text-gray-700">
                  {new Date(session.scheduledAt).toLocaleDateString()} {' '}
                  {new Date(session.scheduledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </p>
                <span className={cn(
                  'text-xs font-medium',
                  session.status === 'scheduled' && 'text-blue-600',
                  session.status === 'attended' && 'text-green-600',
                  session.status === 'missed' && 'text-red-600',
                )}>
                  {session.status}
                </span>
              </div>
            </div>
          </button>
        ))}
      </div>

      {selectedSession && selectedSession.status === 'scheduled' && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
          <div className="w-full max-w-sm rounded-lg bg-white p-6 shadow-lg">
            <h2 className="mb-2 text-lg font-semibold">{selectedSession.memberName}</h2>
            <p className="mb-4 text-sm text-gray-500">
              {new Date(selectedSession.scheduledAt).toLocaleString()}
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => mutation.mutate({ id: selectedSession.id, status: 'attended' })}
                disabled={mutation.isPending}
                className="flex-1 rounded-md bg-green-600 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
              >
                {t('pt.mark_attended')}
              </button>
              <button
                onClick={() => mutation.mutate({ id: selectedSession.id, status: 'missed' })}
                disabled={mutation.isPending}
                className="flex-1 rounded-md bg-red-600 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
              >
                {t('pt.mark_missed')}
              </button>
            </div>
            <button
              onClick={() => setSelectedId(null)}
              className="mt-3 w-full text-center text-sm text-gray-500 hover:text-gray-700"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
