import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import type {
  CreateMembershipPlanRequest,
  UpdateMembershipPlanRequest,
  MembershipPlan,
} from '@/types/domain'

const planSchema = z
  .object({
    nameAr: z.string().min(1).max(255),
    nameEn: z.string().min(1).max(255),
    descriptionAr: z.string().optional(),
    descriptionEn: z.string().optional(),
    priceSarInput: z.coerce.number().positive(),
    durationDays: z.coerce.number().int().positive(),
    gracePeriodDays: z.coerce.number().int().min(0),
    freezeAllowed: z.boolean(),
    maxFreezeDays: z.coerce.number().int().min(0),
    gxClassesIncluded: z.boolean(),
    ptSessionsIncluded: z.boolean(),
    sortOrder: z.coerce.number().int().min(0),
  })
  .refine((data) => data.gracePeriodDays <= data.durationDays, {
    message: 'Grace period cannot exceed duration',
    path: ['gracePeriodDays'],
  })
  .refine(
    (data) => {
      if (!data.freezeAllowed) return data.maxFreezeDays === 0
      return data.maxFreezeDays > 0
    },
    {
      message:
        'Max freeze days must be 0 when freeze is not allowed, or > 0 when allowed',
      path: ['maxFreezeDays'],
    },
  )

type FormValues = z.infer<typeof planSchema>

interface PlanFormProps {
  plan?: MembershipPlan
  onSubmit: (
    data: CreateMembershipPlanRequest | UpdateMembershipPlanRequest,
  ) => void
  onCancel: () => void
  isSubmitting: boolean
}

