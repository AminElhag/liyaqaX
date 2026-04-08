import { useTranslation } from 'react-i18next'
import { useState } from 'react'

interface BrandingPreviewProps {
  logoUrl: string
  portalTitle: string
  primaryColor: string
  secondaryColor: string
}

export function BrandingPreview({
  logoUrl,
  portalTitle,
  primaryColor,
  secondaryColor,
}: BrandingPreviewProps) {
  const { t } = useTranslation()
  const [logoError, setLogoError] = useState(false)
  const displayTitle = portalTitle || 'Liyaqa'
  const displayPrimary = primaryColor || '#6366F1'
  const displaySecondary = secondaryColor || '#F1F5F9'

  return (
    <div className="overflow-hidden rounded-lg border border-gray-200">
      <div className="p-3 text-xs font-medium text-gray-500">{t('branding.preview_title')}</div>
      <div className="border-t" style={{ backgroundColor: displaySecondary }}>
        <div className="flex items-center justify-between border-b bg-white px-4 py-3">
          <div className="flex items-center gap-2">
            {logoUrl && !logoError ? (
              <img
                src={logoUrl}
                onError={() => setLogoError(true)}
                alt={displayTitle}
                className="h-6 w-auto"
              />
            ) : (
              <div
                className="flex h-6 w-6 items-center justify-center rounded text-xs font-bold text-white"
                style={{ backgroundColor: displayPrimary }}
              >
                {displayTitle.charAt(0)}
              </div>
            )}
            <span className="text-sm font-medium">{displayTitle}</span>
          </div>
          <span className="text-xs text-gray-400">Profile</span>
        </div>
        <div className="p-4" style={{ backgroundColor: displayPrimary }}>
          <div className="h-2 w-32 rounded bg-white/30" />
        </div>
        <div className="flex gap-2 p-4">
          <button
            type="button"
            className="rounded-md px-3 py-1.5 text-xs font-medium text-white"
            style={{ backgroundColor: displayPrimary }}
          >
            Book a Class
          </button>
          <button
            type="button"
            className="rounded-md px-3 py-1.5 text-xs font-medium text-white"
            style={{ backgroundColor: displayPrimary }}
          >
            My Membership
          </button>
        </div>
      </div>
    </div>
  )
}
