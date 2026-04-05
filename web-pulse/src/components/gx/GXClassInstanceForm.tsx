import { useTranslation } from 'react-i18next'
import type { GXClassType, CreateGXClassInstanceRequest } from '@/types/domain'

interface GXClassInstanceFormProps {
  classTypes: GXClassType[]
  onSubmit: (data: CreateGXClassInstanceRequest) => void
  isSubmitting?: boolean
}

export function GXClassInstanceForm({
  classTypes,
  onSubmit,
  isSubmitting = false,
}: GXClassInstanceFormProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const formData = new FormData(e.currentTarget)

    const data: CreateGXClassInstanceRequest = {
      classTypeId: formData.get('classTypeId') as string,
      instructorId: formData.get('instructorId') as string,
      scheduledAt: new Date(formData.get('scheduledAt') as string).toISOString(),
      durationMinutes: formData.get('durationMinutes')
        ? Number(formData.get('durationMinutes'))
        : undefined,
      capacity: formData.get('capacity')
        ? Number(formData.get('capacity'))
        : undefined,
      room: (formData.get('room') as string) || undefined,
      notes: (formData.get('notes') as string) || undefined,
    }

    onSubmit(data)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium">{t('gx.form.classType')}</label>
        <select name="classTypeId" required className="mt-1 w-full rounded border p-2">
          <option value="">{t('gx.form.selectClassType')}</option>
          {classTypes
            .filter((ct) => ct.isActive)
            .map((ct) => (
              <option key={ct.id} value={ct.id}>
                {isAr ? ct.nameAr : ct.nameEn}
              </option>
            ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium">{t('gx.form.instructorId')}</label>
        <input name="instructorId" type="text" required className="mt-1 w-full rounded border p-2" />
      </div>

      <div>
        <label className="block text-sm font-medium">{t('gx.form.scheduledAt')}</label>
        <input name="scheduledAt" type="datetime-local" required className="mt-1 w-full rounded border p-2" />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium">{t('gx.form.duration')}</label>
          <input name="durationMinutes" type="number" min={1} max={480} className="mt-1 w-full rounded border p-2" />
        </div>
        <div>
          <label className="block text-sm font-medium">{t('gx.form.capacity')}</label>
          <input name="capacity" type="number" min={1} max={500} className="mt-1 w-full rounded border p-2" />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium">{t('gx.form.room')}</label>
        <input name="room" type="text" maxLength={100} className="mt-1 w-full rounded border p-2" />
      </div>

      <div>
        <label className="block text-sm font-medium">{t('gx.form.notes')}</label>
        <textarea name="notes" rows={2} className="mt-1 w-full rounded border p-2" />
      </div>

      <button
        type="submit"
        disabled={isSubmitting}
        className="w-full rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
      >
        {isSubmitting ? t('common.submitting') : t('gx.form.scheduleClass')}
      </button>
    </form>
  )
}
