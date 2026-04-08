import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updatePortalSettings, portalSettingsKeys } from '@/api/portalSettings'
import { BrandingPreview } from './BrandingPreview'

interface BrandingSectionProps {
  initialLogoUrl: string
  initialPortalTitle: string
  initialPrimaryColor: string
  initialSecondaryColor: string
}

export function BrandingSection({
  initialLogoUrl,
  initialPortalTitle,
  initialPrimaryColor,
  initialSecondaryColor,
}: BrandingSectionProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [logoUrl, setLogoUrl] = useState(initialLogoUrl)
  const [portalTitle, setPortalTitle] = useState(initialPortalTitle)
  const [primaryColor, setPrimaryColor] = useState(initialPrimaryColor || '#6366F1')
  const [secondaryColor, setSecondaryColor] = useState(initialSecondaryColor || '#F1F5F9')
  const [toast, setToast] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      updatePortalSettings({
        logoUrl: logoUrl || undefined,
        portalTitle: portalTitle || undefined,
        primaryColorHex: primaryColor || undefined,
        secondaryColorHex: secondaryColor || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: portalSettingsKeys.all })
      setToast(t('branding.saved_toast'))
      setTimeout(() => setToast(null), 3000)
    },
  })

  return (
    <div className="space-y-6">
      <h3 className="text-lg font-semibold">{t('branding.title')}</h3>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium">{t('branding.logo_url')}</label>
            <input
              type="text"
              value={logoUrl}
              onChange={(e) => setLogoUrl(e.target.value)}
              placeholder={t('branding.logo_url_placeholder')}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium">{t('branding.portal_title')}</label>
            <input
              type="text"
              value={portalTitle}
              onChange={(e) => setPortalTitle(e.target.value)}
              placeholder={t('branding.portal_title_placeholder')}
              maxLength={100}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium">{t('branding.primary_color')}</label>
            <div className="flex items-center gap-2">
              <input
                type="color"
                value={primaryColor}
                onChange={(e) => setPrimaryColor(e.target.value)}
                className="h-10 w-10 cursor-pointer rounded border border-gray-300"
              />
              <input
                type="text"
                value={primaryColor}
                onChange={(e) => setPrimaryColor(e.target.value)}
                pattern="^#[0-9A-Fa-f]{6}$"
                className="w-28 rounded-md border border-gray-300 px-3 py-2 text-sm font-mono"
              />
            </div>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium">{t('branding.secondary_color')}</label>
            <div className="flex items-center gap-2">
              <input
                type="color"
                value={secondaryColor}
                onChange={(e) => setSecondaryColor(e.target.value)}
                className="h-10 w-10 cursor-pointer rounded border border-gray-300"
              />
              <input
                type="text"
                value={secondaryColor}
                onChange={(e) => setSecondaryColor(e.target.value)}
                pattern="^#[0-9A-Fa-f]{6}$"
                className="w-28 rounded-md border border-gray-300 px-3 py-2 text-sm font-mono"
              />
            </div>
          </div>
        </div>

        <BrandingPreview
          logoUrl={logoUrl}
          portalTitle={portalTitle}
          primaryColor={primaryColor}
          secondaryColor={secondaryColor}
        />
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {mutation.isPending ? t('common.saving') : t('branding.save_button')}
        </button>
        {toast && (
          <span className="text-sm text-green-600">{toast}</span>
        )}
      </div>
    </div>
  )
}
