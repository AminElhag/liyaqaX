import { useState, useRef } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { uploadMemberCsv } from '@/api/memberImport'
import type { MemberImportAcceptedResponse } from '@/api/memberImport'

interface ImportMembersModalProps {
  clubPublicId: string
  isOpen: boolean
  onClose: () => void
  onJobCreated: (job: MemberImportAcceptedResponse) => void
}

export function ImportMembersModal({ clubPublicId, isOpen, onClose, onJobCreated }: ImportMembersModalProps) {
  const { t } = useTranslation()
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadMemberCsv(clubPublicId, file),
    onSuccess: (data) => {
      setSelectedFile(null)
      setError(null)
      onJobCreated(data)
      onClose()
    },
    onError: (err: { detail?: string; message?: string }) => {
      setError(err.detail ?? err.message ?? t('import.error.file_level'))
    },
  })

  if (!isOpen) return null

  const handleUpload = () => {
    if (!selectedFile) return
    uploadMutation.mutate(selectedFile)
  }

  const handleClose = () => {
    setSelectedFile(null)
    setError(null)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h3 className="mb-4 text-lg font-semibold text-gray-900">
          {t('import.modal.title')}
        </h3>

        <div className="mb-4">
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv"
            data-testid="csv-file-input"
            onChange={(e) => {
              setSelectedFile(e.target.files?.[0] ?? null)
              setError(null)
            }}
            className="block w-full text-sm text-gray-500 file:me-4 file:rounded-md file:border-0 file:bg-blue-50 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-blue-700 hover:file:bg-blue-100"
          />
        </div>

        <div className="mb-4">
          <a
            href="/sample-import.csv"
            download
            data-testid="sample-csv-link"
            className="text-sm text-blue-600 hover:text-blue-800 underline"
          >
            {t('import.modal.download_sample')}
          </a>
        </div>

        {error && (
          <div className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={handleClose}
            disabled={uploadMutation.isPending}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            onClick={handleUpload}
            disabled={!selectedFile || uploadMutation.isPending}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {uploadMutation.isPending ? t('common.loading') : t('import.modal.upload')}
          </button>
        </div>
      </div>
    </div>
  )
}
