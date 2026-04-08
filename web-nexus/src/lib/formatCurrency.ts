/**
 * Format a monetary amount from halalas to SAR display string.
 * All monetary values in the system are integers in halalas (1 SAR = 100 halalas).
 *
 * @param halalas - Amount in halalas (integer)
 * @param locale - Display locale ('ar' or 'en')
 * @returns Formatted string, e.g. "SAR 150.00" or "150.00 ر.س"
 */
export function formatCurrency(halalas: number, locale: string = 'en'): string {
  const sar = halalas / 100
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'SAR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(sar)
}
