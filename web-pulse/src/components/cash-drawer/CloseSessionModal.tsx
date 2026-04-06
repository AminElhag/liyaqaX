import { useState } from 'react'
import { useTranslation } from 'react-i18next'

interface CloseSessionModalProps {
  isOpen: boolean
  onClose: () => void
  onConfirm: (countedClosingHalalas: number) => void
  isLoading?: boolean
}

export function CloseSessionModal({
  isOpen,
  onClose,
  onConfirm,
  isLoading,
}: CloseSessionModalProps) {
  const { t } = useTranslation()
  const [amountSar, setAmountSar] = useState('')

  if (!isOpen) return null

  const handleSubmit = () => {
    const sar = parseFloat(amountSar)
    if (isNaN(sar) || sar < 0) return
    onConfirm(Math.round(sar * 100))
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">
          {t('cash_drawer.close_session')}
        </h2>
        <p className="mb-4 text-sm text-gray-600">
          {t('cash_drawer.close_instruction')}
        </p>
        <div className="mb-4">
          <label
            htmlFor="countedAmount"
            className="mb-1 block text-sm font-medium text-gray-700"
          >
            {t('cash_drawer.counted_closing')} (SAR)
          </label>
          <input
            id="countedAmount"
            type="number"
            step="0.01"
            min="0"
            value={amountSar}
            onChange={(e) => setAmountSar(e.target.value)}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            autoFocus
          />
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
            disabled={!amountSar || isLoading}
            className="rounded-md bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700 disabled:opacity-50"
          >
            {t('cash_drawer.close_session')}
          </button>
        </div>
      </div>
    </div>
  )
}
