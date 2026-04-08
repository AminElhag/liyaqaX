import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { renewMembership, membershipKeys } from '@/api/memberships'
import { getMembershipPlanList, membershipPlanKeys } from '@/api/membershipPlans'
import { formatCurrency } from '@/lib/formatCurrency'
import type { ApiError } from '@/types/api'
import type { PaymentMethod } from '@/types/domain'

const schema = z.object({
  planId: z.string().min(1, 'Plan is required'),
  paymentMethod: z.enum(['cash', 'card', 'bank-transfer', 'other']),
  referenceNumber: z.string().optional(),
  notes: z.string().optional(),
})

type FormValues = z.infer<typeof schema>

interface RenewalFormProps {
  memberId: string
  membershipId: string
  currentEndDate: string
  onSuccess: () => void
  onCancel: () => void
}

function computeNextDay(isoDate: string): string {
  const date = new Date(isoDate)
  date.setDate(date.getDate() + 1)
  return date.toISOString().split('T')[0]
}

export function RenewalForm({
  memberId,
  membershipId,
  currentEndDate,
  onSuccess,
  onCancel,
}: RenewalFormProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const locale = i18n.language
  const queryClient = useQueryClient()

  const defaultStartDate = computeNextDay(currentEndDate)

  const { data: plansData } = useQuery({
    queryKey: membershipPlanKeys.list({ size: 100 }),
    queryFn: () => getMembershipPlanList({ size: 100 }),
    staleTime: 5 * 60 * 1000,
  })

  const activePlans = plansData?.items.filter((p) => p.isActive) ?? []

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      planId: '',
      paymentMethod: 'cash',
    },
  })

  const selectedPlanId = watch('planId')
  const selectedPlan = activePlans.find((p) => p.id === selectedPlanId)

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      renewMembership(memberId, membershipId, {
        planId: values.planId,
        startDate: defaultStartDate,
        paymentMethod: values.paymentMethod as PaymentMethod,
        amountHalalas: selectedPlan?.priceHalalas ?? 0,
        referenceNumber: values.referenceNumber || undefined,
        notes: values.notes || undefined,
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
      <div>
        <label className={labelClass}>{t('membership.selectPlan')}</label>
        <select {...register('planId')} className={inputClass}>
          <option value="">{t('membership.selectPlan')}</option>
          {activePlans.map((plan) => (
            <option key={plan.id} value={plan.id}>
              {isAr ? plan.nameAr : plan.nameEn} — {formatCurrency(plan.priceHalalas, locale)}
            </option>
          ))}
        </select>
        {errors.planId && <p className={errorClass}>{errors.planId.message}</p>}
      </div>

      {selectedPlan && (
        <div className="rounded-md bg-gray-50 p-3">
          <div className="flex justify-between text-sm">
            <span className="text-gray-500">{t('membership.amount')}</span>
            <span className="font-semibold text-gray-900">
              {formatCurrency(selectedPlan.priceHalalas, locale)}
            </span>
          </div>
          <p className="mt-1 text-xs text-gray-400">
            {selectedPlan.durationDays} {t('membershipPlans.durationDays')}
          </p>
        </div>
      )}

      <div>
        <label className={labelClass}>{t('membership.startDate')}</label>
        <input
          type="date"
          value={defaultStartDate}
          readOnly
          className={`${inputClass} bg-gray-50 text-gray-500`}
        />
      </div>

      <div>
        <label className={labelClass}>{t('membership.paymentMethod')}</label>
        <select {...register('paymentMethod')} className={inputClass}>
          {(['cash', 'card', 'bank-transfer', 'other'] as const).map((method) => (
            <option key={method} value={method}>
              {t(`membership.paymentMethods.${method}`)}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className={labelClass}>{t('membership.referenceNumber')}</label>
        <input {...register('referenceNumber')} className={inputClass} />
      </div>

      <div>
        <label className={labelClass}>{t('membership.notes')}</label>
        <textarea {...register('notes')} rows={2} className={inputClass} />
      </div>

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
          disabled={isSubmitting || !selectedPlan}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isSubmitting ? t('common.saving') : t('membership.renewAndCollect')}
        </button>
      </div>
    </form>
  )
}
