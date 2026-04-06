export function formatHalalas(halalas: number, locale: string = 'en'): string {
  const sar = halalas / 100
  if (locale === 'ar') {
    return `${sar.toLocaleString('ar-SA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ر.س`
  }
  return `SAR ${sar.toLocaleString('en-SA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
