import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import type { CreateMemberRequest } from '@/types/domain'

const emptyToUndefined = z.literal('').transform(() => undefined)

const step1Schema = z.object({
  firstNameAr: z.string().min(1).max(100),
  firstNameEn: z.string().min(1).max(100),
  lastNameAr: z.string().min(1).max(100),
  lastNameEn: z.string().min(1).max(100),
  email: z.string().email().max(255),
  phone: z.string().min(1).max(50),
  dateOfBirth: z.string().min(1).optional().or(emptyToUndefined),
  gender: z.enum(['male', 'female', 'unspecified']).optional().or(emptyToUndefined),
  nationalId: z.string().max(50).optional().or(emptyToUndefined),
  branchId: z.string().uuid(),
  notes: z.string().optional().or(emptyToUndefined),
})

const step2Schema = z.object({
  emergencyNameAr: z.string().min(1).max(255),
  emergencyNameEn: z.string().min(1).max(255),
  emergencyPhone: z.string().min(1).max(50),
  emergencyRelationship: z.string().max(100).optional().or(emptyToUndefined),
})

const fullSchema = step1Schema.merge(step2Schema)
type FormValues = z.infer<typeof fullSchema>

interface BranchOption {
  id: string
  nameAr: string
  nameEn: string
}

interface MemberRegistrationFormProps {
  branches: BranchOption[]
  onSubmit: (data: CreateMemberRequest, generatedPassword: string) => void
  isSubmitting: boolean
}

