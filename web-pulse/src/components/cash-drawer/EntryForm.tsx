import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import type { CashDrawerEntryType } from '@/types/domain'

const entrySchema = z.object({
  entryType: z.enum(['cash_in', 'cash_out', 'float_adjustment']),
  amountSar: z.coerce.number().positive('Amount must be greater than zero'),
  description: z.string().min(1, 'Description is required').max(255),
})

type EntryFormValues = z.infer<typeof entrySchema>

interface EntryFormProps {
  onSubmit: (data: {
    entryType: CashDrawerEntryType
    amountHalalas: number
    description: string
  }) => void
  isSubmitting?: boolean
}

export function EntryForm({ onSubmit, isSubmitting }: EntryFormProps) {
  const { t } = useTranslation()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<EntryFormValues>({
    resolver: zodResolver(entrySchema),
    defaultValues: {
      entryType: 'cash_in',
      amountSar: undefined,
      description: '',
    },
  })

  const handleFormSubmit = (values: EntryFormValues) => {
    const amountHalalas = Math.round(values.amountSar * 100)
    onSubmit({
      entryType: values.entryType,
      amountHalalas,
      description: values.description,
    })
    reset()
  }

  const labelClass = 'block text-sm font-medium text-gray-700'
  const inputClass =
    'mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500'

  return (
    <form
      onSubmit={handleSubmit(handleFormSubmit)}
      className="rounded-lg border border-gray-200 bg-white p-4"
    >
      <h3 className="mb-3 text-sm font-semibold text-gray-900">
        {t('cash_drawer.add_entry')}
      </h3>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
        <div>
          <label htmlFor="entryType" className={labelClass}>
            {t('cash_drawer.entry_type')}
          </label>
          <select {...register('entryType')} id="entryType" className={inputClass}>
            <option value="cash_in">{t('cash_drawer.entry.cash_in')}</option>
            <option value="cash_out">{t('cash_drawer.entry.cash_out')}</option>
            <option value="float_adjustment">
              {t('cash_drawer.entry.float_adjustment')}
            </option>
          </select>
        </div>
        <div>
          <label htmlFor="amountSar" className={labelClass}>
            {t('cash_drawer.amount_sar')}
          </label>
          <input
            {...register('amountSar')}
            id="amountSar"
            type="number"
            step="0.01"
            min="0.01"
            placeholder="0.00"
            className={inputClass}
          />
          {errors.amountSar && (
            <p className="mt-1 text-xs text-red-600">{errors.amountSar.message}</p>
          )}
        </div>
        <div>
          <label htmlFor="description" className={labelClass}>
            {t('cash_drawer.description')}
          </label>
          <input
            {...register('description')}
            id="description"
            type="text"
            placeholder={t('cash_drawer.description_placeholder')}
            className={inputClass}
          />
          {errors.description && (
            <p className="mt-1 text-xs text-red-600">{errors.description.message}</p>
          )}
        </div>
        <div className="flex items-end">
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {t('cash_drawer.add')}
          </button>
        </div>
      </div>
    </form>
  )
}