export function PlanForm({
  plan,
  onSubmit,
  onCancel,
  isSubmitting,
}: PlanFormProps) {
  const { t } = useTranslation()
  const isEdit = !!plan

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(planSchema),
    defaultValues: {
      nameAr: plan?.nameAr ?? '',
      nameEn: plan?.nameEn ?? '',
      descriptionAr: plan?.descriptionAr ?? '',
      descriptionEn: plan?.descriptionEn ?? '',
      priceSarInput: plan ? plan.priceHalalas / 100 : undefined,
      durationDays: plan?.durationDays ?? undefined,
      gracePeriodDays: plan?.gracePeriodDays ?? 0,
      freezeAllowed: plan?.freezeAllowed ?? true,
      maxFreezeDays: plan?.maxFreezeDays ?? 30,
      gxClassesIncluded: plan?.gxClassesIncluded ?? true,
      ptSessionsIncluded: plan?.ptSessionsIncluded ?? false,
      sortOrder: plan?.sortOrder ?? 0,
    },
  })

  const freezeAllowed = watch('freezeAllowed')

  function handleFormSubmit(values: FormValues) {
    const priceHalalas = Math.round(values.priceSarInput * 100)

    if (isEdit) {
      const request: UpdateMembershipPlanRequest = {
        nameAr: values.nameAr,
        nameEn: values.nameEn,
        descriptionAr: values.descriptionAr || undefined,
        descriptionEn: values.descriptionEn || undefined,
        priceHalalas,
        durationDays: values.durationDays,
        gracePeriodDays: values.gracePeriodDays,
        freezeAllowed: values.freezeAllowed,
        maxFreezeDays: values.maxFreezeDays,
        gxClassesIncluded: values.gxClassesIncluded,
        ptSessionsIncluded: values.ptSessionsIncluded,
        sortOrder: values.sortOrder,
      }
      onSubmit(request)
    } else {
      const request: CreateMembershipPlanRequest = {
        nameAr: values.nameAr,
        nameEn: values.nameEn,
        descriptionAr: values.descriptionAr || undefined,
        descriptionEn: values.descriptionEn || undefined,
        priceHalalas,
        durationDays: values.durationDays,
        gracePeriodDays: values.gracePeriodDays,
        freezeAllowed: values.freezeAllowed,
        maxFreezeDays: values.maxFreezeDays,
        gxClassesIncluded: values.gxClassesIncluded,
        ptSessionsIncluded: values.ptSessionsIncluded,
        sortOrder: values.sortOrder,
      }
      onSubmit(request)
    }
  }

  const inputClass =
    'mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500'
  const labelClass = 'block text-sm font-medium text-gray-700'
  const errorClass = 'mt-1 text-xs text-red-600'

  return (
    <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-5">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="nameAr" className={labelClass}>
            {t('membershipPlans.form.nameAr')}
          </label>
          <input
            {...register('nameAr')}
            id="nameAr"
            dir="rtl"
            className={inputClass}
          />
          {errors.nameAr && (
            <p className={errorClass}>{errors.nameAr.message}</p>
          )}
        </div>
        <div>
          <label htmlFor="nameEn" className={labelClass}>
            {t('membershipPlans.form.nameEn')}
          </label>
          <input {...register('nameEn')} id="nameEn" className={inputClass} />
          {errors.nameEn && (
            <p className={errorClass}>{errors.nameEn.message}</p>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="descriptionAr" className={labelClass}>
            {t('membershipPlans.form.descriptionAr')}
          </label>
          <textarea
            {...register('descriptionAr')}
            id="descriptionAr"
            dir="rtl"
            rows={2}
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="descriptionEn" className={labelClass}>
            {t('membershipPlans.form.descriptionEn')}
          </label>
          <textarea
            {...register('descriptionEn')}
            id="descriptionEn"
            rows={2}
            className={inputClass}
          />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div>
          <label htmlFor="priceSarInput" className={labelClass}>
            {t('membershipPlans.form.price')}
          </label>
          <input
            {...register('priceSarInput')}
            id="priceSarInput"
            type="number"
            step="0.01"
            min="0.01"
            className={inputClass}
          />
          {errors.priceSarInput && (
            <p className={errorClass}>{errors.priceSarInput.message}</p>
          )}
        </div>
        <div>
          <label htmlFor="durationDays" className={labelClass}>
            {t('membershipPlans.form.duration')}
          </label>
          <input
            {...register('durationDays')}
            id="durationDays"
            type="number"
            min="1"
            className={inputClass}
          />
          {errors.durationDays && (
            <p className={errorClass}>{errors.durationDays.message}</p>
          )}
        </div>
        <div>
          <label htmlFor="gracePeriodDays" className={labelClass}>
            {t('membershipPlans.form.gracePeriod')}
          </label>
          <input
            {...register('gracePeriodDays')}
            id="gracePeriodDays"
            type="number"
            min="0"
            className={inputClass}
          />
          {errors.gracePeriodDays && (
            <p className={errorClass}>{errors.gracePeriodDays.message}</p>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex items-center gap-3">
          <input
            {...register('freezeAllowed')}
            type="checkbox"
            id="freezeAllowed"
            className="h-4 w-4 rounded border-gray-300"
          />
          <label htmlFor="freezeAllowed" className={labelClass}>
            {t('membershipPlans.form.freeze')}
          </label>
        </div>
        {freezeAllowed && (
          <div>
            <label htmlFor="maxFreezeDays" className={labelClass}>
              {t('membershipPlans.form.maxFreezeDays')}
            </label>
            <input
              {...register('maxFreezeDays')}
              id="maxFreezeDays"
              type="number"
              min="1"
              className={inputClass}
            />
            {errors.maxFreezeDays && (
              <p className={errorClass}>{errors.maxFreezeDays.message}</p>
            )}
          </div>
        )}
      </div>

      <div className="flex flex-wrap gap-6">
        <div className="flex items-center gap-3">
          <input
            {...register('gxClassesIncluded')}
            type="checkbox"
            id="gxClassesIncluded"
            className="h-4 w-4 rounded border-gray-300"
          />
          <label htmlFor="gxClassesIncluded" className={labelClass}>
            {t('membershipPlans.form.gxIncluded')}
          </label>
        </div>
        <div className="flex items-center gap-3">
          <input
            {...register('ptSessionsIncluded')}
            type="checkbox"
            id="ptSessionsIncluded"
            className="h-4 w-4 rounded border-gray-300"
          />
          <label htmlFor="ptSessionsIncluded" className={labelClass}>
            {t('membershipPlans.form.ptIncluded')}
          </label>
        </div>
      </div>

      <div className="flex justify-end gap-3 border-t pt-4">
        <button
          type="button"
          onClick={onCancel}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          disabled={isSubmitting}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {isSubmitting
            ? t('common.saving')
            : isEdit
              ? t('membershipPlans.editPlan')
              : t('membershipPlans.addPlan')}
        </button>
      </div>
    </form>
  )
}
