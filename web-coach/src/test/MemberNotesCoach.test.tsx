import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemberNotes } from '@/components/members/MemberNotes'
import '@/i18n'
import i18n from '@/i18n'

vi.mock('@/api/memberNotes', () => ({
  listCoachNotes: vi.fn(),
  createCoachNote: vi.fn(),
  coachNoteKeys: {
    all: ['coach-notes'],
    list: (id: string) => ['coach-notes', id],
  },
}))

function renderWithQuery(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  )
}

describe('MemberNotes (Coach)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    i18n.changeLanguage('en')
  })

  it('renders notes list for member', async () => {
    const { listCoachNotes } = await import('@/api/memberNotes')
    vi.mocked(listCoachNotes).mockResolvedValue([
      {
        noteId: 'note-1',
        noteType: 'GENERAL',
        content: 'Trainer note',
        followUpAt: null,
        createdByName: 'trainer',
        createdAt: '2026-04-07T09:00:00Z',
      },
    ])

    renderWithQuery(<MemberNotes memberId="member-1" />)

    expect(await screen.findByText('Trainer note')).toBeDefined()
  })

  it('add form shows only general and health type options', async () => {
    const { listCoachNotes } = await import('@/api/memberNotes')
    vi.mocked(listCoachNotes).mockResolvedValue([])

    renderWithQuery(<MemberNotes memberId="member-1" />)

    // Wait for data to load and click the add button
    const addBtn = await screen.findByText(i18n.t('notes.add_button'))
    addBtn.click()

    // Should show general and health buttons
    expect(await screen.findByText(i18n.t('notes.type.general'))).toBeDefined()
    expect(screen.getByText(i18n.t('notes.type.health'))).toBeDefined()

    // Should NOT show complaint or follow_up buttons
    expect(screen.queryByText(i18n.t('notes.type.complaint'))).toBeNull()
    expect(screen.queryByText(i18n.t('notes.type.follow_up'))).toBeNull()
  })
})
