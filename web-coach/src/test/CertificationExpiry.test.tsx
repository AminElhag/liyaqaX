import { describe, it, expect } from 'vitest'

describe('Certification expiry check', () => {
  it('flags certification expiring within 30 days', () => {
    const today = new Date()
    const thirtyDaysFromNow = new Date(today.getTime() + 30 * 24 * 60 * 60 * 1000)
    const expiryDate = new Date(today.getTime() + 15 * 24 * 60 * 60 * 1000) // 15 days from now

    const isExpiringSoon = expiryDate <= thirtyDaysFromNow
    expect(isExpiringSoon).toBe(true)
  })

  it('does not flag certification expiring in more than 30 days', () => {
    const today = new Date()
    const thirtyDaysFromNow = new Date(today.getTime() + 30 * 24 * 60 * 60 * 1000)
    const expiryDate = new Date(today.getTime() + 60 * 24 * 60 * 60 * 1000) // 60 days from now

    const isExpiringSoon = expiryDate <= thirtyDaysFromNow
    expect(isExpiringSoon).toBe(false)
  })
})
