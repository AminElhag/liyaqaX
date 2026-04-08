import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState, useEffect } from 'react'
import { getPortalSettings, updatePortalSettings, portalSettingsKeys } from '@/api/portalSettings'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission } from '@/types/permissions'
import { BrandingSection } from '@/components/settings/BrandingSection'

export const Route = createFileRoute('/settings/portal')({
  component: PortalSettingsPage,
})

function PortalSettingsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const { data: settings, isLoading } = useQuery({
    queryKey: portalSettingsKeys.detail(),
    queryFn: getPortalSettings,
  })

  const [gxBookingEnabled, setGxBookingEnabled] = useState(true)
  const [ptViewEnabled, setPtViewEnabled] = useState(true)
  const [invoiceViewEnabled, setInvoiceViewEnabled] = useState(true)
  const [onlinePaymentEnabled, setOnlinePaymentEnabled] = useState(false)
  const [selfRegistrationEnabled, setSelfRegistrationEnabled] = useState(false)
  const [portalMessage, setPortalMessage] = useState('')
  const [featureToast, setFeatureToast] = useState<string | null>(null)

  useEffect(() => {
    if (settings) {
      setGxBookingEnabled(settings.gxBookingEnabled)
      setPtViewEnabled(settings.ptViewEnabled)
      setInvoiceViewEnabled(settings.invoiceViewEnabled)
      setOnlinePaymentEnabled(settings.onlinePaymentEnabled)
      setSelfRegistrationEnabled(settings.selfRegistrationEnabled)
      setPortalMessage(settings.portalMessage ?? '')
    }
  }, [settings])

  const featureMutation = useMutation({
    mutationFn: () =>
      updatePortalSettings({
        gxBookingEnabled,
        ptViewEnabled,
        invoiceViewEnabled,
        onlinePaymentEnabled,
        selfRegistrationEnabled,
        portalMessage: portalMessage || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: portalSettingsKeys.all })
      setFeatureToast('Settings saved')
      setTimeout(() => setFeatureToast(null), 3000)
    },
  })

  if (isLoading) {
    return <div className="p-6">{t('common.loading')}</div>
  }

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-6">
      <h2 className="text-xl font-bold">Portal Settings</h2>

      <div className="space-y-4 rounded-lg border border-gray-200 bg-white p-6">
        <h3 className="text-lg font-semibold">Feature Flags</h3>
        <div className="space-y-3">
          <Toggle label="GX Class Booking" checked={gxBookingEnabled} onChange={setGxBookingEnabled} />
          <Toggle label="PT Session View" checked={ptViewEnabled} onChange={setPtViewEnabled} />
          <Toggle label="Invoice View" checked={invoiceViewEnabled} onChange={setInvoiceViewEnabled} />
          <Toggle label="Online Payment" checked={onlinePaymentEnabled} onChange={setOnlinePaymentEnabled} />
          <Toggle label="Self Registration" checked={selfRegistrationEnabled} onChange={setSelfRegistrationEnabled} />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium">Portal Message</label>
          <textarea
            value={portalMessage}
            onChange={(e) => setPortalMessage(e.target.value)}
            maxLength={500}
            rows={3}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => featureMutation.mutate()}
            disabled={featureMutation.isPending}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {featureMutation.isPending ? t('common.saving') : 'Save Portal Settings'}
          </button>
          {featureToast && (
            <span className="text-sm text-green-600">{featureToast}</span>
          )}
        </div>
      </div>

      <PermissionGate permission={Permission.BRANDING_UPDATE}>
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <BrandingSection
            initialLogoUrl={settings?.logoUrl ?? ''}
            initialPortalTitle={settings?.portalTitle ?? ''}
            initialPrimaryColor={settings?.primaryColorHex ?? ''}
            initialSecondaryColor={settings?.secondaryColorHex ?? ''}
          />
        </div>
      </PermissionGate>
    </div>
  )
}

function Toggle({
  label,
  checked,
  onChange,
}: {
  label: string
  checked: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <label className="flex items-center justify-between">
      <span className="text-sm">{label}</span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
          checked ? 'bg-blue-600' : 'bg-gray-200'
        }`}
      >
        <span
          className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition-transform ${
            checked ? 'translate-x-5' : 'translate-x-0'
          }`}
        />
      </button>
    </label>
  )
}
