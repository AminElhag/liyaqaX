import { useTranslation } from 'react-i18next'
import { MemberStatusBadge } from '@/components/members/MemberStatusBadge'
import { formatCurrency } from '@/lib/formatCurrency'
import { formatDate } from '@/lib/formatDate'
import type { Membership, MembershipStatus } from '@/types/domain'

interface MembershipCardProps {
  membership: Membership
}

export function MembershipCard({ membership }: MembershipCardProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const locale = i18n.language

  const planName = isAr ? membership.plan.nameAr : membership.plan.nameEn
  const daysRemaining = Math.max(
    0,
    Math.ceil(
      (new Date(membership.endDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24),
    ),
  )

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-6">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-semibold text-gray-900">
          {t('membership.activeTitle')}
        </h3>
        <MemberStatusBadge status={membership.status as MembershipStatus} />
      </div>

      <div className="space-y-3">
        <div className="flex justify-between text-sm">
          <span className="text-gray-500">{planName}</span>
          <span className="font-medium text-gray-900">
            {formatCurrency(membership.plan.priceHalalas, locale)}
          </span>
        </div>

        <div className="flex justify-between text-sm">
          <span className="text-gray-500">{t('membership.startDate')}</span>
          <span className="text-gray-900">{formatDate(membership.startDate, locale)}</span>
        </div>

        <div className="flex justify-between text-sm">
          <span className="text-gray-500">{t('membership.endDate')}</span>
          <span className="text-gray-900">{formatDate(membership.endDate, locale)}</span>
        </div>

        {membership.status === 'active' && (
          <div className="flex justify-between text-sm">
            <span className="text-gray-500">{t('membership.daysRemaining', { days: daysRemaining })}</span>
          </div>
        )}

        {membership.graceEndDate && (
          <div className="flex justify-between text-sm">
            <span className="text-gray-500">{t('membership.graceEndDate')}</span>
            <span className="text-gray-900">{formatDate(membership.graceEndDate, locale)}</span>
          </div>
        )}

        {membership.payment && (
          <div className="mt-4 border-t border-gray-100 pt-3">
            <p className="mb-1 text-xs font-medium uppercase text-gray-400">
              {t('membership.paymentInfo')}
            </p>
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">
                {t(`membership.paymentMethods.${membership.payment.paymentMethod}`)}
              </span>
              <span className="font-medium text-gray-900">
                {formatCurrency(membership.payment.amountHalalas, locale)}
              </span>
            </div>
          </div>
        )}

        {membership.invoice && (
          <div className="mt-2 border-t border-gray-100 pt-3">
            <p className="mb-1 text-xs font-medium uppercase text-gray-400">
              {t('membership.invoiceInfo')}
            </p>
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">{membership.invoice.invoiceNumber}</span>
              <span className="font-medium text-gray-900">
                {formatCurrency(membership.invoice.totalHalalas, locale)}
              </span>
            </div>
          </div>
        )}
      </div>

      <div className="mt-4">
        <button
          type="button"
          disabled
          title={t('membership.renewComingSoon')}
          className="w-full rounded-md bg-gray-100 px-4 py-2 text-sm font-medium text-gray-400 cursor-not-allowed"
        >
          {t('membership.renewComingSoon')}
        </button>
      </div>
    </div>
  )
}
