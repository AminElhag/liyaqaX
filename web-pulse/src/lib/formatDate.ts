import { format, parseISO } from 'date-fns'

const RIYADH_TZ = 'Asia/Riyadh'

/**
 * Format an ISO 8601 UTC string for display in Asia/Riyadh timezone.
 * Example: "2025-03-15T10:30:00Z" → "15 Mar 2025, 01:30 PM"
 */
export function formatDateTime(iso: string, locale: string = 'en'): string {
  const date = parseISO(iso)
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: RIYADH_TZ,
  }).format(date)
}

/**
 * Format an ISO 8601 date string for display (date only, no time).
 * Example: "2025-03-15" → "15 Mar 2025"
 */
export function formatDate(iso: string, locale: string = 'en'): string {
  const date = parseISO(iso)
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    timeZone: RIYADH_TZ,
  }).format(date)
}

/**
 * Format a date showing both Gregorian and Hijri calendars.
 * Example: "15 Mar 2025 — ١٥ رمضان ١٤٤٦"
 */
export function formatDateDual(iso: string, locale: string = 'en'): string {
  const date = parseISO(iso)
  const gregorian = formatDate(iso, locale)
  const hijri = new Intl.DateTimeFormat(`${locale}-u-ca-islamic-umalqura`, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: RIYADH_TZ,
  }).format(date)
  return `${gregorian} — ${hijri}`
}

/**
 * Format an ISO date as a relative short string.
 * Uses date-fns format for simple patterns.
 */
export function formatShortDate(iso: string): string {
  return format(parseISO(iso), 'dd/MM/yyyy')
}
