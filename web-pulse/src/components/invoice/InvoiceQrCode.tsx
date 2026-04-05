import { QRCodeSVG } from 'qrcode.react'
import { useTranslation } from 'react-i18next'

interface InvoiceQrCodeProps {
  qrCode: string | null
  size?: number
}

export function InvoiceQrCode({ qrCode, size = 200 }: InvoiceQrCodeProps) {
  const { t } = useTranslation()

  if (!qrCode) {
    return (
      <div className="flex items-center justify-center rounded-lg border border-dashed border-gray-300 bg-gray-50 p-6">
        <p className="text-sm text-gray-500">{t('invoice.noQr')}</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center gap-2">
      <QRCodeSVG value={qrCode} size={size} level="M" />
    </div>
  )
}
