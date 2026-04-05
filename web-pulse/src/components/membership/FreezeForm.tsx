import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { freezeMembership, membershipKeys } from '@/api/memberships'
import type { ApiError } from '@/types/api'

const schema = z
  .object({
    freezeStartDate: z.string().min(1, 'Start date is required'),
    freezeEndDate: z.string().min(1, 'End date is required'),
    reason: z.string().optional(),
  })
  .refine((data) => data.freezeEndDate > data.freezeStartDate, {
    message: 'End date must be after start date',
    path: ['freezeEndDate'],
  })

type FormValues = z.infer<typeof schema>

interface FreezeFormProps {
  memberId: string
  membershipId: string
  maxFreezeDays: number
  freezeDaysUsed: number
  onSuccess: () => void
  onCancel: () => void
}

function todayIso(): string {
  return new Date().toISOString().split('T')[0]
}

function daysBetween(start: string, end: string): number {
  const startDate = new Date(start)
  const endDate = new Date(end)
  return Math.ceil((endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24))
}

export function FreezeForm({
  memberId,
  membershipId,
  maxFreezeDays,
  freezeDaysUsed,
  onSuccess,
  onCancel,
}: FreezeFormProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      freezeStartDate: todayIso(),
      freezeEndDate: '',
      reason: '',
    },
  })

  const freezeStartDate = watch('freezeStartDate')
  const freezeEndDate = watch('freezeEndDate')

  const requestedDays =
    freezeStartDate && freezeEndDate && freezeEndDate > freezeStartDate
      ? daysBetween(freezeStartDate, freezeEndDate)
      : 0

  const remainingDays = maxFreezeDays - freezeDaysUsed
  const exceedsLimit = requestedDays > remainingDays

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      freezeMembership(memberId, membershipId, {
        freezeStartDate: values.freezeStartDate,
        freezeEndDate: values.freezeEndDate,
        reason: values.reason || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: membershipKeys.active(memberId) })
      queryClient.invalidateQueries({ queryKey: membershipKeys.histories() })
      queryClient.invalidateQueries({ queryKey: ['members'] })
      onSuccess()
    },
  })

  const onSubmit = (values: FormValues) => mutation.mutate(values)

  const labelClass = 'block text-sm font-medium text-gray-700 mb-1'
  const inputClass =
    'block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500'
  const errorClass = 'mt-1 text-xs text-red-600'

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
      <h3 className="text-lg font-semibold text-gray-900">{t('membership.freezeTitle')}</h3>

      <div>
        <label className={labelClass}>{t('membership.freezeStartDate')}</label>
        <input
          type="date"
          min={todayIso()}
          {...register('freezeStartDate')}
          className={inputClass}
        />
        {errors.freezeStartDate && (
          <p className={errorClass}>{errors.freezeStartDate.message}</p>
        )}
      </div>

      <div>
        <label className={labelClass}>{t('membership.freezeEndDate')}</label>
        <input
          type="date"
          min={freezeStartDate || todayIso()}
          {...register('freezeEndDate')}
          className={inputClass}
        />
        {errors.freezeEndDate && (
          <p className={errorClass}>{errors.freezeEndDate.message}</p>
        )}
      </div>

      <div>
        <label className={labelClass}>{t('membership.freezeReason')}</label>
        <textarea {...register('reason')} rows={2} className={inputClass} />
      </div>

      {requestedDays > 0 && (
        <div className={`rounded-md p-3 text-sm ${exceedsLimit ? 'bg-red-50' : 'bg-gray-50'}`}>
          <p className={exceedsLimit ? 'text-red-700' : 'text-gray-700'}>
            {t('membership.freezeDaysCalc', {
              requested: requestedDays,
              remaining: remainingDays,
              max: maxFreezeDays,
            })}
          </p>
          {exceedsLimit && (
            <p className="mt-1 font-medium text-red-700">
              {t('membership.freezeExceedsLimit')}
            </p>
          )}
        </div>
      )}

      {mutation.error && (
        <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {(mutation.error as unknown as ApiError)?.detail ?? t('common.error')}
        </div>
      )}

      <div className="flex justify-end gap-3 pt-2">
        <button
          type="button"
          onClick={onCancel}
          className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          disabled={isSubmitting || exceedsLimit || requestedDays === 0}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isSubmitting ? t('common.saving') : t('membership.freezeTitle')}
        </button>
      </div>
    </form>
  )
}
