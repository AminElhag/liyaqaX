import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { QRCodeSVG } from 'qrcode.react'
import { getMemberQr, memberQrKeys } from '@/api/memberQr'
import { useAuthStore } from '@/stores/useAuthStore'

export const Route = createFileRoute('/qr/')({
  component: QrPage,
})

function QrPage() {
  const { t, i18n } = useTranslation()
  const member = useAuthStore((s) => s.member)

  const { data, isLoading } = useQuery({
    queryKey: memberQrKeys.qr,
    queryFn: getMemberQr,
  })

  const locale = i18n.language
  const memberName = member
    ? locale === 'ar'
      ? `${member.firstNameAr ?? member.firstName} ${member.lastNameAr ?? member.lastName}`
      : `${member.firstName} ${member.lastName}`
    : ''

  return (
    <div className="flex flex-col items-center justify-center px-4 py-8">
      <h1 className="mb-6 text-xl font-bold text-gray-900">
        {t('qr.page_title')}
      </h1>

      {isLoading && (
        <div className="h-64 w-64 animate-pulse rounded-lg bg-gray-200" />
      )}

      {data && (
        <div className="flex flex-col items-center gap-4 rounded-xl bg-white p-8 shadow-md">
          <QRCodeSVG
            value={data.qrValue}
            size={256}
            level="M"
            includeMargin
          />
          <p className="text-lg font-semibold text-gray-900">{memberName}</p>
          <p className="text-center text-sm text-gray-500">
            {t('qr.instruction')}
          </p>
        </div>
      )}
    </div>
  )
}
