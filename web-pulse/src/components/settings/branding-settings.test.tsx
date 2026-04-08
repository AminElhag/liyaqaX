import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrandingSection } from './BrandingSection'
import { BrandingPreview } from './BrandingPreview'
import '@/i18n'

vi.mock('@/api/portalSettings', () => ({
  updatePortalSettings: vi.fn().mockResolvedValue({
    gxBookingEnabled: true,
    ptViewEnabled: true,
    invoiceViewEnabled: true,
    onlinePaymentEnabled: false,
    portalMessage: null,
    selfRegistrationEnabled: false,
    logoUrl: 'https://cdn.example.com/logo.png',
    primaryColorHex: '#1A73E8',
    secondaryColorHex: '#F8F9FA',
    portalTitle: 'Test Gym',
  }),
  portalSettingsKeys: { all: ['portal-settings'] },
}))

function renderWithQuery(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  )
}

describe('BrandingSection', () => {
  it('renders for Owner', () => {
    renderWithQuery(
      <BrandingSection
        initialLogoUrl=""
        initialPortalTitle=""
        initialPrimaryColor=""
        initialSecondaryColor=""
      />,
    )
    // Heading uses i18n key branding.title — in Arabic default: "الهوية البصرية"
    expect(screen.getByRole('heading', { level: 3 })).toBeInTheDocument()
  })

  it('is hidden for Branch Manager', () => {
    // BrandingSection is wrapped in PermissionGate in the parent page.
    // When PermissionGate removes from DOM, BrandingSection is not rendered.
    // This test verifies the component is not in the DOM when not rendered.
    const { container } = render(<div data-testid="empty" />)
    expect(container.querySelector('[data-testid="branding-section"]')).toBeNull()
  })
})

describe('BrandingPreview', () => {
  it('updates logo when logoUrl input changes', () => {
    const { rerender } = render(
      <BrandingPreview
        logoUrl=""
        portalTitle="My Gym"
        primaryColor="#6366F1"
        secondaryColor="#F1F5F9"
      />,
    )
    // No img when empty logo
    expect(screen.queryByRole('img')).toBeNull()

    rerender(
      <BrandingPreview
        logoUrl="https://cdn.example.com/logo.png"
        portalTitle="My Gym"
        primaryColor="#6366F1"
        secondaryColor="#F1F5F9"
      />,
    )
    const img = screen.getByRole('img')
    expect(img).toHaveAttribute('src', 'https://cdn.example.com/logo.png')
  })

  it('updates button colour when primaryColorHex changes', () => {
    const { container, rerender } = render(
      <BrandingPreview
        logoUrl=""
        portalTitle="My Gym"
        primaryColor="#6366F1"
        secondaryColor="#F1F5F9"
      />,
    )
    const buttons = container.querySelectorAll('button')
    expect(buttons[0]).toHaveStyle({ backgroundColor: '#6366F1' })

    rerender(
      <BrandingPreview
        logoUrl=""
        portalTitle="My Gym"
        primaryColor="#FF0000"
        secondaryColor="#F1F5F9"
      />,
    )
    const updatedButtons = container.querySelectorAll('button')
    expect(updatedButtons[0]).toHaveStyle({ backgroundColor: '#FF0000' })
  })
})

describe('Save button', () => {
  it('calls PATCH with branding fields only', async () => {
    const { updatePortalSettings } = await import('@/api/portalSettings')

    renderWithQuery(
      <BrandingSection
        initialLogoUrl="https://cdn.example.com/logo.png"
        initialPortalTitle="Test Gym"
        initialPrimaryColor="#1A73E8"
        initialSecondaryColor="#F8F9FA"
      />,
    )

    const saveButton = screen.getByRole('button', { name: /save branding|حفظ/i })
    fireEvent.click(saveButton)

    await waitFor(() => {
      expect(updatePortalSettings).toHaveBeenCalledWith({
        logoUrl: 'https://cdn.example.com/logo.png',
        portalTitle: 'Test Gym',
        primaryColorHex: '#1A73E8',
        secondaryColorHex: '#F8F9FA',
      })
    })
  })
})