function generatePassword(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  const special = '!@#$%'
  let password = ''
  for (let i = 0; i < 11; i++) {
    password += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  password += special.charAt(Math.floor(Math.random() * special.length))
  return password
}

export function MemberRegistrationForm({
  branches,
  onSubmit,
  isSubmitting,
}: MemberRegistrationFormProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const [step, setStep] = useState(1)

  const {
    register,
    handleSubmit,
    trigger,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(fullSchema),
    defaultValues: {
      gender: undefined,
      dateOfBirth: undefined,
      nationalId: undefined,
      notes: undefined,
      emergencyRelationship: undefined,
    },
  })

  const goToStep2 = async () => {
    const valid = await trigger([
      'firstNameAr',
      'firstNameEn',
      'lastNameAr',
      'lastNameEn',
      'email',
      'phone',
      'branchId',
    ])
    if (valid) setStep(2)
  }

  const onFormSubmit = (values: FormValues) => {
    const password = generatePassword()
    const request: CreateMemberRequest = {
      email: values.email,
      password,
      firstNameAr: values.firstNameAr,
      firstNameEn: values.firstNameEn,
      lastNameAr: values.lastNameAr,
      lastNameEn: values.lastNameEn,
      phone: values.phone,
      nationalId: values.nationalId || undefined,
      dateOfBirth: values.dateOfBirth || undefined,
      gender: values.gender || undefined,
      branchId: values.branchId,
      notes: values.notes || undefined,
      emergencyContact: {
        nameAr: values.emergencyNameAr,
        nameEn: values.emergencyNameEn,
        phone: values.emergencyPhone,
        relationship: values.emergencyRelationship || undefined,
      },
    }
    onSubmit(request, password)
  }

  const inputCn =
    'w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500'
  const labelCn = 'block text-sm font-medium text-gray-700 mb-1'
  const errorCn = 'mt-1 text-xs text-red-600'

  return (
    <form onSubmit={handleSubmit(onFormSubmit)} className="space-y-6">
      {/* Step indicator */}
      <div className="flex gap-2">
        {[1, 2].map((s) => (
          <div
            key={s}
            className={`flex h-8 w-8 items-center justify-center rounded-full text-sm font-medium ${
              step >= s
                ? 'bg-blue-600 text-white'
                : 'bg-gray-200 text-gray-500'
            }`}
          >
            {s}
          </div>
        ))}
        <span className="flex items-center text-sm text-gray-600">
          {step === 1 ? t('members.form.step1') : t('members.form.step2')}
        </span>
      </div>

      {/* Step 1 — Personal info */}
      {step === 1 && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className={labelCn}>
                {t('members.form.firstName')} (العربية)
              </label>
              <input {...register('firstNameAr')} className={inputCn} dir="rtl" />
              {errors.firstNameAr && (
                <p className={errorCn}>{errors.firstNameAr.message}</p>
              )}
            </div>
            <div>
              <label className={labelCn}>
                {t('members.form.firstName')} (English)
              </label>
              <input {...register('firstNameEn')} className={inputCn} />
              {errors.firstNameEn && (
                <p className={errorCn}>{errors.firstNameEn.message}</p>
              )}
            </div>
            <div>
              <label className={labelCn}>
                {t('members.form.lastName')} (العربية)
              </label>
              <input {...register('lastNameAr')} className={inputCn} dir="rtl" />
              {errors.lastNameAr && (
                <p className={errorCn}>{errors.lastNameAr.message}</p>
              )}
            </div>
            <div>
              <label className={labelCn}>
                {t('members.form.lastName')} (English)
              </label>
              <input {...register('lastNameEn')} className={inputCn} />
              {errors.lastNameEn && (
                <p className={errorCn}>{errors.lastNameEn.message}</p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className={labelCn}>{t('members.form.email')}</label>
              <input
                {...register('email')}
                type="email"
                className={inputCn}
              />
              {errors.email && (
                <p className={errorCn}>{errors.email.message}</p>
              )}
            </div>
            <div>
              <label className={labelCn}>{t('members.form.phone')}</label>
              <input {...register('phone')} type="tel" className={inputCn} />
              {errors.phone && (
                <p className={errorCn}>{errors.phone.message}</p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div>
              <label className={labelCn}>
                {t('members.form.dateOfBirth')}
              </label>
              <input
                {...register('dateOfBirth')}
                type="date"
                className={inputCn}
              />
            </div>
            <div>
              <label className={labelCn}>{t('members.form.gender')}</label>
              <select {...register('gender')} className={inputCn}>
                <option value="">—</option>
                <option value="male">
                  {t('members.form.genderOptions.male')}
                </option>
                <option value="female">
                  {t('members.form.genderOptions.female')}
                </option>
                <option value="unspecified">
                  {t('members.form.genderOptions.unspecified')}
                </option>
              </select>
            </div>
            <div>
              <label className={labelCn}>
                {t('members.form.nationalId')}
              </label>
              <input {...register('nationalId')} className={inputCn} />
            </div>
          </div>

          <div>
            <label className={labelCn}>{t('members.form.branch')}</label>
            <select {...register('branchId')} className={inputCn}>
              <option value="">—</option>
              {branches.map((b) => (
                <option key={b.id} value={b.id}>
                  {isAr ? b.nameAr : b.nameEn}
                </option>
              ))}
            </select>
            {errors.branchId && (
              <p className={errorCn}>{errors.branchId.message}</p>
            )}
          </div>

          <div>
            <label className={labelCn}>{t('members.form.notes')}</label>
            <textarea {...register('notes')} rows={3} className={inputCn} />
          </div>

          <div className="flex justify-end">
            <button
              type="button"
              onClick={goToStep2}
              className="rounded-md bg-blue-600 px-6 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700"
            >
              {t('common.next')}
            </button>
          </div>
        </div>
      )}

      {/* Step 2 — Emergency contact */}
      {step === 2 && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className={labelCn}>
                {t('members.form.emergencyName')} (العربية)
              </label>
              <input
                {...register('emergencyNameAr')}
                className={inputCn}
                dir="rtl"
              />
              {errors.emergencyNameAr && (
                <p className={errorCn}>{errors.emergencyNameAr.message}</p>
              )}
            </div>
            <div>
              <label className={labelCn}>
                {t('members.form.emergencyName')} (English)
              </label>
              <input {...register('emergencyNameEn')} className={inputCn} />
              {errors.emergencyNameEn && (
                <p className={errorCn}>{errors.emergencyNameEn.message}</p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className={labelCn}>
                {t('members.form.emergencyPhone')}
              </label>
              <input
                {...register('emergencyPhone')}
                type="tel"
                className={inputCn}
              />
              {errors.emergencyPhone && (
                <p className={errorCn}>{errors.emergencyPhone.message}</p>
              )}
            </div>
            <div>
              <label className={labelCn}>
                {t('members.form.emergencyRelationship')}
              </label>
              <input
                {...register('emergencyRelationship')}
                className={inputCn}
              />
            </div>
          </div>

          <div className="flex justify-between">
            <button
              type="button"
              onClick={() => setStep(1)}
              className="rounded-md border border-gray-300 bg-white px-6 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50"
            >
              {t('common.previous')}
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-md bg-blue-600 px-6 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {t('members.registerMember')}
            </button>
          </div>
        </div>
      )}
    </form>
  )
}
