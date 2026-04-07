import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getImportJob, cancelImportJob, rollbackImportJob } from '@/api/memberImport'
import type { MemberImportJobResponse } from '@/api/memberImport'

interface ImportJobStatusPanelProps {
  jobId: string | null
  clubPublicId: string
  onClear: () => void
}

const STATUS_COLORS: Record<string, string> = {
  QUEUED: 'bg-yellow-100 text-yellow-800',
  PROCESSING: 'bg-blue-100 text-blue-800',
  COMPLETED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-gray-100 text-gray-800',
  ROLLED_BACK: 'bg-red-100 text-red-800',
}

export function ImportJobStatusPanel({ jobId, clubPublicId, onClear }: ImportJobStatusPanelProps) {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [showRollbackConfirm, setShowRollbackConfirm] = useState(false)

  const isActive = (status: string) => status === 'QUEUED' || status === 'PROCESSING'

  const { data: job } = useQuery({
    queryKey: ['import-job', jobId],
    queryFn: () => getImportJob(jobId!),
    enabled: !!jobId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status && isActive(status) ? 3000 : false
    },
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelImportJob(jobId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['import-job', jobId] })
    },
  })

  const rollbackMutation = useMutation({
    mutationFn: () => rollbackImportJob(jobId!),
    onSuccess: () => {
      setShowRollbackConfirm(false)
      queryClient.invalidateQueries({ queryKey: ['import-job', jobId] })
    },
  })

  if (!jobId || !job) return null

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '-'
    return new Intl.DateTimeFormat(i18n.language, {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'Asia/Riyadh',
    }).format(new Date(dateStr))
  }

  return (
    <div className="mt-6 rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-center justify-between">
        <h4 className="text-sm font-semibold text-gray-900">{t('import.status.title', { defaultValue: 'Import Status' })}</h4>
        <div className="flex items-center gap-2">
          <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_COLORS[job.status] ?? 'bg-gray-100 text-gray-800'}`}>
            {t(`import.status.${job.status.toLowerCase()}`)}
          </span>
          {!isActive(job.status) && (
            <button
              type="button"
              onClick={onClear}
              className="text-xs text-gray-400 hover:text-gray-600"
            >
              &times;
            </button>
          )}
        </div>
      </div>

      <div className="mb-3 text-sm text-gray-600">
        <p>{job.fileName}</p>
        <p className="text-xs text-gray-400">{t('common.created_at')}: {formatDate(job.createdAt)}</p>
      </div>

      {job.status === 'COMPLETED' || job.status === 'ROLLED_BACK' ? (
        <div className="mb-3 grid grid-cols-3 gap-3 text-center">
          <div className="rounded bg-green-50 p-2">
            <p className="text-lg font-semibold text-green-700">{job.importedCount ?? 0}</p>
            <p className="text-xs text-green-600">{t('import.result.imported')}</p>
          </div>
          <div className="rounded bg-yellow-50 p-2">
            <p className="text-lg font-semibold text-yellow-700">{job.skippedCount ?? 0}</p>
            <p className="text-xs text-yellow-600">{t('import.result.skipped')}</p>
          </div>
          <div className="rounded bg-red-50 p-2">
            <p className="text-lg font-semibold text-red-700">{job.errorCount ?? 0}</p>
            <p className="text-xs text-red-600">{t('import.result.errors')}</p>
          </div>
        </div>
      ) : null}

      {job.errorDetail && (job.errorCount ?? 0) > 0 && (
        <div className="mb-3">
          <p className="mb-1 text-xs font-medium text-gray-500">{t('import.result.errors')}:</p>
          <pre className="max-h-40 overflow-auto rounded bg-gray-50 p-2 text-xs text-gray-700">
            {job.errorDetail}
          </pre>
        </div>
      )}

      <div className="flex gap-2">
        {job.status === 'QUEUED' && (
          <button
            type="button"
            data-testid="cancel-import-btn"
            onClick={() => cancelMutation.mutate()}
            disabled={cancelMutation.isPending}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            {t('import.cancel.button')}
          </button>
        )}

        {job.status === 'COMPLETED' && (
          <>
            {!showRollbackConfirm ? (
              <button
                type="button"
                data-testid="rollback-import-btn"
                onClick={() => setShowRollbackConfirm(true)}
                className="rounded-md border border-red-300 px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-50"
              >
                {t('import.rollback.button')}
              </button>
            ) : (
              <div className="flex flex-col gap-2 rounded-md border border-red-200 bg-red-50 p-3">
                <p className="text-sm text-red-700">
                  {t('import.rollback.confirm', { count: job.importedCount ?? 0 })}
                </p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    data-testid="confirm-rollback-btn"
                    onClick={() => rollbackMutation.mutate()}
                    disabled={rollbackMutation.isPending}
                    className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                  >
                    {rollbackMutation.isPending ? t('common.loading') : t('import.rollback.button')}
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowRollbackConfirm(false)}
                    className="rounded-md border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
                  >
                    {t('common.cancel')}
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
