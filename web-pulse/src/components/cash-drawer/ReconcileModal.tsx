import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ReconciliationStatus } from '@/types/domain'

interface ReconcileModalProps {
  isOpen: boolean
  onClose: () => void
  onConfirm: (status: ReconciliationStatus, notes?: string) => void
  isLoading?: boolean
}

export function ReconcileModal({
  isOpen,
  onClose,
  onConfirm,
  isLoading,
}: ReconcileModalProps) {
  const { t } = useTranslation()
  const [status, setStatus] = useState<ReconciliationStatus>('approved')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState('')

  if (!isOpen) return null

  const handleSubmit = () => {
    if (status === 'flagged' && !notes.trim()) {
      setError(t('cash_drawer.reconcile.notes_required'))
      return
    }
    setError('')
    onConfirm(status, notes.trim() || undefined)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">
          {t('cash_drawer.reconcile.title')}
        </h2>
        <div className="mb-4">
          <label className="mb-1 block text-sm font-medium text-gray-700">
            {t('cash_drawer.reconcile.status')}
          </label>
          <div className="flex gap-3">
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="reconciliationStatus"
                value="approved"
                checked={status === 'approved'}
                onChange={() => {
                  setStatus('approved')
                  setError('')
                }}
                className="text-blue-600"
              />
              {t('cash_drawer.reconcile.approve')}
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="reconciliationStatus"
                value="flagged"
                checked={status === 'flagged'}
                onChange={() => setStatus('flagged')}
                className="text-red-600"
              />
              {t('cash_drawer.reconcile.flag')}
            </label>
          </div>
        </div>
        <div className="mb-4">
          <label
            htmlFor="reconcileNotes"
            className="mb-1 block text-sm font-medium text-gray-700"
          >
            {t('cash_drawer.reconcile.notes')}
            {status === 'flagged' && (
              <span className="text-red-500"> *</span>
            )}
          </label>
          <textarea
            id="reconcileNotes"
            value={notes}
            onChange={(e) => {
              setNotes(e.target.value)
              if (error) setError('')
            }}
            rows={3}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
          {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
        </div>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={isLoading}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {t('cash_drawer.reconcile.confirm')}
          </button>
        </div>
      </div>
    </div>
  )
}
