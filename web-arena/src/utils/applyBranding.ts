import type { PortalSettings } from '@/types/domain'

export function applyBranding(settings: PortalSettings) {
  const root = document.documentElement
  if (settings.primaryColorHex) {
    root.style.setProperty('--color-primary', settings.primaryColorHex)
  }
  if (settings.secondaryColorHex) {
    root.style.setProperty('--color-secondary', settings.secondaryColorHex)
  }
  if (settings.portalTitle) {
    document.title = settings.portalTitle
  }
}
